/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kairo.core.agent;

import io.kairo.api.agent.AgentConfig;
import io.kairo.api.execution.ExecutionEventType;
import io.kairo.api.execution.ResourceConstraint;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.execution.DefaultResourceConstraint;
import io.kairo.core.execution.ExecutionEventEmitter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * The core ReAct (Reasoning + Acting) iteration loop, extracted from {@link DefaultReActAgent}.
 *
 * <p>This class <b>owns</b> the conversation history — only it may {@code add()} to the list.
 * External collaborators use {@link #injectMessages} or {@link #replaceHistory} for controlled
 * mutations.
 *
 * <p>Orchestrates the loop phases: {@link IterationGuards} → {@link ReasoningPhase} → {@link
 * ToolPhase}, with hook decisions applied by {@link HookDecisionApplier}.
 *
 * <p>Package-private: not part of the public API.
 */
class ReActLoop {

    private static final Logger log = LoggerFactory.getLogger(ReActLoop.class);

    private final List<Msg> conversationHistory;
    private final AtomicInteger currentIteration;
    private final Supplier<ModelConfig> modelConfigSupplier;
    private volatile boolean streamingEnabled = false;
    private final AtomicBoolean danglingRecoveryDone = new AtomicBoolean(false);

    @Nullable private final ExecutionEventEmitter eventEmitter;

    // Decomposed phase collaborators
    private final IterationGuards guards;
    private final HookDecisionApplier hookDecisions;
    private final ReasoningPhase reasoningPhase;
    private final ToolPhase toolPhase;

    // Retained for dangling recovery (needs ctx fields)
    private final ReActLoopContext ctx;

    /**
     * Create a new ReActLoop.
     *
     * @param ctx the immutable context holding all dependencies
     * @param interrupted shared interrupted flag (set by {@link DefaultReActAgent#interrupt()})
     * @param currentIteration shared iteration counter
     * @param totalTokensUsed shared token counter
     * @param modelConfigSupplier supplier for building ModelConfig each iteration
     */
    ReActLoop(
            ReActLoopContext ctx,
            AtomicBoolean interrupted,
            AtomicInteger currentIteration,
            AtomicLong totalTokensUsed,
            Supplier<ModelConfig> modelConfigSupplier) {
        this(ctx, interrupted, currentIteration, totalTokensUsed, modelConfigSupplier, null);
    }

    /**
     * Create a new ReActLoop with an optional {@link ExecutionEventEmitter}.
     *
     * @param ctx the immutable context holding all dependencies
     * @param interrupted shared interrupted flag (set by {@link DefaultReActAgent#interrupt()})
     * @param currentIteration shared iteration counter
     * @param totalTokensUsed shared token counter
     * @param modelConfigSupplier supplier for building ModelConfig each iteration
     * @param eventEmitter optional emitter for durable execution events (may be null)
     */
    ReActLoop(
            ReActLoopContext ctx,
            AtomicBoolean interrupted,
            AtomicInteger currentIteration,
            AtomicLong totalTokensUsed,
            Supplier<ModelConfig> modelConfigSupplier,
            @Nullable ExecutionEventEmitter eventEmitter) {
        this.ctx = ctx;
        this.conversationHistory = new CopyOnWriteArrayList<>();
        this.currentIteration = currentIteration;
        this.modelConfigSupplier = modelConfigSupplier;
        this.eventEmitter = eventEmitter;

        // Initialize loop detector from config thresholds
        var loopDetector =
                new LoopDetector(
                        ctx.config().loopHashWarnThreshold(),
                        ctx.config().loopHashHardLimit(),
                        ctx.config().loopFreqWarnThreshold(),
                        ctx.config().loopFreqHardLimit(),
                        ctx.config().loopFreqWindow(),
                        3);

        // Build phase collaborators
        List<ResourceConstraint> effectiveConstraints = resolveResourceConstraints(ctx.config());
        this.guards = new IterationGuards(ctx, interrupted, currentIteration, effectiveConstraints);
        this.hookDecisions = new HookDecisionApplier(ctx);
        this.toolPhase =
                new ToolPhase(
                        ctx,
                        guards,
                        hookDecisions,
                        conversationHistory,
                        loopDetector,
                        currentIteration,
                        eventEmitter);
        this.reasoningPhase =
                new ReasoningPhase(
                        ctx,
                        guards,
                        hookDecisions,
                        conversationHistory,
                        totalTokensUsed,
                        currentIteration,
                        () -> streamingEnabled,
                        toolPhase,
                        eventEmitter);
    }

    // ---- History management (controlled mutation) ----

    /** Inject messages into the conversation history (e.g. user input, session memory). */
    void injectMessages(List<Msg> messages) {
        if (messages != null) {
            conversationHistory.addAll(messages);
        }
    }

    /** Replace the entire conversation history (e.g. after compaction). */
    void replaceHistory(List<Msg> newHistory) {
        conversationHistory.clear();
        if (newHistory != null) {
            conversationHistory.addAll(newHistory);
        }
    }

    /** Return an unmodifiable view of the conversation history. */
    List<Msg> getHistory() {
        return Collections.unmodifiableList(conversationHistory);
    }

    void setCompactionTrigger(CompactionTrigger compactionTrigger) {
        this.toolPhase.setCompactionTrigger(compactionTrigger);
    }

    void setStreamingEnabled(boolean enabled) {
        this.streamingEnabled = enabled;
    }

    boolean isStreamingEnabled() {
        return streamingEnabled;
    }

    // ---- Core loop ----

    /** The core ReAct loop. Uses {@code Mono.defer()} for stack-safe recursion. */
    Mono<Msg> runLoop() {
        // Recover dangling tool calls once per runLoop() invocation
        if (danglingRecoveryDone.compareAndSet(false, true)) {
            recoverDanglingToolCalls();
        }

        return Mono.defer(this::runSingleIteration);
    }

    private Mono<Msg> runSingleIteration() {
        return guards.evaluate()
                .switchIfEmpty(
                        Mono.defer(
                                () -> {
                                    Mono<Msg> execution =
                                            reasoningPhase.execute(
                                                    modelConfigSupplier.get(), this::runLoop);

                                    // Emit ITERATION_COMPLETE after the iteration finishes
                                    // (best-effort)
                                    if (eventEmitter != null) {
                                        execution =
                                                execution.flatMap(
                                                        msg ->
                                                                emitBestEffort(
                                                                                ExecutionEventType
                                                                                        .ITERATION_COMPLETE,
                                                                                "{\"iteration\":"
                                                                                        + currentIteration
                                                                                                .get()
                                                                                        + "}")
                                                                        .thenReturn(msg));
                                    }
                                    return execution;
                                }));
    }

    /**
     * Best-effort event emission — errors are logged and swallowed so that event emission failures
     * never break the ReAct loop.
     */
    private Mono<Void> emitBestEffort(ExecutionEventType type, String payloadJson) {
        if (eventEmitter == null) {
            return Mono.empty();
        }
        return eventEmitter
                .emit(type, payloadJson)
                .onErrorResume(
                        e -> {
                            log.warn(
                                    "Failed to emit {} event for execution: {}",
                                    type,
                                    e.getMessage());
                            return Mono.empty();
                        });
    }

    // ---- Dangling Tool Call Recovery ----

    /**
     * Scan the conversation history and inject error ToolResults for any ASSISTANT tool_use blocks
     * that lack corresponding TOOL result messages. This handles both interrupt recovery (agent was
     * interrupted mid-tool-execution) and session resumption (history loaded from storage with
     * incomplete tool calls).
     *
     * <p>Runs once per {@link #runLoop()} invocation, before the first iteration.
     */
    void recoverDanglingToolCalls() {
        if (conversationHistory.isEmpty()) {
            return;
        }

        int lastAssistantIdx = findLastAssistantIndex();
        if (lastAssistantIdx < 0) {
            return;
        }

        Msg lastAssistant = conversationHistory.get(lastAssistantIdx);
        List<String> toolCallIds = extractToolCallIds(lastAssistant);

        if (toolCallIds.isEmpty()) {
            return;
        }

        Set<String> answeredIds = collectAnsweredToolUseIds(lastAssistantIdx);
        List<String> danglingIds = findDanglingToolUseIds(toolCallIds, answeredIds);

        if (danglingIds.isEmpty()) {
            return;
        }

        log.warn(
                "Agent '{}' recovering {} dangling tool call(s): {}",
                ctx.agentName(),
                danglingIds.size(),
                danglingIds);

        List<ToolResult> errorResults = buildDanglingErrorResults(danglingIds);
        Msg toolMsg = hookDecisions.buildToolResultMsg(errorResults, conversationHistory);
        conversationHistory.add(toolMsg);
    }

    private int findLastAssistantIndex() {
        for (int i = conversationHistory.size() - 1; i >= 0; i--) {
            if (conversationHistory.get(i).role() == MsgRole.ASSISTANT) {
                return i;
            }
        }
        return -1;
    }

    private List<String> extractToolCallIds(Msg assistantMsg) {
        return assistantMsg.contents().stream()
                .filter(Content.ToolUseContent.class::isInstance)
                .map(Content.ToolUseContent.class::cast)
                .map(Content.ToolUseContent::toolId)
                .toList();
    }

    private Set<String> collectAnsweredToolUseIds(int lastAssistantIdx) {
        Set<String> answeredIds = new HashSet<>();
        for (int i = lastAssistantIdx + 1; i < conversationHistory.size(); i++) {
            Msg msg = conversationHistory.get(i);
            if (msg.role() != MsgRole.TOOL) {
                continue;
            }
            for (Content c : msg.contents()) {
                if (c instanceof Content.ToolResultContent trc) {
                    answeredIds.add(trc.toolUseId());
                }
            }
        }
        return answeredIds;
    }

    private List<String> findDanglingToolUseIds(List<String> toolCallIds, Set<String> answeredIds) {
        return toolCallIds.stream().filter(id -> !answeredIds.contains(id)).toList();
    }

    private List<ToolResult> buildDanglingErrorResults(List<String> danglingIds) {
        return danglingIds.stream()
                .map(
                        id ->
                                new ToolResult(
                                        id,
                                        "Tool call interrupted \u2014 no result available",
                                        true,
                                        Map.of()))
                .toList();
    }

    /**
     * Resolve the effective list of {@link ResourceConstraint}s from the agent config.
     *
     * <p>If the config contains an explicit {@code resourceConstraints} list, it is used as-is
     * (including an empty list, which opts out of all constraints). Otherwise, a {@link
     * DefaultResourceConstraint} is auto-created from the config's maxIterations, tokenBudget, and
     * timeout.
     */
    private static List<ResourceConstraint> resolveResourceConstraints(AgentConfig config) {
        if (config != null && config.resourceConstraints() != null) {
            return config.resourceConstraints();
        }
        if (config != null) {
            return List.of(
                    new DefaultResourceConstraint(
                            config.maxIterations(), config.tokenBudget(), config.timeout()));
        }
        return List.of();
    }
}
