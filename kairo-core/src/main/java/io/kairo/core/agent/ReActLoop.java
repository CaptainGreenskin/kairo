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
import io.kairo.api.agent.IterationSignal;
import io.kairo.api.event.AgentProgressEvent;
import io.kairo.api.event.KairoEventBus;
import io.kairo.api.execution.ExecutionEventType;
import io.kairo.api.execution.ResourceConstraint;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.tool.OutputBudgetConfig;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tracing.Span;
import io.kairo.api.tracing.Tracer;
import io.kairo.core.agent.checkpoint.IterationCheckpointManager;
import io.kairo.core.execution.DefaultResourceConstraint;
import io.kairo.core.execution.ExecutionEventEmitter;
import io.kairo.core.tool.DefaultToolExecutor;
import io.kairo.core.tool.OutputBudgetTracker;
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

    /** Default max consecutive Skip signals before aborting (env-configurable). */
    private static final int DEFAULT_MAX_CONSECUTIVE_SKIPS =
            Integer.parseInt(
                    System.getenv().getOrDefault("KAIRO_AGENT_MAX_CONSECUTIVE_SKIPS", "5"));

    /** Environment flag to disable loop rescue (default: enabled). */
    private static final String LOOP_RESCUE_ENV = "KAIRO_LOOP_DETECT_RESCUE";

    /** Rescue prompt injected as a USER message on first loop detection. */
    private static final String RESCUE_PROMPT =
            "It looks like you're repeating the same approach. "
                    + "Please stop and think about what's not working. "
                    + "Try a completely different approach or ask yourself: "
                    + "what assumption might be wrong?";

    private final List<Msg> conversationHistory;
    private final AtomicInteger currentIteration;
    private final Supplier<ModelConfig> modelConfigSupplier;
    private volatile boolean streamingEnabled = false;
    private volatile java.util.function.Consumer<String> textDeltaConsumer = null;
    private final AtomicBoolean danglingRecoveryDone = new AtomicBoolean(false);

    // ---- Loop control fields ----
    private final int maxConsecutiveSkips = DEFAULT_MAX_CONSECUTIVE_SKIPS;
    private final AtomicInteger consecutiveSkips = new AtomicInteger(0);
    private final LoopDetector loopDetector;
    private final ToolCallHistory toolCallHistory;
    private final AtomicBoolean loopRescueAttempted = new AtomicBoolean(false);

    @Nullable private final ExecutionEventEmitter eventEmitter;
    @Nullable private final AgentProgressTracker progressTracker;
    @Nullable private volatile IterationCheckpointManager checkpointManager;
    @Nullable private volatile KairoEventBus eventBus;
    @Nullable private volatile CompactionTrigger compactionTrigger;
    @Nullable private volatile StallDetector stallDetector;

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
        this(
                ctx,
                interrupted,
                currentIteration,
                totalTokensUsed,
                modelConfigSupplier,
                eventEmitter,
                null);
    }

    ReActLoop(
            ReActLoopContext ctx,
            AtomicBoolean interrupted,
            AtomicInteger currentIteration,
            AtomicLong totalTokensUsed,
            Supplier<ModelConfig> modelConfigSupplier,
            @Nullable ExecutionEventEmitter eventEmitter,
            @Nullable AgentProgressTracker progressTracker) {
        this.ctx = ctx;
        this.conversationHistory = new CopyOnWriteArrayList<>();
        this.currentIteration = currentIteration;
        this.modelConfigSupplier = modelConfigSupplier;
        this.eventEmitter = eventEmitter;
        this.progressTracker = progressTracker;

        // Initialize loop detector from config thresholds (owned by ReActLoop for dispatcher use)
        this.loopDetector =
                new LoopDetector(
                        ctx.config().loopHashWarnThreshold(),
                        ctx.config().loopHashHardLimit(),
                        ctx.config().loopFreqWarnThreshold(),
                        ctx.config().loopFreqHardLimit(),
                        ctx.config().loopFreqWindow(),
                        3);
        this.toolCallHistory = new ToolCallHistory();

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
                        () -> textDeltaConsumer,
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
        this.compactionTrigger = compactionTrigger;
        this.toolPhase.setCompactionTrigger(compactionTrigger);
    }

    void setCheckpointManager(@Nullable IterationCheckpointManager checkpointManager) {
        this.checkpointManager = checkpointManager;
        this.toolPhase.setCheckpointManager(checkpointManager);
    }

    /** Returns the total number of tool calls executed in this loop. */
    int getTotalToolCalls() {
        return toolPhase.getTotalToolCalls();
    }

    @Nullable
    AgentProgressTracker getProgressTracker() {
        return progressTracker;
    }

    void setStreamingEnabled(boolean enabled) {
        this.streamingEnabled = enabled;
    }

    boolean isStreamingEnabled() {
        return streamingEnabled;
    }

    void setTextDeltaConsumer(java.util.function.Consumer<String> consumer) {
        this.textDeltaConsumer = consumer;
    }

    void setStallDetector(@Nullable StallDetector stallDetector) {
        this.stallDetector = stallDetector;
    }

    void setEventBus(@Nullable KairoEventBus eventBus) {
        this.eventBus = eventBus;
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
        // Publish ITERATION_START event (best-effort, must not break the loop)
        publishIterationStart();

        // Wrap in deferContextual to access Reactor Context for span hierarchy
        return Mono.deferContextual(
                        ctxView -> {
                            Tracer t = ctx.tracer();
                            if (t == null) {
                                // No tracer available (test/headless path) — skip span creation
                                return preemptiveCompactIfNeeded()
                                        .then(Mono.defer(this::evaluateGuardsAndExecute));
                            }

                            // Retrieve parent agent span from Reactor Context
                            Span parentSpan =
                                    ctxView.hasKey(DefaultToolExecutor.SPAN_CONTEXT_KEY)
                                            ? ctxView.get(DefaultToolExecutor.SPAN_CONTEXT_KEY)
                                            : null;

                            // Start iteration-level span (child of agent span)
                            Span iterationSpan =
                                    t.startIterationSpan(parentSpan, currentIteration.get());

                            // Feed diagnostics with current span info
                            if (ctxView.hasKey(MutableDiagnostics.class)) {
                                MutableDiagnostics diag = ctxView.get(MutableDiagnostics.class);
                                if (iterationSpan.spanId() != null) {
                                    diag.setCurrentSpanId(iterationSpan.spanId());
                                }
                            }

                            return preemptiveCompactIfNeeded()
                                    .then(Mono.defer(this::evaluateGuardsAndExecute))
                                    .doOnSuccess(
                                            msg -> {
                                                iterationSpan.setStatus(true, "completed");
                                                iterationSpan.end();
                                            })
                                    .doOnError(
                                            e -> {
                                                iterationSpan.setStatus(false, e.getMessage());
                                                t.recordException(iterationSpan, e);
                                                iterationSpan.end();
                                            })
                                    .doOnCancel(
                                            () -> {
                                                iterationSpan.setStatus(false, "cancelled");
                                                iterationSpan.end();
                                            })
                                    // Override SPAN_CONTEXT_KEY so tool spans nest under iteration
                                    .contextWrite(
                                            c ->
                                                    c.put(
                                                            DefaultToolExecutor.SPAN_CONTEXT_KEY,
                                                            iterationSpan));
                        })
                // Keep existing OutputBudgetTracker contextWrite BELOW
                .contextWrite(
                        ctx -> {
                            // Inject a fresh OutputBudgetTracker per iteration via Reactor Context.
                            // Reads the OutputBudgetConfig from the ToolContext (set by
                            // DefaultReActAgent).
                            OutputBudgetConfig budgetConfig = OutputBudgetConfig.DEFAULT;
                            if (ctx.hasKey(DefaultToolExecutor.CONTEXT_KEY)) {
                                ToolContext tc = ctx.get(DefaultToolExecutor.CONTEXT_KEY);
                                if (tc.budget() != null) {
                                    budgetConfig = tc.budget();
                                }
                            }
                            return ctx.put(
                                    OutputBudgetTracker.CONTEXT_KEY,
                                    new OutputBudgetTracker(budgetConfig));
                        });
    }

    private Mono<Void> preemptiveCompactIfNeeded() {
        CompactionTrigger trigger = compactionTrigger;
        if (trigger == null) {
            return Mono.empty();
        }
        return trigger.checkAndCompact(conversationHistory).then();
    }

    private Mono<Msg> evaluateGuardsAndExecute() {
        return guards.evaluate()
                .switchIfEmpty(
                        Mono.defer(
                                () -> {
                                    Mono<Msg> execution =
                                            reasoningPhase
                                                    .execute(modelConfigSupplier.get())
                                                    .flatMap(this::dispatchSignal);

                                    // Update progress tracker and emit ITERATION_COMPLETE
                                    // (both best-effort — must not break the loop)
                                    execution =
                                            execution.doOnNext(
                                                    msg -> {
                                                        if (progressTracker != null) {
                                                            progressTracker.update(
                                                                    currentIteration.get(),
                                                                    "Iteration "
                                                                            + currentIteration.get()
                                                                            + " complete",
                                                                    toolPhase.getTotalToolCalls(),
                                                                    0);
                                                        }
                                                    });

                                    // Publish ITERATION_END event
                                    execution = execution.doOnNext(msg -> publishIterationEnd(msg));

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
     * Dispatch an {@link IterationSignal} returned by ReasoningPhase or ToolPhase. Maps each signal
     * case to the appropriate loop action.
     *
     * <p>This is the "two-level dispatcher" per the v3 design:
     *
     * <ul>
     *   <li>Level 1: Route signals to the correct handler
     *   <li>Level 2 (ToolCallsRequested): Loop detection guard before tool execution
     * </ul>
     */
    private Mono<Msg> dispatchSignal(IterationSignal sig) {
        log.info(
                "react.signal iter={} signal={} reason={}",
                currentIteration.get(),
                sig.getClass().getSimpleName(),
                extractReason(sig));

        return Mono.deferContextual(
                ctxView -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Consumer<String> recorder =
                            (java.util.function.Consumer<String>)
                                    ctxView.getOrDefault(
                                            io.kairo.core.hook.DefaultHookChain
                                                    .DIAGNOSTICS_RECORDER_KEY,
                                            null);
                    if (recorder != null) {
                        recorder.accept("signal:" + sig.getClass().getSimpleName());
                    }
                    return dispatchSignalInternal(sig);
                });
    }

    private Mono<Msg> dispatchSignalInternal(IterationSignal sig) {
        return switch (sig) {
            case IterationSignal.ToolCallsRequested tcr -> {
                consecutiveSkips.set(0); // non-Skip → reset
                // ── Loop detection guard (preamble) ──
                IterationSignal loopResult = evaluateLoopDetection(tcr.calls());
                if (loopResult != null) {
                    yield dispatchSignal(loopResult);
                }
                // Clean — proceed with tool execution; pause stall detector during
                // tool execution (bash can block for minutes compiling/running servers)
                yield Mono.fromRunnable(
                                () -> {
                                    if (stallDetector != null) stallDetector.pause();
                                })
                        .then(toolPhase.execute(tcr.calls()))
                        .doFinally(
                                s -> {
                                    if (stallDetector != null) stallDetector.resume();
                                })
                        .flatMap(this::dispatchSignal);
            }
            case IterationSignal.ContinueAfterTools ignored -> {
                consecutiveSkips.set(0);
                currentIteration.incrementAndGet();
                yield Mono.defer(this::runLoop);
            }
            case IterationSignal.ContinueWithNudge n -> {
                consecutiveSkips.set(0);
                currentIteration.incrementAndGet();
                conversationHistory.add(n.syntheticMsg());
                yield Mono.defer(this::runLoop);
            }
            case IterationSignal.CompactThenContinue cc -> {
                consecutiveSkips.set(0);
                currentIteration.incrementAndGet();
                if (compactionTrigger != null) {
                    yield compactionTrigger
                            .checkAndCompact(conversationHistory)
                            .then(Mono.defer(this::runLoop));
                }
                yield Mono.defer(this::runLoop);
            }
            case IterationSignal.Skip s -> {
                // Don't increment iter counter (model wasn't called, no max-iter budget consumed)
                int skips = consecutiveSkips.incrementAndGet();
                if (skips > maxConsecutiveSkips) {
                    yield dispatchSignal(
                            new IterationSignal.Abort(
                                    new IllegalStateException(
                                            "agent stuck on Skip x"
                                                    + skips
                                                    + " (last: "
                                                    + s.reason()
                                                    + ")"),
                                    "max-consecutive-skip exceeded"));
                }
                log.info(
                        "react.skip iter={} consecutive={} reason={}",
                        currentIteration.get(),
                        skips,
                        s.reason());
                yield Mono.defer(this::runLoop);
            }
            case IterationSignal.Complete c -> {
                consecutiveSkips.set(0);
                yield firePreCompleteHook(c.finalAnswer());
            }
            case IterationSignal.LoopDetected ld -> {
                consecutiveSkips.set(0);
                yield Mono.error(new LoopDetectionException(ld.info().message()));
            }
            case IterationSignal.Abort a -> {
                Throwable cause =
                        a.cause() != null ? a.cause() : new IllegalStateException(a.reason());
                yield Mono.error(cause);
            }
        };
    }

    /**
     * Evaluate loop detection for the given tool calls (dispatcher preamble).
     *
     * <p>Runs Layer 0 (per-call repetition via {@link ToolCallHistory}) and Layers 1-3 (set-level
     * hash/frequency via {@link LoopDetector}). Returns an {@link IterationSignal} if a loop
     * condition was detected; {@code null} means execution should proceed normally.
     */
    IterationSignal evaluateLoopDetection(List<Content.ToolUseContent> toolCalls) {
        // Layer 0: per-call repetition check (ToolCallHistory)
        LoopDetector.DetectionResult.Level worstPerCall = LoopDetector.DetectionResult.Level.NONE;
        String perCallMessage = null;
        for (Content.ToolUseContent tc : toolCalls) {
            String argsJson = tc.input() != null ? tc.input().toString() : "";
            ToolCallHistory.Status status = toolCallHistory.record(tc.toolName(), argsJson);
            if (status == ToolCallHistory.Status.ABORT) {
                worstPerCall = LoopDetector.DetectionResult.Level.HARD_STOP;
                perCallMessage =
                        "Tool call loop detected: '"
                                + tc.toolName()
                                + "' called "
                                + toolCallHistory.abortAt()
                                + " consecutive times with same arguments \u2014 aborting";
                break;
            }
            if (status == ToolCallHistory.Status.WARN
                    && worstPerCall != LoopDetector.DetectionResult.Level.HARD_STOP) {
                worstPerCall = LoopDetector.DetectionResult.Level.WARN;
                perCallMessage =
                        "Potential tool call loop: '"
                                + tc.toolName()
                                + "' called "
                                + toolCallHistory.warnAt()
                                + " consecutive times with same arguments";
            }
        }

        if (worstPerCall == LoopDetector.DetectionResult.Level.HARD_STOP) {
            if (!isRescueEnabled() || loopRescueAttempted.getAndSet(true)) {
                return new IterationSignal.LoopDetected(
                        new IterationSignal.LoopDetectionInfo(perCallMessage, "HARD_STOP"));
            }
            conversationHistory.add(Msg.of(MsgRole.USER, "[Loop Rescue] " + RESCUE_PROMPT));
            log.warn("Tool call loop detected \u2014 injecting rescue prompt (attempt 1/1)");
            return new IterationSignal.Skip("loop rescue: " + perCallMessage);
        }
        if (worstPerCall == LoopDetector.DetectionResult.Level.WARN) {
            conversationHistory.add(
                    Msg.of(
                            MsgRole.USER,
                            "[Loop Warning] " + (perCallMessage != null ? perCallMessage : "")));
            return new IterationSignal.Skip("loop warn: " + perCallMessage);
        }

        // Layer 1-3: set-level hash/frequency/repetition check (LoopDetector)
        var detection = loopDetector.check(toolCalls);
        if (detection.level() == LoopDetector.DetectionResult.Level.HARD_STOP) {
            if (!isRescueEnabled() || loopRescueAttempted.getAndSet(true)) {
                return new IterationSignal.LoopDetected(
                        new IterationSignal.LoopDetectionInfo(detection.message(), "HARD_STOP"));
            }
            conversationHistory.add(Msg.of(MsgRole.USER, "[Loop Rescue] " + RESCUE_PROMPT));
            log.warn("Loop detected \u2014 injecting rescue prompt (attempt 1/1)");
            return new IterationSignal.Skip("loop rescue: " + detection.message());
        }
        if (detection.level() == LoopDetector.DetectionResult.Level.WARN) {
            conversationHistory.add(Msg.of(MsgRole.USER, "[Loop Warning] " + detection.message()));
            return new IterationSignal.Skip("loop warn: " + detection.message());
        }
        return null;
    }

    /** Check whether loop rescue is enabled via environment flag. */
    private static boolean isRescueEnabled() {
        String env = System.getenv(LOOP_RESCUE_ENV);
        return env == null || !env.equalsIgnoreCase("false");
    }

    /**
     * Fire the pre-complete hook and return the final answer. Note: The PRE_COMPLETE hook is
     * already fired by {@link ReasoningPhase} before producing the Complete signal. This method
     * exists as a clean dispatcher exit point.
     */
    private Mono<Msg> firePreCompleteHook(Msg finalAnswer) {
        return Mono.just(finalAnswer);
    }

    /** Extract a human-readable reason from the signal for structured logging. */
    private String extractReason(IterationSignal sig) {
        if (sig instanceof IterationSignal.Skip s) return s.reason();
        if (sig instanceof IterationSignal.Abort a) return a.reason();
        if (sig instanceof IterationSignal.CompactThenContinue c) return c.reason();
        if (sig instanceof IterationSignal.ContinueWithNudge n) return n.reason();
        if (sig instanceof IterationSignal.LoopDetected ld) return ld.info().message();
        return "";
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

    // ---- Progress event publishing (best-effort) ----

    private void publishIterationStart() {
        if (eventBus == null) return;
        eventBus.publish(
                new AgentProgressEvent(
                                ctx.agentName(),
                                AgentProgressEvent.Phase.ITERATION_START,
                                currentIteration.get() + 1,
                                null,
                                0,
                                0,
                                0)
                        .toKairoEvent());
    }

    private void publishIterationEnd(Msg result) {
        if (eventBus == null) return;
        String summary =
                result.text() != null && !result.text().isEmpty()
                        ? result.text().substring(0, Math.min(200, result.text().length()))
                        : "iteration complete";
        eventBus.publish(
                new AgentProgressEvent(
                                ctx.agentName(),
                                AgentProgressEvent.Phase.ITERATION_END,
                                currentIteration.get(),
                                summary,
                                0,
                                0,
                                0)
                        .toKairoEvent());
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
                .map(id -> ToolResult.error(id, "Tool call interrupted \u2014 no result available"))
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
