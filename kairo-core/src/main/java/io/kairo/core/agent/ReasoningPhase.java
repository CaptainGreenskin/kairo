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

import io.kairo.api.agent.IterationSignal;
import io.kairo.api.execution.ExecutionEventType;
import io.kairo.api.guardrail.*;
import io.kairo.api.hook.*;
import io.kairo.api.hook.PreCompleteEvent;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.model.RawStreamingModelProvider;
import io.kairo.api.model.StreamChunk;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.agent.continuation.AgentContinuationStrategy;
import io.kairo.core.agent.continuation.ContinuationContext;
import io.kairo.core.agent.continuation.ContinuationDecision;
import io.kairo.core.agent.continuation.NoopContinuationStrategy;
import io.kairo.core.execution.ExecutionEventEmitter;
import io.kairo.core.model.DetectedToolCall;
import io.kairo.core.model.StreamingToolDetector;
import io.kairo.core.tool.StreamingToolExecutor;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The reasoning phase of the ReAct loop: pre-hook, model call, post-hook, response processing.
 *
 * <p>Handles streaming vs non-streaming model calls, token accounting, and routes the model
 * response to either a final answer or tool execution via {@link ToolPhase}.
 *
 * <p>The class itself is package-private and not part of the public API. The single exposed surface
 * is {@link #THINKING_DELTA_KEY}, which downstream agents need so they can publish reasoning deltas
 * to a Reactor Context — see kairo-code's {@code AgentService} streaming path.
 */
public class ReasoningPhase {

    private static final Logger log = LoggerFactory.getLogger(ReasoningPhase.class);

    /**
     * Reactor Context key for an optional consumer of thinking-delta strings. When present, the
     * streaming reasoning path forwards every {@code reasoning_content} delta (GLM-5.1, o1, Claude
     * thinking) to the consumer so transports (WebSocket / SSE) can stream the model's thought
     * process to clients in real time. The consumer must not block — it's invoked on the model
     * provider's I/O thread.
     */
    public static final String THINKING_DELTA_KEY = "kairo.thinking-delta-consumer";

    private final ReActLoopContext ctx;
    private final IterationGuards guards;
    private final HookDecisionApplier hookDecisions;
    private final List<Msg> conversationHistory;
    private final AtomicLong totalTokensUsed;
    private final AtomicInteger currentIteration;
    private final Supplier<Boolean> streamingEnabledSupplier;
    private final Supplier<java.util.function.Consumer<String>> textDeltaConsumerSupplier;
    @Nullable private final ExecutionEventEmitter eventEmitter;

    // Continuation strategy support
    private final AtomicInteger nudgeCount = new AtomicInteger(0);
    private volatile ModelResponse.StopReason lastStopReason;

    // Suppresses repeated "GuardrailChain is null" log lines — emitted at most once per phase
    // per ReasoningPhase instance so multi-iteration sessions don't flood the log when guardrails
    // are simply not configured (the common case in dev).
    private final java.util.concurrent.atomic.AtomicBoolean preGuardrailWarnedOnce =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean postGuardrailWarnedOnce =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    ReasoningPhase(
            ReActLoopContext ctx,
            IterationGuards guards,
            HookDecisionApplier hookDecisions,
            List<Msg> conversationHistory,
            AtomicLong totalTokensUsed,
            AtomicInteger currentIteration,
            Supplier<Boolean> streamingEnabledSupplier) {
        this(
                ctx,
                guards,
                hookDecisions,
                conversationHistory,
                totalTokensUsed,
                currentIteration,
                streamingEnabledSupplier,
                () -> null,
                null);
    }

    ReasoningPhase(
            ReActLoopContext ctx,
            IterationGuards guards,
            HookDecisionApplier hookDecisions,
            List<Msg> conversationHistory,
            AtomicLong totalTokensUsed,
            AtomicInteger currentIteration,
            Supplier<Boolean> streamingEnabledSupplier,
            Supplier<java.util.function.Consumer<String>> textDeltaConsumerSupplier,
            @Nullable ExecutionEventEmitter eventEmitter) {
        this.ctx = ctx;
        this.guards = guards;
        this.hookDecisions = hookDecisions;
        this.conversationHistory = conversationHistory;
        this.totalTokensUsed = totalTokensUsed;
        this.currentIteration = currentIteration;
        this.streamingEnabledSupplier = streamingEnabledSupplier;
        this.textDeltaConsumerSupplier =
                textDeltaConsumerSupplier != null ? textDeltaConsumerSupplier : () -> null;
        this.eventEmitter = eventEmitter;
    }

    /**
     * Execute one reasoning cycle: pre-hook → model call → post-hook → response processing.
     *
     * @param modelConfig the model configuration for this iteration
     * @return a signal indicating what the dispatcher should do next
     */
    Mono<IterationSignal> execute(ModelConfig modelConfig) {
        return firePreReasoning(modelConfig)
                .flatMap(hookResult -> handlePreReasoningHookResult(hookResult, modelConfig));
    }

    private Mono<HookResult<PreReasoningEvent>> firePreReasoning(ModelConfig modelConfig) {
        PreReasoningEvent preEvent =
                new PreReasoningEvent(
                        Collections.unmodifiableList(conversationHistory), modelConfig, false);
        return ctx.hookChain().<PreReasoningEvent>firePreReasoningWithResult(preEvent);
    }

    private Mono<IterationSignal> handlePreReasoningHookResult(
            HookResult<PreReasoningEvent> hookResult, ModelConfig baseModelConfig) {
        if (!hookResult.shouldProceed()) {
            log.info(
                    "Agent '{}' reasoning aborted by hook: {}",
                    ctx.agentName(),
                    hookResult.reason());
            String abortReason =
                    hookResult.reason() != null
                            ? hookResult.reason()
                            : "Reasoning aborted by hook.";
            return Mono.just(new IterationSignal.Complete(guards.buildFinalResponse(abortReason)));
        }

        if (hookResult.shouldSkip()) {
            log.info(
                    "Agent '{}' reasoning skipped by hook: {}",
                    ctx.agentName(),
                    hookResult.reason());
            return Mono.just(new IterationSignal.Skip("pre-reasoning hook vetoed"));
        }

        PreReasoningEvent pre = hookResult.event();
        if (pre.cancelled()) {
            log.info("Agent '{}' reasoning cancelled by hook", ctx.agentName());
            return Mono.just(
                    new IterationSignal.Complete(
                            guards.buildFinalResponse("Processing cancelled by hook.")));
        }

        hookDecisions.applyInjections(hookResult, conversationHistory);

        ModelConfig effectiveConfig =
                hookResult.hasModifiedInput()
                        ? hookDecisions.applyReasoningConfigOverrides(
                                baseModelConfig, hookResult.modifiedInput())
                        : baseModelConfig;

        log.debug(
                "Agent '{}' iteration {}: calling model", ctx.agentName(), currentIteration.get());

        // Emit MODEL_CALL_REQUEST before the model call (best-effort)
        Mono<Void> preEmit =
                emitBestEffort(
                        ExecutionEventType.MODEL_CALL_REQUEST,
                        "{\"messageCount\":" + conversationHistory.size() + "}");

        // PRE_MODEL guardrail evaluation
        Mono<ModelResponse> responseMono =
                evaluatePreModelGuardrail(conversationHistory, effectiveConfig)
                        .flatMap(
                                preDecision -> {
                                    if (preDecision.action() == GuardrailDecision.Action.DENY) {
                                        return Mono.<ModelResponse>error(
                                                new GuardrailDenyException(
                                                        "Guardrail denied model call: "
                                                                + preDecision.reason()));
                                    }
                                    // Apply MODIFY if present
                                    List<Msg> effectiveMessages = conversationHistory;
                                    ModelConfig guardedConfig = effectiveConfig;
                                    if (preDecision.action() == GuardrailDecision.Action.MODIFY
                                            && preDecision.modifiedPayload()
                                                    instanceof
                                                    GuardrailPayload.ModelInput modified) {
                                        effectiveMessages = modified.messages();
                                        guardedConfig = modified.config();
                                    }
                                    List<Msg> finalMessages = effectiveMessages;
                                    ModelConfig finalConfig = guardedConfig;
                                    if (streamingEnabledSupplier.get()) {
                                        return callModelStreamingWithFallback(
                                                finalMessages, finalConfig);
                                    }
                                    return guards.withCancellationSignal(
                                            ctx.errorRecovery()
                                                    .callModelWithRecovery(
                                                            finalMessages, finalConfig, 0));
                                });

        return guards.withCooperativeCancellation(preEmit.then(responseMono))
                .flatMap(response -> evaluatePostModelGuardrail(response))
                .doOnError(
                        GuardrailDenyException.class,
                        e -> log.warn("Guardrail denied: {}", e.getMessage()))
                .onErrorResume(GuardrailDenyException.class, e -> Mono.<ModelResponse>empty())
                .flatMap(
                        response -> {
                            // Emit MODEL_CALL_RESPONSE after model response (best-effort)
                            Mono<Void> postEmit =
                                    emitBestEffort(
                                            ExecutionEventType.MODEL_CALL_RESPONSE,
                                            "{\"model\":\""
                                                    + (response.model() != null
                                                            ? response.model()
                                                            : "unknown")
                                                    + "\"}");
                            return postEmit.then(handleModelResponseWithPostHook(response));
                        })
                .switchIfEmpty(
                        Mono.defer(
                                () -> {
                                    // PRE_MODEL DENY case — return error message
                                    return Mono.just(
                                            (IterationSignal)
                                                    new IterationSignal.Complete(
                                                            guards.buildFinalResponse(
                                                                    "Model call blocked by guardrail policy.")));
                                }));
    }

    /** Update budgets, run post-reasoning hook, then process tool/final response flow. */
    private Mono<IterationSignal> handleModelResponseWithPostHook(ModelResponse response) {
        ctx.errorRecovery().fallbackManager().reset();
        applyTokenAccounting(response);
        ctx.tokenBudgetManager().advanceTurn();

        return firePostReasoning(response);
    }

    private void applyTokenAccounting(ModelResponse response) {
        if (response.usage() != null) {
            ctx.tokenBudgetManager().recordModelUsage(response.usage());
            totalTokensUsed.set(ctx.tokenBudgetManager().totalAccountedTokens());

            log.info(
                    "[Iteration {}] input={} output={} cache_read={} cache_write={}",
                    currentIteration.get(),
                    response.usage().inputTokens(),
                    response.usage().outputTokens(),
                    response.usage().cacheReadTokens(),
                    response.usage().cacheCreationTokens());
        } else {
            log.warn("Model response missing usage data — token tracking may be inaccurate");
        }
    }

    private Mono<IterationSignal> firePostReasoning(ModelResponse response) {
        return ctx.hookChain()
                .<PostReasoningEvent>firePostReasoningWithResult(
                        new PostReasoningEvent(response, false))
                .flatMap(postResult -> handlePostReasoningHookResult(postResult, response));
    }

    private Mono<IterationSignal> handlePostReasoningHookResult(
            HookResult<PostReasoningEvent> postResult, ModelResponse response) {
        if (!postResult.shouldProceed()) {
            log.info(
                    "Agent '{}' post-reasoning aborted by hook: {}",
                    ctx.agentName(),
                    postResult.reason());
            String reason =
                    postResult.reason() != null
                            ? postResult.reason()
                            : "Post-reasoning aborted by hook.";
            return Mono.just(new IterationSignal.Complete(guards.buildFinalResponse(reason)));
        }

        if (postResult.hasInjectedContext()) {
            conversationHistory.add(
                    hookDecisions.systemHookContextMessage(postResult.injectedContext()));
        }
        return processModelResponse(response);
    }

    /** Process the model response: convert to Msg, check for tool calls, execute if needed. */
    private Mono<IterationSignal> processModelResponse(ModelResponse response) {
        // Capture stop reason for continuation strategy evaluation
        this.lastStopReason = response.stopReason();
        Msg assistantMsg = appendAssistantResponse(response);
        List<Content.ToolUseContent> toolCalls = extractToolCalls(response);
        return routeModelResponseByToolCalls(assistantMsg, toolCalls);
    }

    private Msg appendAssistantResponse(ModelResponse response) {
        Msg assistantMsg = convertResponseToMsg(response);
        conversationHistory.add(assistantMsg);
        return assistantMsg;
    }

    /** Route model response based on tool calls, with event emission. */
    private Mono<IterationSignal> routeModelResponseByToolCalls(
            Msg assistantMsg, List<Content.ToolUseContent> toolCalls) {
        boolean hasText =
                assistantMsg.contents().stream().anyMatch(c -> c instanceof Content.TextContent);
        boolean preExec = !toolCalls.isEmpty() && isStreamingPreExecuted(toolCalls);
        String branch =
                toolCalls.isEmpty() ? "final" : (preExec ? "streaming-pre-exec" : "tool-execute");
        // INFO-level so a single grep on iter/branch reveals exactly why the loop took (or
        // didn't take) the next step. Previously the only signal was "iteration completed N
        // tokens" — useless for diagnosing premature termination at iter 11.
        log.info(
                "react.continuation agent={} iter={} hasText={} toolCount={} branch={}",
                ctx.agentName(),
                currentIteration.get(),
                hasText,
                toolCalls.size(),
                branch);

        if (toolCalls.isEmpty()) {
            // Consult continuation strategy before terminating
            AgentContinuationStrategy strategy = ctx.continuationStrategy();
            if (strategy != null && !(strategy instanceof NoopContinuationStrategy)) {
                return strategy.decide(buildContinuationContext(assistantMsg))
                        .flatMap(
                                decision ->
                                        applyContinuationDecision(
                                                strategy, decision, assistantMsg));
            }
            // No strategy or Noop — fall through to normal termination
            log.debug(
                    "Agent '{}' final answer candidate \u2014 firing PRE_COMPLETE hooks",
                    ctx.agentName());
            return firePreComplete(assistantMsg);
        }

        // Emit TOOL_CALL_REQUEST for each tool call (best-effort)
        Mono<Void> toolEmissions = Mono.empty();
        if (eventEmitter != null) {
            for (Content.ToolUseContent tc : toolCalls) {
                toolEmissions =
                        toolEmissions.then(
                                emitBestEffort(
                                        ExecutionEventType.TOOL_CALL_REQUEST,
                                        "{\"toolCallId\":\""
                                                + tc.toolId()
                                                + "\",\"toolName\":\""
                                                + tc.toolName()
                                                + "\"}"));
            }
        }

        if (preExec) {
            return toolEmissions.then(handleStreamingPreExecutedTools(toolCalls));
        }
        return toolEmissions.then(
                Mono.just((IterationSignal) new IterationSignal.ToolCallsRequested(toolCalls)));
    }

    /**
     * Fire PRE_COMPLETE hooks. If any hook returns INJECT, inject the message and continue the
     * loop. Otherwise return the final assistant message — analogous to claude-code
     * preventContinuation.
     */
    private Mono<IterationSignal> firePreComplete(Msg assistantMsg) {
        PreCompleteEvent event =
                new PreCompleteEvent(
                        assistantMsg, Collections.unmodifiableList(conversationHistory), false);
        return ctx.hookChain()
                .<PreCompleteEvent>firePreCompleteWithResult(event)
                .flatMap(
                        result -> {
                            if (result.decision() == HookResult.Decision.INJECT) {
                                log.debug(
                                        "Agent '{}' PRE_COMPLETE hook injected message — continuing"
                                                + " loop (source: {})",
                                        ctx.agentName(),
                                        result.hookSource());
                                hookDecisions.applyInjections(result, conversationHistory);
                                // Build the injected message from the last added to history
                                Msg injectedMsg =
                                        conversationHistory.get(conversationHistory.size() - 1);
                                return Mono.just(
                                        (IterationSignal)
                                                new IterationSignal.ContinueWithNudge(
                                                        injectedMsg, "PRE_COMPLETE hook injected"));
                            }
                            log.debug(
                                    "Agent '{}' PRE_COMPLETE hooks passed — returning final answer",
                                    ctx.agentName());
                            return Mono.just(
                                    (IterationSignal) new IterationSignal.Complete(assistantMsg));
                        });
    }

    private boolean isStreamingPreExecuted(List<Content.ToolUseContent> toolCalls) {
        return toolCalls.stream()
                .allMatch(tc -> tc.input() != null && tc.input().containsKey("_streaming_result"));
    }

    // ---- Continuation Strategy helpers ----

    private ContinuationContext buildContinuationContext(Msg assistantMsg) {
        float pressure =
                ctx.tokenBudgetManager() != null ? ctx.tokenBudgetManager().pressure() : 0f;

        int toolCallsInLastK = countRecentToolCalls(3);

        List<Msg> history = Collections.unmodifiableList(conversationHistory);

        Map<String, Object> extensionData = new HashMap<>();

        return new ContinuationContext(
                ctx.agentName(),
                currentIteration.get(),
                ctx.config().maxIterations(),
                history,
                assistantMsg,
                lastStopReason,
                pressure,
                nudgeCount.get(),
                false, // isPlanMode — reserved for future use
                toolCallsInLastK,
                extensionData);
    }

    private Mono<IterationSignal> applyContinuationDecision(
            AgentContinuationStrategy strategy, ContinuationDecision decision, Msg assistantMsg) {

        String reason;
        if (decision instanceof ContinuationDecision.Terminate t) {
            reason = t.reason();
        } else if (decision instanceof ContinuationDecision.Nudge n) {
            reason = n.reason();
        } else if (decision instanceof ContinuationDecision.CompactAndRetry c) {
            reason = c.reason();
        } else if (decision instanceof ContinuationDecision.Escalate e) {
            reason = e.cause() != null ? e.cause().getMessage() : "escalated";
        } else {
            reason = "pass";
        }

        log.info(
                "CONTINUATION_DECISION strategy={} decision={} reason={} iter={} nudges={}",
                strategy.name(),
                decision.getClass().getSimpleName(),
                reason,
                currentIteration.get(),
                nudgeCount.get());

        if (decision instanceof ContinuationDecision.Terminate t) {
            log.debug("Agent '{}' continuation terminated: {}", ctx.agentName(), t.reason());
            return firePreComplete(assistantMsg);
        } else if (decision instanceof ContinuationDecision.Nudge n) {
            nudgeCount.incrementAndGet();
            conversationHistory.add(n.syntheticUserMessage());
            log.info(
                    "Agent '{}' continuation nudge #{}: {}",
                    ctx.agentName(),
                    nudgeCount.get(),
                    n.reason());
            return Mono.just(
                    new IterationSignal.ContinueWithNudge(n.syntheticUserMessage(), n.reason()));
        } else if (decision instanceof ContinuationDecision.CompactAndRetry c) {
            log.info("Agent '{}' continuation compact-and-retry: {}", ctx.agentName(), c.reason());
            return Mono.just(new IterationSignal.CompactThenContinue(c.reason()));
        } else if (decision instanceof ContinuationDecision.Escalate e) {
            Throwable cause =
                    e.cause() != null ? e.cause() : new IllegalStateException("escalated");
            log.error("Agent '{}' continuation escalated", ctx.agentName(), cause);
            return Mono.just(new IterationSignal.Abort(cause, "escalated"));
        } else {
            // Pass means no opinion — fall through to normal termination
            return firePreComplete(assistantMsg);
        }
    }

    private int countRecentToolCalls(int lookbackIterations) {
        int count = 0;
        int assistantMsgsChecked = 0;
        for (int i = conversationHistory.size() - 1;
                i >= 0 && assistantMsgsChecked < lookbackIterations;
                i--) {
            Msg msg = conversationHistory.get(i);
            if (msg.role() == MsgRole.ASSISTANT) {
                assistantMsgsChecked++;
                count +=
                        (int)
                                msg.contents().stream()
                                        .filter(c -> c instanceof Content.ToolUseContent)
                                        .count();
            }
        }
        return count;
    }

    private Mono<IterationSignal> handleStreamingPreExecutedTools(
            List<Content.ToolUseContent> toolCalls) {
        log.debug(
                "Streaming path already executed {} tool(s), using pre-computed results",
                toolCalls.size());
        List<ToolResult> preResults =
                toolCalls.stream()
                        .map(
                                tc ->
                                        ToolResult.success(
                                                tc.toolId(),
                                                (String) tc.input().get("_streaming_result")))
                        .toList();
        conversationHistory.add(hookDecisions.buildToolResultMsg(preResults, conversationHistory));
        return Mono.just(new IterationSignal.ContinueAfterTools(toolCalls.size()));
    }

    // ---- Model response conversion ----

    /** Convert a {@link ModelResponse} into an assistant {@link Msg}. */
    private Msg convertResponseToMsg(ModelResponse response) {
        Msg.Builder builder = Msg.builder().role(MsgRole.ASSISTANT).sourceAgentId(ctx.agentId());
        for (Content content : response.contents()) {
            builder.addContent(content);
        }
        return builder.build();
    }

    /** Extract {@link Content.ToolUseContent} blocks from a model response. */
    private List<Content.ToolUseContent> extractToolCalls(ModelResponse response) {
        if (response == null || response.contents() == null) {
            return List.of();
        }
        return response.contents().stream()
                .filter(Content.ToolUseContent.class::isInstance)
                .map(Content.ToolUseContent.class::cast)
                .toList();
    }

    // ---- Streaming Execution ----

    /**
     * Call the model in streaming mode, detecting and eagerly executing tool calls.
     *
     * <p>Falls back to the non-streaming path if the provider does not support raw streaming.
     */
    private Mono<ModelResponse> callModelStreamingWithFallback(
            List<Msg> messages, ModelConfig modelConfig) {
        var provider = ctx.config().modelProvider();

        if (ctx.toolExecutor() == null) {
            log.debug("Streaming eager tool execution disabled: no ToolExecutor configured");
            return fallbackModelCall(messages, modelConfig);
        }

        if (!(provider instanceof RawStreamingModelProvider rawProvider)) {
            log.debug("Provider '{}' does not support streamRaw, falling back", provider.name());
            return fallbackModelCall(messages, modelConfig);
        }
        Flux<StreamChunk> rawStream =
                guards.withCooperativeCancellation(
                        guards.withCancellationSignal(
                                rawProvider.streamRaw(messages, modelConfig)));

        // Tap TEXT chunks to fire per-token output consumer (if registered).
        // Accumulated text is also used to build a ModelResponse for text-only responses,
        // avoiding a second fallback model call.
        // Tap THINKING chunks via Reactor Context so transports can stream reasoning_content
        // deltas to the UI without changing AgentBuilder/AgentService signatures.
        var textAccumulator = new StringBuilder();
        java.util.function.Consumer<String> deltaConsumer = textDeltaConsumerSupplier.get();
        Flux<StreamChunk> tappedStream =
                rawStream.transformDeferredContextual(
                        (flux, ctxView) -> {
                            @SuppressWarnings("unchecked")
                            java.util.function.Consumer<String> thinkingConsumer =
                                    (java.util.function.Consumer<String>)
                                            ctxView.getOrDefault(THINKING_DELTA_KEY, null);
                            return flux.doOnNext(
                                    chunk -> {
                                        if (chunk.type() == io.kairo.api.model.StreamChunkType.TEXT
                                                && chunk.content() != null) {
                                            textAccumulator.append(chunk.content());
                                            if (deltaConsumer != null) {
                                                deltaConsumer.accept(chunk.content());
                                            }
                                        } else if (chunk.type()
                                                        == io.kairo.api.model.StreamChunkType
                                                                .THINKING
                                                && chunk.content() != null
                                                && thinkingConsumer != null) {
                                            try {
                                                thinkingConsumer.accept(chunk.content());
                                            } catch (Exception ignored) {
                                                // Never let a UI sink failure abort the reasoning
                                                // stream.
                                            }
                                        }
                                    });
                        });

        // Detect tool calls incrementally
        var detector = new StreamingToolDetector();
        Flux<DetectedToolCall> detectedTools = detector.detect(tappedStream);

        // If the tool executor supports streaming dispatch
        if (!ctx.toolExecutor().supportsStreaming()) {
            return fallbackModelCall(messages, modelConfig);
        }
        return executeStreamingTools(messages, modelConfig, detectedTools, textAccumulator);
    }

    private Mono<ModelResponse> fallbackModelCall(List<Msg> messages, ModelConfig modelConfig) {
        return guards.withCancellationSignal(
                ctx.errorRecovery().callModelWithRecovery(messages, modelConfig, 0));
    }

    private Mono<ModelResponse> executeStreamingTools(
            List<Msg> messages,
            ModelConfig modelConfig,
            Flux<DetectedToolCall> detectedTools,
            StringBuilder textAccumulator) {
        var streamingExecutor = new StreamingToolExecutor(ctx.toolExecutor());
        // Track tool call ID → tool name AND original args for building the synthetic response.
        // Preserving args lets downstream consumers (UI bridge hook) display the actual tool
        // input rather than only the synthetic `_streaming_result` marker.
        var toolNameMap = new java.util.concurrent.ConcurrentHashMap<String, String>();
        var toolArgsMap = new java.util.concurrent.ConcurrentHashMap<String, Map<String, Object>>();
        Flux<DetectedToolCall> trackedTools =
                detectedTools.doOnNext(
                        tool -> {
                            if (tool.toolName() != null) {
                                toolNameMap.put(tool.toolCallId(), tool.toolName());
                            }
                            if (tool.args() != null) {
                                toolArgsMap.put(tool.toolCallId(), tool.args());
                            }
                        });
        return streamingExecutor
                .executeEager(trackedTools)
                .collectList()
                .flatMap(
                        toolResults -> {
                            int inputTokensEstimate = estimateInputTokens(messages);
                            if (toolResults.isEmpty()) {
                                // Text-only response: build from accumulated stream text.
                                // Avoids a redundant fallback model call; per-token output
                                // was already fired via textDeltaConsumer during streaming.
                                String accumulated = textAccumulator.toString();
                                if (!accumulated.isEmpty()) {
                                    log.debug(
                                            "Streaming text-only response ({} chars)",
                                            accumulated.length());
                                    return Mono.just(
                                            buildTextOnlyResponse(
                                                    accumulated,
                                                    modelConfig.model(),
                                                    inputTokensEstimate));
                                }
                                // Empty stream — fall back (rare edge case)
                                return fallbackModelCall(messages, modelConfig);
                            }
                            log.debug(
                                    "Streaming execution completed {} tool results",
                                    toolResults.size());
                            return Mono.just(
                                    buildSyntheticStreamingResponse(
                                            toolResults,
                                            toolNameMap,
                                            toolArgsMap,
                                            modelConfig.model(),
                                            inputTokensEstimate));
                        })
                .transform(guards::withCooperativeCancellation);
    }

    private ModelResponse buildTextOnlyResponse(String text, String modelName, int inputTokens) {
        // Provider streaming chunks don't carry usage metadata, so estimate via char-count
        // heuristic (~4 chars/token, the well-known cl100k approximation). Real values would
        // be ideal, but a non-zero estimate is strictly better than the previous Usage(0,0,0,0)
        // which broke every downstream budget/compaction calculation.
        int outputTokens = estimateTextTokens(text);
        return new ModelResponse(
                "streaming",
                List.of(new Content.TextContent(text)),
                new ModelResponse.Usage(inputTokens, outputTokens, 0, 0),
                ModelResponse.StopReason.END_TURN,
                modelName);
    }

    private ModelResponse buildSyntheticStreamingResponse(
            List<ToolResult> toolResults,
            Map<String, String> toolNameMap,
            Map<String, Map<String, Object>> toolArgsMap,
            String modelName,
            int inputTokens) {
        var contents = new ArrayList<Content>();
        int outputCharCount = 0;
        for (var tr : toolResults) {
            String toolName = toolNameMap.getOrDefault(tr.toolUseId(), tr.toolUseId());
            // Merge the original tool args with the streaming-result marker so the UI bridge
            // can display the real input (path, command, …) while the agent loop still
            // recognizes the response as pre-executed via `isStreamingPreExecuted`.
            Map<String, Object> originalArgs = toolArgsMap.getOrDefault(tr.toolUseId(), Map.of());
            var mergedInput = new java.util.LinkedHashMap<String, Object>(originalArgs);
            mergedInput.put("_streaming_result", tr.content());
            contents.add(new Content.ToolUseContent(tr.toolUseId(), toolName, mergedInput));
            outputCharCount += toolName.length();
            if (originalArgs != null) {
                outputCharCount += originalArgs.toString().length();
            }
        }
        int outputTokens = Math.max(1, outputCharCount / 4);
        return new ModelResponse(
                "streaming",
                contents,
                new ModelResponse.Usage(inputTokens, outputTokens, 0, 0),
                ModelResponse.StopReason.TOOL_USE,
                modelName);
    }

    /** Char-count → token estimate (~4 chars/token). Returns at least 1 for non-empty text. */
    private static int estimateTextTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }

    /** Walk the input message list and approximate total prompt tokens via char-count. */
    private static int estimateInputTokens(List<Msg> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int totalChars = 0;
        for (Msg m : messages) {
            if (m == null || m.contents() == null) continue;
            for (Content c : m.contents()) {
                if (c instanceof Content.TextContent t && t.text() != null) {
                    totalChars += t.text().length();
                } else if (c instanceof Content.ToolUseContent tu) {
                    if (tu.toolName() != null) totalChars += tu.toolName().length();
                    if (tu.input() != null) totalChars += tu.input().toString().length();
                } else if (c instanceof Content.ToolResultContent tr && tr.content() != null) {
                    totalChars += tr.content().length();
                }
            }
        }
        return Math.max(0, totalChars / 4);
    }

    // ---- Guardrail helpers ----

    private Mono<GuardrailDecision> evaluatePreModelGuardrail(
            List<Msg> messages, ModelConfig config) {
        GuardrailChain chain = ctx.guardrailChain();
        if (chain == null) {
            if (preGuardrailWarnedOnce.compareAndSet(false, true)) {
                log.debug(
                        "GuardrailChain is null — PRE_MODEL guardrail evaluation skipped (suppressed for the rest of this session)");
            }
            return Mono.just(GuardrailDecision.allow("no-guardrail"));
        }
        return Mono.defer(
                () ->
                        chain.evaluate(
                                new GuardrailContext(
                                        GuardrailPhase.PRE_MODEL,
                                        ctx.agentName(),
                                        config.model(),
                                        new GuardrailPayload.ModelInput(messages, config),
                                        Map.of())));
    }

    private Mono<ModelResponse> evaluatePostModelGuardrail(ModelResponse response) {
        GuardrailChain chain = ctx.guardrailChain();
        if (chain == null) {
            if (postGuardrailWarnedOnce.compareAndSet(false, true)) {
                log.debug(
                        "GuardrailChain is null — POST_MODEL guardrail evaluation skipped (suppressed for the rest of this session)");
            }
            return Mono.just(response);
        }
        String targetName = response.model() != null ? response.model() : "model";
        return Mono.defer(
                        () ->
                                chain.evaluate(
                                        new GuardrailContext(
                                                GuardrailPhase.POST_MODEL,
                                                ctx.agentName(),
                                                targetName,
                                                new GuardrailPayload.ModelOutput(response),
                                                Map.of())))
                .flatMap(
                        decision -> {
                            if (decision.action() == GuardrailDecision.Action.DENY) {
                                return Mono.error(
                                        new GuardrailDenyException(
                                                "Guardrail denied model response: "
                                                        + decision.reason()));
                            }
                            if (decision.action() == GuardrailDecision.Action.MODIFY
                                    && decision.modifiedPayload()
                                            instanceof GuardrailPayload.ModelOutput modified) {
                                return Mono.just(modified.response());
                            }
                            return Mono.just(response);
                        });
    }

    /** Internal exception for guardrail DENY flow control. */
    private static class GuardrailDenyException extends RuntimeException {
        GuardrailDenyException(String message) {
            super(message);
        }
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
                            log.warn("Failed to emit {} event: {}", type, e.getMessage());
                            return Mono.empty();
                        });
    }
}
