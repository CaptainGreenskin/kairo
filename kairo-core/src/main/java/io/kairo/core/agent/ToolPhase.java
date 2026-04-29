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
import io.kairo.api.tool.ToolInvocation;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.agent.checkpoint.IterationCheckpointManager;
import io.kairo.core.execution.ExecutionEventEmitter;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
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

    /** Environment flag to disable loop rescue (default: enabled). */
    private static final String LOOP_RESCUE_ENV = "KAIRO_LOOP_DETECT_RESCUE";

    /** Rescue prompt injected as a USER message on first loop detection. */
    private static final String RESCUE_PROMPT =
            "It looks like you're repeating the same approach. "
                    + "Please stop and think about what's not working. "
                    + "Try a completely different approach or ask yourself: "
                    + "what assumption might be wrong?";

    private final ReActLoopContext ctx;
    private final AtomicInteger totalToolCallsCounter = new AtomicInteger(0);
    private final IterationGuards guards;
    private final HookDecisionApplier hookDecisions;
    private final List<Msg> conversationHistory;
    private final LoopDetector loopDetector;
    private final AtomicInteger currentIteration;
    @Nullable private final ExecutionEventEmitter eventEmitter;
    @Nullable private volatile IterationCheckpointManager checkpointManager;
    private CompactionTrigger compactionTrigger;

    /** Tracks whether a rescue prompt has already been injected for the current loop. */
    private final AtomicBoolean loopRescueAttempted = new AtomicBoolean(false);

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

    void setCheckpointManager(@Nullable IterationCheckpointManager checkpointManager) {
        // This setter allows the checkpoint manager to be configured after construction.
        // The field is non-final to support this late binding pattern.
        // Note: This intentionally replaces the constructor-injected value.
        this.checkpointManager = checkpointManager;
    }

    /** Returns the total number of tool calls executed since this agent was created. */
    int getTotalToolCalls() {
        return totalToolCallsCounter.get();
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
            if (!isRescueEnabled() || loopRescueAttempted.getAndSet(true)) {
                // Rescue disabled or already attempted — truly stuck
                return Mono.error(new LoopDetectionException(detection.message()));
            }
            // First detection with rescue enabled — inject rescue prompt and continue
            conversationHistory.add(Msg.of(MsgRole.USER, "[Loop Rescue] " + RESCUE_PROMPT));
            log.warn("Loop detected — injecting rescue prompt (attempt 1/1)");
            return loopContinuation.get();
        }
        if (detection.level() == LoopDetector.DetectionResult.Level.WARN) {
            conversationHistory.add(Msg.of(MsgRole.USER, "[Loop Warning] " + detection.message()));
            return loopContinuation.get();
        }
        return null;
    }

    /** Check whether loop rescue is enabled via environment flag. */
    private static boolean isRescueEnabled() {
        String env = System.getenv(LOOP_RESCUE_ENV);
        return env == null || !env.equalsIgnoreCase("false");
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

        // Save iteration checkpoint for crash recovery (best-effort).
        Mono<Msg> loopDecision = proceedWithCompactionIfNeeded(loopContinuation);
        if (checkpointManager != null) {
            int iteration = currentIteration.get();
            List<Msg> historySnapshot = List.copyOf(conversationHistory);
            loopDecision =
                    checkpointManager
                            .save(iteration, historySnapshot)
                            .onErrorResume(
                                    e -> {
                                        log.warn(
                                                "Failed to save iteration checkpoint {}: {}",
                                                iteration,
                                                e.getMessage());
                                        return Mono.empty();
                                    })
                            .then(loopDecision);
        }

        return loopDecision;
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
        return executeToolCallsInParallel(toolCalls);
    }

    private List<ToolResult> noToolExecutorResults(List<Content.ToolUseContent> toolCalls) {
        return toolCalls.stream()
                .map(
                        tc ->
                                new ToolResult(
                                        tc.toolId(), "No tool executor configured", true, Map.of()))
                .toList();
    }

    /**
     * Execute tool calls with parallel dispatch for read-only tools.
     *
     * <p>PreActing hooks fire sequentially (to preserve ordering and allow hooks to block/modify),
     * then the actual tool execution uses {@link ToolExecutor#executeParallel(List)} so read-only
     * tools run concurrently while write tools run serially. PostActing hooks fire per-result.
     */
    private Mono<List<ToolResult>> executeToolCallsInParallel(
            List<Content.ToolUseContent> toolCalls) {
        // Step 1: Fire PreActing hooks and plan execution for each tool call.
        // Step 2: Check cancellation — a hook may have set interrupted=true.
        return firePreActingForAll(toolCalls)
                .flatMap(plans -> guards.checkCancelled().thenReturn(plans))
                .flatMap(plans -> executePlannedBatch(plans, toolCalls));
    }

    /** Fire PreActing hooks for all tool calls sequentially, returning execution plans. */
    private Mono<List<IndexedPlan>> firePreActingForAll(List<Content.ToolUseContent> toolCalls) {
        List<IndexedPlan> plans = new ArrayList<>(toolCalls.size());
        Mono<Void> chain = Mono.empty();
        for (int i = 0; i < toolCalls.size(); i++) {
            final int idx = i;
            final Content.ToolUseContent toolCall = toolCalls.get(i);
            chain =
                    chain.then(guards.checkCancelled())
                            .then(firePreActing(toolCall))
                            .doOnNext(
                                    hookResult -> {
                                        totalToolCallsCounter.incrementAndGet();
                                        ToolExecutionPlan plan =
                                                planToolExecution(toolCall, hookResult);
                                        plans.add(new IndexedPlan(idx, toolCall, plan));
                                    })
                            .then();
        }
        return chain.thenReturn(plans);
    }

    /** Execute a batch of planned tool calls via executeParallel, then fire PostActing hooks. */
    private Mono<List<ToolResult>> executePlannedBatch(
            List<IndexedPlan> plans, List<Content.ToolUseContent> toolCalls) {
        // Separate terminal plans (blocked/skipped by hooks) from executable ones.
        List<IndexedPlan> terminalPlans =
                plans.stream().filter(p -> !p.plan().shouldExecute()).toList();
        List<IndexedPlan> executablePlans =
                plans.stream().filter(p -> p.plan().shouldExecute()).toList();

        if (executablePlans.isEmpty()) {
            // All tools were blocked/skipped by hooks — no execution needed.
            return finishTerminalPlans(terminalPlans, toolCalls);
        }

        // Build invocations for parallel dispatch.
        List<ToolInvocation> invocations =
                executablePlans.stream()
                        .map(p -> new ToolInvocation(p.plan().toolName(), p.plan().input()))
                        .toList();

        // Use executeParallel with cooperative cancellation so interrupt signals terminate tools.
        return guards.withCooperativeCancellation(ctx.toolExecutor().executeParallel(invocations))
                .collectList()
                .flatMap(
                        execResults -> {
                            // Map execution results back to their indexed plans.
                            List<ToolResult> results = new ArrayList<>(toolCalls.size());
                            int execIdx = 0;
                            for (int i = 0; i < toolCalls.size(); i++) {
                                IndexedPlan ip = plans.get(i);
                                ToolResult result;
                                if (ip.plan().shouldExecute()) {
                                    result = execResults.get(execIdx++);
                                    // Replace toolUseId with the original tool call's ID.
                                    result =
                                            new ToolResult(
                                                    ip.toolCall().toolId(),
                                                    result.content(),
                                                    result.isError(),
                                                    result.metadata());
                                } else {
                                    result = ip.plan().terminalResult();
                                }
                                results.add(result);
                            }
                            // Fire PostActing hooks for all results.
                            return firePostActingForAll(results, toolCalls);
                        })
                .onErrorResume(
                        e -> {
                            if (isCancellationException(e)) {
                                return Mono.error(e);
                            }
                            // Fallback: if parallel execution fails, return error results for
                            // executable plans and terminal results for the rest.
                            List<ToolResult> errorResults = new ArrayList<>(toolCalls.size());
                            for (int i = 0; i < toolCalls.size(); i++) {
                                IndexedPlan ip = plans.get(i);
                                if (ip.plan().shouldExecute()) {
                                    errorResults.add(
                                            new ToolResult(
                                                    ip.toolCall().toolId(),
                                                    "Error executing tool: " + e.getMessage(),
                                                    true,
                                                    Map.of(
                                                            "error_code", "tool_execution_failed",
                                                            "error_type",
                                                                    e.getClass().getSimpleName(),
                                                            "tool_name", ip.plan().toolName())));
                                } else {
                                    errorResults.add(ip.plan().terminalResult());
                                }
                            }
                            return Mono.just(errorResults);
                        });
    }

    /** Finish execution for plans that were blocked/skipped by hooks (no tool dispatch needed). */
    private Mono<List<ToolResult>> finishTerminalPlans(
            List<IndexedPlan> terminalPlans, List<Content.ToolUseContent> toolCalls) {
        // Build results in original order.
        List<ToolResult> results = new ArrayList<>(toolCalls.size());
        for (IndexedPlan ip : terminalPlans) {
            while (results.size() < ip.index()) {
                results.add(null);
            }
            results.add(ip.plan().terminalResult());
        }
        // Fire PostActing hooks for terminal results too.
        return firePostActingForAll(results, toolCalls);
    }

    /** Fire PostActing hooks for all results sequentially. */
    private Mono<List<ToolResult>> firePostActingForAll(
            List<ToolResult> results, List<Content.ToolUseContent> toolCalls) {
        List<ToolResult> finalResults = new ArrayList<>(Collections.nCopies(results.size(), null));
        Mono<Void> chain = Mono.empty();
        for (int i = 0; i < results.size(); i++) {
            final int idx = i;
            final ToolResult result = results.get(i);
            final Content.ToolUseContent toolCall = toolCalls.get(i);
            final Instant toolStart = Instant.now();
            chain =
                    chain.then(guards.checkCancelled())
                            .then(
                                    emitToolCallResponse(
                                            toolCall.toolId(), toolCall.toolName(), result))
                            .then(
                                    finishToolExecutionPipeline(
                                            toolCall.toolName(), result, toolStart))
                            .doOnNext(r -> finalResults.set(idx, r))
                            .then();
        }
        return chain.thenReturn(finalResults);
    }

    /** Indexed plan: associates a tool call with its execution plan and original position. */
    private record IndexedPlan(
            int index, Content.ToolUseContent toolCall, ToolExecutionPlan plan) {}

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
        totalToolCallsCounter.incrementAndGet();
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
