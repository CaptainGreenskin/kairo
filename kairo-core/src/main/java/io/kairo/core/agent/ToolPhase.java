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

import io.kairo.api.exception.AgentInterruptedException;
import io.kairo.api.execution.ExecutionEventType;
import io.kairo.api.hook.*;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.execution.ExecutionEventEmitter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The tool execution phase of the ReAct loop.
 *
 * <p>Handles tool selection from model response, single and sequential tool execution with
 * PreActing/PostActing hooks, loop detection, compaction triggers, and skill tool restrictions.
 *
 * <p>Package-private: not part of the public API.
 */
class ToolPhase {

    private static final Logger log = LoggerFactory.getLogger(ToolPhase.class);

    private final ReActLoopContext ctx;
    private final IterationGuards guards;
    private final HookDecisionApplier hookDecisions;
    private final List<Msg> conversationHistory;
    private final LoopDetector loopDetector;
    private final AtomicInteger currentIteration;
    @Nullable private final ExecutionEventEmitter eventEmitter;
    private CompactionTrigger compactionTrigger;

    ToolPhase(
            ReActLoopContext ctx,
            IterationGuards guards,
            HookDecisionApplier hookDecisions,
            List<Msg> conversationHistory,
            LoopDetector loopDetector,
            AtomicInteger currentIteration) {
        this(ctx, guards, hookDecisions, conversationHistory, loopDetector, currentIteration, null);
    }

    ToolPhase(
            ReActLoopContext ctx,
            IterationGuards guards,
            HookDecisionApplier hookDecisions,
            List<Msg> conversationHistory,
            LoopDetector loopDetector,
            AtomicInteger currentIteration,
            @Nullable ExecutionEventEmitter eventEmitter) {
        this.ctx = ctx;
        this.guards = guards;
        this.hookDecisions = hookDecisions;
        this.conversationHistory = conversationHistory;
        this.loopDetector = loopDetector;
        this.currentIteration = currentIteration;
        this.eventEmitter = eventEmitter;
    }

    void setCompactionTrigger(CompactionTrigger compactionTrigger) {
        this.compactionTrigger = compactionTrigger;
    }

    /**
     * Execute tool calls and continue the loop after completion.
     *
     * <p>Handles loop detection, tool execution with hooks, skill restrictions, compaction, and
     * recursion back into the main loop.
     *
     * @param toolCalls the tool calls from the model response
     * @param loopContinuation supplier for the next loop iteration
     * @return the final response from the continued loop
     */
    Mono<Msg> executeAndContinue(
            List<Content.ToolUseContent> toolCalls, Supplier<Mono<Msg>> loopContinuation) {
        log.debug(
                "Agent '{}' requesting {} tool call(s): {}",
                ctx.agentName(),
                toolCalls.size(),
                toolCalls.stream()
                        .map(Content.ToolUseContent::toolName)
                        .collect(Collectors.joining(", ")));

        Mono<Msg> loopDecision = evaluateLoopDetection(toolCalls, loopContinuation);
        if (loopDecision != null) {
            return loopDecision;
        }

        return runToolExecutionPipeline(toolCalls, loopContinuation);
    }

    private Mono<Msg> evaluateLoopDetection(
            List<Content.ToolUseContent> toolCalls, Supplier<Mono<Msg>> loopContinuation) {
        var detection = loopDetector.check(toolCalls);
        if (detection.level() == LoopDetector.DetectionResult.Level.HARD_STOP) {
            return Mono.error(new LoopDetectionException(detection.message()));
        }
        if (detection.level() == LoopDetector.DetectionResult.Level.WARN) {
            conversationHistory.add(Msg.of(MsgRole.USER, "[Loop Warning] " + detection.message()));
            return loopContinuation.get();
        }
        return null;
    }

    private Mono<Msg> runToolExecutionPipeline(
            List<Content.ToolUseContent> toolCalls, Supplier<Mono<Msg>> loopContinuation) {
        return guards.checkCancelled()
                .then(executeToolsWithHooks(toolCalls))
                .flatMap(
                        toolResults ->
                                continueAfterToolExecution(
                                        toolCalls, toolResults, loopContinuation));
    }

    private Mono<Msg> continueAfterToolExecution(
            List<Content.ToolUseContent> toolCalls,
            List<ToolResult> toolResults,
            Supplier<Mono<Msg>> loopContinuation) {
        applySkillToolRestrictions(toolCalls, toolResults);
        conversationHistory.add(hookDecisions.buildToolResultMsg(toolResults, conversationHistory));
        currentIteration.incrementAndGet();

        return proceedWithCompactionIfNeeded(loopContinuation);
    }

    private Mono<Msg> proceedWithCompactionIfNeeded(Supplier<Mono<Msg>> loopContinuation) {
        if (compactionTrigger != null) {
            int messagesBefore = conversationHistory.size();
            return guards.checkCancelled()
                    .then(compactionTrigger.checkAndCompact(conversationHistory))
                    .flatMap(
                            compacted -> {
                                if (Boolean.TRUE.equals(compacted)) {
                                    return emitContextCompacted(
                                                    messagesBefore, conversationHistory.size())
                                            .then(loopContinuation.get());
                                }
                                return loopContinuation.get();
                            });
        }
        return guards.checkCancelled().then(loopContinuation.get());
    }

    /**
     * Emit a CONTEXT_COMPACTED event after compaction with before/after message counts.
     * Best-effort: errors are logged and swallowed.
     */
    private Mono<Void> emitContextCompacted(int messagesBefore, int messagesAfter) {
        if (eventEmitter == null) {
            return Mono.empty();
        }
        String payload =
                "{\"iteration\":"
                        + currentIteration.get()
                        + ",\"messagesBefore\":"
                        + messagesBefore
                        + ",\"messagesAfter\":"
                        + messagesAfter
                        + "}";
        return eventEmitter
                .emit(ExecutionEventType.CONTEXT_COMPACTED, payload)
                .onErrorResume(
                        e -> {
                            log.warn("Failed to emit CONTEXT_COMPACTED event: {}", e.getMessage());
                            return Mono.empty();
                        });
    }

    // ---- Tool execution with hooks ----

    /** Execute a list of tool calls, firing PreActing/PostActing hooks for each. */
    private Mono<List<ToolResult>> executeToolsWithHooks(List<Content.ToolUseContent> toolCalls) {
        if (ctx.toolExecutor() == null) {
            return Mono.just(noToolExecutorResults(toolCalls));
        }
        return executeToolCallsSequentially(toolCalls);
    }

    private List<ToolResult> noToolExecutorResults(List<Content.ToolUseContent> toolCalls) {
        return toolCalls.stream()
                .map(
                        tc ->
                                new ToolResult(
                                        tc.toolId(), "No tool executor configured", true, Map.of()))
                .toList();
    }

    private Mono<List<ToolResult>> executeToolCallsSequentially(
            List<Content.ToolUseContent> toolCalls) {
        return Flux.fromIterable(toolCalls)
                .concatMap(
                        toolCall ->
                                guards.checkCancelled().then(executeSingleToolWithHooks(toolCall)))
                .collectList();
    }

    /** Execute a single tool call with PreActing and PostActing hooks. */
    private Mono<ToolResult> executeSingleToolWithHooks(Content.ToolUseContent toolCall) {
        Instant toolStart = Instant.now();
        return firePreActing(toolCall)
                .map(hookResult -> planToolExecution(toolCall, hookResult))
                .flatMap(this::executePlannedTool)
                .flatMap(
                        result ->
                                emitToolCallResponse(toolCall.toolId(), toolCall.toolName(), result)
                                        .then(
                                                finishToolExecutionPipeline(
                                                        toolCall.toolName(), result, toolStart)));
    }

    /**
     * Emit a TOOL_CALL_RESPONSE event with the toolCallId for correlation-based recovery matching.
     * Best-effort: errors are logged and swallowed.
     */
    private Mono<Void> emitToolCallResponse(String toolCallId, String toolName, ToolResult result) {
        if (eventEmitter == null) {
            return Mono.empty();
        }
        String payload =
                "{\"toolCallId\":\""
                        + toolCallId
                        + "\",\"toolName\":\""
                        + toolName
                        + "\",\"result\":\""
                        + escapeJson(result.content())
                        + "\",\"isError\":"
                        + result.isError()
                        + "}";
        return eventEmitter
                .emit(ExecutionEventType.TOOL_CALL_RESPONSE, payload)
                .onErrorResume(
                        e -> {
                            log.warn(
                                    "Failed to emit TOOL_CALL_RESPONSE for {}: {}",
                                    toolName,
                                    e.getMessage());
                            return Mono.empty();
                        });
    }

    /** Minimal JSON string escaping for embedding values in JSON payloads. */
    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\f", "\\f");
    }

    private Mono<HookResult<PreActingEvent>> firePreActing(Content.ToolUseContent toolCall) {
        return guards.checkCancelled()
                .then(
                        ctx.hookChain()
                                .<PreActingEvent>firePreActingWithResult(
                                        new PreActingEvent(
                                                toolCall.toolName(), toolCall.input(), false)));
    }

    private Mono<ToolResult> finishToolExecutionPipeline(
            String toolName, ToolResult result, Instant toolStart) {
        return emitToolResultEvent(toolName, result, toolStart)
                .flatMap(emitted -> firePostActingAndReturn(toolName, emitted));
    }

    private ToolExecutionPlan planToolExecution(
            Content.ToolUseContent toolCall, HookResult<PreActingEvent> hookResult) {
        if (!hookResult.shouldProceed()) {
            log.info("Tool '{}' blocked by hook: {}", toolCall.toolName(), hookResult.reason());
            return ToolExecutionPlan.terminal(
                    toolCall.toolId(),
                    toolCall.toolName(),
                    blockedByHookToolResult(toolCall.toolId(), hookResult.reason()));
        }

        if (hookResult.shouldSkip()) {
            log.info("Tool '{}' skipped by hook: {}", toolCall.toolName(), hookResult.reason());
            return ToolExecutionPlan.terminal(
                    toolCall.toolId(),
                    toolCall.toolName(),
                    skippedByHookToolResult(toolCall.toolId()));
        }

        hookDecisions.applyInjections(hookResult, conversationHistory);

        PreActingEvent pre = hookResult.event();
        if (pre.cancelled()) {
            log.info("Tool '{}' execution cancelled by hook", toolCall.toolName());
            return ToolExecutionPlan.terminal(
                    toolCall.toolId(),
                    toolCall.toolName(),
                    cancelledByHookToolResult(toolCall.toolId()));
        }

        return ToolExecutionPlan.execute(
                toolCall.toolId(),
                toolCall.toolName(),
                resolveEffectiveToolInput(toolCall, hookResult));
    }

    private Map<String, Object> resolveEffectiveToolInput(
            Content.ToolUseContent toolCall, HookResult<PreActingEvent> hookResult) {
        return hookResult.hasModifiedInput() ? hookResult.modifiedInput() : toolCall.input();
    }

    private Mono<ToolResult> executePlannedTool(ToolExecutionPlan plan) {
        if (!plan.shouldExecute()) {
            return Mono.just(plan.terminalResult());
        }
        return executeToolCall(plan.toolUseId(), plan.toolName(), plan.input());
    }

    private ToolResult blockedByHookToolResult(String toolUseId, String reason) {
        return new ToolResult(
                toolUseId,
                "Tool execution blocked by hook: " + (reason != null ? reason : "no reason given"),
                true,
                Map.of("error_code", "tool_blocked_by_hook"));
    }

    private ToolResult skippedByHookToolResult(String toolUseId) {
        return new ToolResult(
                toolUseId,
                "Tool execution skipped by hook",
                false,
                Map.of("skipped_by_hook", true, "result_code", "tool_skipped_by_hook"));
    }

    private ToolResult cancelledByHookToolResult(String toolUseId) {
        return new ToolResult(
                toolUseId,
                "Tool execution cancelled by hook",
                true,
                Map.of("error_code", "tool_cancelled_by_hook"));
    }

    private Mono<ToolResult> executeToolCall(
            String toolUseId, String toolName, Map<String, Object> input) {
        return guards.checkCancelled()
                .then(
                        guards.withCancellationSignal(
                                Mono.defer(() -> ctx.toolExecutor().execute(toolName, input))))
                .transform(guards::withCooperativeCancellation)
                .map(
                        result ->
                                new ToolResult(
                                        toolUseId,
                                        result.content(),
                                        result.isError(),
                                        result.metadata()))
                .onErrorResume(
                        e -> {
                            if (isCancellationException(e)) {
                                return Mono.error(e);
                            }
                            log.error("Tool '{}' execution failed: {}", toolName, e.getMessage());
                            return Mono.just(
                                    new ToolResult(
                                            toolUseId,
                                            "Error executing tool: " + e.getMessage(),
                                            true,
                                            Map.of(
                                                    "error_code",
                                                    "tool_execution_failed",
                                                    "error_type",
                                                    e.getClass().getSimpleName(),
                                                    "tool_name",
                                                    toolName)));
                        });
    }

    private Mono<ToolResult> firePostActingAndReturn(String toolName, ToolResult result) {
        PostActingEvent postEvent = new PostActingEvent(toolName, result);
        return guards.checkCancelled()
                .then(
                        Mono.defer(
                                () ->
                                        ctx.hookChain()
                                                .<PostActingEvent>firePostActingWithResult(
                                                        postEvent)
                                                .map(
                                                        postResult -> {
                                                            if (postResult.hasInjectedContext()) {
                                                                conversationHistory.add(
                                                                        hookDecisions
                                                                                .systemHookContextMessage(
                                                                                        postResult
                                                                                                .injectedContext()));
                                                            }
                                                            return result;
                                                        })));
    }

    /** Emit OnToolResult hook as a best-effort side-effect without breaking tool flow. */
    private Mono<ToolResult> emitToolResultEvent(
            String toolName, ToolResult toolResult, Instant toolStart) {
        Duration toolDuration = Duration.between(toolStart, Instant.now());
        return ctx.hookChain()
                .fireOnToolResult(
                        new ToolResultEvent(
                                toolName, toolResult, toolDuration, !toolResult.isError()))
                .onErrorResume(
                        e -> {
                            if (isCancellationException(e)) {
                                return Mono.error(e);
                            }
                            log.warn(
                                    "OnToolResult hook failed for tool '{}': {}",
                                    toolName,
                                    e.getMessage());
                            return Mono.empty();
                        })
                .thenReturn(toolResult);
    }

    // ---- Skill tool restrictions ----

    /** Apply skill_load tool restrictions from tool results. */
    private void applySkillToolRestrictions(
            List<Content.ToolUseContent> toolCalls, List<ToolResult> toolResults) {
        if (ctx.toolExecutor() == null) {
            return;
        }
        for (int i = 0; i < toolResults.size(); i++) {
            ToolResult tr = toolResults.get(i);
            String toolName = i < toolCalls.size() ? toolCalls.get(i).toolName() : null;
            if (!"skill_load".equals(toolName) || tr.metadata() == null) {
                continue;
            }
            Set<String> allowed = parseAllowedTools(tr.metadata().get("allowedTools"));
            if (!allowed.isEmpty()) {
                ctx.toolExecutor().setAllowedTools(allowed);
                log.info("Skill tool restrictions activated: {}", allowed);
            }
        }
    }

    private Set<String> parseAllowedTools(Object allowedTools) {
        if (!(allowedTools instanceof List<?> toolList)) {
            return Set.of();
        }
        return toolList.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.toSet());
    }

    // ---- Cancellation detection ----

    /**
     * Check if the given exception represents a cancellation that should propagate instead of being
     * converted to a ToolResult.
     */
    private static boolean isCancellationException(Throwable e) {
        if (e instanceof AgentInterruptedException) {
            return true;
        }
        if (e instanceof java.util.concurrent.CancellationException) {
            return true;
        }
        Throwable cause = e.getCause();
        return cause != null
                && (cause instanceof AgentInterruptedException
                        || cause instanceof java.util.concurrent.CancellationException);
    }

    // ---- Internal record ----

    private record ToolExecutionPlan(
            String toolUseId,
            String toolName,
            Map<String, Object> input,
            ToolResult terminalResult) {
        static ToolExecutionPlan execute(
                String toolUseId, String toolName, Map<String, Object> input) {
            return new ToolExecutionPlan(toolUseId, toolName, input, null);
        }

        static ToolExecutionPlan terminal(
                String toolUseId, String toolName, ToolResult terminalResult) {
            return new ToolExecutionPlan(toolUseId, toolName, null, terminalResult);
        }

        boolean shouldExecute() {
            return terminalResult == null;
        }
    }
}
