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

import io.kairo.api.guardrail.*;
import io.kairo.api.hook.*;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.model.RawStreamingModelProvider;
import io.kairo.api.model.StreamChunk;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.message.MsgBuilder;
import io.kairo.core.model.DetectedToolCall;
import io.kairo.core.model.StreamingToolDetector;
import io.kairo.core.tool.StreamingToolExecutor;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
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
 * <p>Package-private: not part of the public API.
 */
class ReasoningPhase {

    private static final Logger log = LoggerFactory.getLogger(ReasoningPhase.class);

    private final ReActLoopContext ctx;
    private final IterationGuards guards;
    private final HookDecisionApplier hookDecisions;
    private final List<Msg> conversationHistory;
    private final AtomicLong totalTokensUsed;
    private final AtomicInteger currentIteration;
    private final Supplier<Boolean> streamingEnabledSupplier;
    private final ToolPhase toolPhase;

    ReasoningPhase(
            ReActLoopContext ctx,
            IterationGuards guards,
            HookDecisionApplier hookDecisions,
            List<Msg> conversationHistory,
            AtomicLong totalTokensUsed,
            AtomicInteger currentIteration,
            Supplier<Boolean> streamingEnabledSupplier,
            ToolPhase toolPhase) {
        this.ctx = ctx;
        this.guards = guards;
        this.hookDecisions = hookDecisions;
        this.conversationHistory = conversationHistory;
        this.totalTokensUsed = totalTokensUsed;
        this.currentIteration = currentIteration;
        this.streamingEnabledSupplier = streamingEnabledSupplier;
        this.toolPhase = toolPhase;
    }

    /**
     * Execute one reasoning cycle: pre-hook → model call → post-hook → response processing.
     *
     * @param modelConfig the model configuration for this iteration
     * @param loopContinuation supplier for the next loop iteration (for recursion)
     * @return the final response Msg, or the result of continued looping
     */
    Mono<Msg> execute(ModelConfig modelConfig, Supplier<Mono<Msg>> loopContinuation) {
        return firePreReasoning(modelConfig)
                .flatMap(
                        hookResult ->
                                handlePreReasoningHookResult(
                                        hookResult, modelConfig, loopContinuation));
    }

    private Mono<HookResult<PreReasoningEvent>> firePreReasoning(ModelConfig modelConfig) {
        PreReasoningEvent preEvent =
                new PreReasoningEvent(
                        Collections.unmodifiableList(conversationHistory), modelConfig, false);
        return ctx.hookChain().<PreReasoningEvent>firePreReasoningWithResult(preEvent);
    }

    private Mono<Msg> handlePreReasoningHookResult(
            HookResult<PreReasoningEvent> hookResult,
            ModelConfig baseModelConfig,
            Supplier<Mono<Msg>> loopContinuation) {
        if (!hookResult.shouldProceed()) {
            log.info(
                    "Agent '{}' reasoning aborted by hook: {}",
                    ctx.agentName(),
                    hookResult.reason());
            String abortReason =
                    hookResult.reason() != null
                            ? hookResult.reason()
                            : "Reasoning aborted by hook.";
            return Mono.just(guards.buildFinalResponse(abortReason));
        }

        if (hookResult.shouldSkip()) {
            log.info(
                    "Agent '{}' reasoning skipped by hook: {}",
                    ctx.agentName(),
                    hookResult.reason());
            currentIteration.incrementAndGet();
            return loopContinuation.get();
        }

        PreReasoningEvent pre = hookResult.event();
        if (pre.cancelled()) {
            log.info("Agent '{}' reasoning cancelled by hook", ctx.agentName());
            return Mono.just(guards.buildFinalResponse("Processing cancelled by hook."));
        }

        hookDecisions.applyInjections(hookResult, conversationHistory);

        ModelConfig effectiveConfig =
                hookResult.hasModifiedInput()
                        ? hookDecisions.applyReasoningConfigOverrides(
                                baseModelConfig, hookResult.modifiedInput())
                        : baseModelConfig;

        log.debug(
                "Agent '{}' iteration {}: calling model", ctx.agentName(), currentIteration.get());

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

        return guards.withCooperativeCancellation(responseMono)
                .flatMap(response -> evaluatePostModelGuardrail(response))
                .doOnError(
                        GuardrailDenyException.class,
                        e -> log.warn("Guardrail denied: {}", e.getMessage()))
                .onErrorResume(GuardrailDenyException.class, e -> Mono.<ModelResponse>empty())
                .flatMap(response -> handleModelResponseWithPostHook(response, loopContinuation))
                .switchIfEmpty(
                        Mono.defer(
                                () -> {
                                    // PRE_MODEL DENY case — return error message
                                    return Mono.just(
                                            guards.buildFinalResponse(
                                                    "Model call blocked by guardrail policy."));
                                }));
    }

    /** Update budgets, run post-reasoning hook, then process tool/final response flow. */
    private Mono<Msg> handleModelResponseWithPostHook(
            ModelResponse response, Supplier<Mono<Msg>> loopContinuation) {
        ctx.errorRecovery().fallbackManager().reset();
        applyTokenAccounting(response);
        ctx.tokenBudgetManager().advanceTurn();

        return firePostReasoning(response, loopContinuation);
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

    private Mono<Msg> firePostReasoning(
            ModelResponse response, Supplier<Mono<Msg>> loopContinuation) {
        return ctx.hookChain()
                .<PostReasoningEvent>firePostReasoningWithResult(
                        new PostReasoningEvent(response, false))
                .flatMap(
                        postResult ->
                                handlePostReasoningHookResult(
                                        postResult, response, loopContinuation));
    }

    private Mono<Msg> handlePostReasoningHookResult(
            HookResult<PostReasoningEvent> postResult,
            ModelResponse response,
            Supplier<Mono<Msg>> loopContinuation) {
        if (!postResult.shouldProceed()) {
            log.info(
                    "Agent '{}' post-reasoning aborted by hook: {}",
                    ctx.agentName(),
                    postResult.reason());
            String reason =
                    postResult.reason() != null
                            ? postResult.reason()
                            : "Post-reasoning aborted by hook.";
            return Mono.just(guards.buildFinalResponse(reason));
        }

        if (postResult.hasInjectedContext()) {
            conversationHistory.add(
                    hookDecisions.systemHookContextMessage(postResult.injectedContext()));
        }
        return processModelResponse(response, loopContinuation);
    }

    /** Process the model response: convert to Msg, check for tool calls, execute if needed. */
    private Mono<Msg> processModelResponse(
            ModelResponse response, Supplier<Mono<Msg>> loopContinuation) {
        Msg assistantMsg = appendAssistantResponse(response);
        List<Content.ToolUseContent> toolCalls = extractToolCalls(response);
        return routeModelResponseByToolCalls(assistantMsg, toolCalls, loopContinuation);
    }

    private Msg appendAssistantResponse(ModelResponse response) {
        Msg assistantMsg = convertResponseToMsg(response);
        conversationHistory.add(assistantMsg);
        return assistantMsg;
    }

    private Mono<Msg> routeModelResponseByToolCalls(
            Msg assistantMsg,
            List<Content.ToolUseContent> toolCalls,
            Supplier<Mono<Msg>> loopContinuation) {
        if (toolCalls.isEmpty()) {
            log.debug("Agent '{}' produced final answer (no tool calls)", ctx.agentName());
            return Mono.just(assistantMsg);
        }
        if (isStreamingPreExecuted(toolCalls)) {
            return handleStreamingPreExecutedTools(toolCalls, loopContinuation);
        }
        return toolPhase.executeAndContinue(toolCalls, loopContinuation);
    }

    private boolean isStreamingPreExecuted(List<Content.ToolUseContent> toolCalls) {
        return toolCalls.stream()
                .allMatch(tc -> tc.input() != null && tc.input().containsKey("_streaming_result"));
    }

    private Mono<Msg> handleStreamingPreExecutedTools(
            List<Content.ToolUseContent> toolCalls, Supplier<Mono<Msg>> loopContinuation) {
        log.debug(
                "Streaming path already executed {} tool(s), using pre-computed results",
                toolCalls.size());
        List<ToolResult> preResults =
                toolCalls.stream()
                        .map(
                                tc ->
                                        new ToolResult(
                                                tc.toolId(),
                                                (String) tc.input().get("_streaming_result"),
                                                false,
                                                Map.of()))
                        .toList();
        conversationHistory.add(hookDecisions.buildToolResultMsg(preResults));
        currentIteration.incrementAndGet();
        return loopContinuation.get();
    }

    // ---- Model response conversion ----

    /** Convert a {@link ModelResponse} into an assistant {@link Msg}. */
    private Msg convertResponseToMsg(ModelResponse response) {
        MsgBuilder builder =
                MsgBuilder.create().role(MsgRole.ASSISTANT).sourceAgentId(ctx.agentId());

        for (Content content : response.contents()) {
            if (content instanceof Content.TextContent tc) {
                builder.text(tc.text());
            } else if (content instanceof Content.ToolUseContent tu) {
                builder.toolUse(tu.toolId(), tu.toolName(), tu.input());
            } else if (content instanceof Content.ThinkingContent th) {
                builder.thinking(th.thinking(), th.budgetTokens());
            }
            // ignore other content types in assistant messages
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

        // Detect tool calls incrementally
        var detector = new StreamingToolDetector();
        Flux<DetectedToolCall> detectedTools = detector.detect(rawStream);

        // If the tool executor supports streaming dispatch
        if (!ctx.toolExecutor().supportsStreaming()) {
            return fallbackModelCall(messages, modelConfig);
        }
        return executeStreamingTools(messages, modelConfig, detectedTools);
    }

    private Mono<ModelResponse> fallbackModelCall(List<Msg> messages, ModelConfig modelConfig) {
        return guards.withCancellationSignal(
                ctx.errorRecovery().callModelWithRecovery(messages, modelConfig, 0));
    }

    private Mono<ModelResponse> executeStreamingTools(
            List<Msg> messages, ModelConfig modelConfig, Flux<DetectedToolCall> detectedTools) {
        var streamingExecutor = new StreamingToolExecutor(ctx.toolExecutor());
        // Track tool call ID → tool name mapping for building the synthetic response
        var toolNameMap = new java.util.concurrent.ConcurrentHashMap<String, String>();
        Flux<DetectedToolCall> trackedTools =
                detectedTools.doOnNext(
                        tool -> {
                            if (tool.toolName() != null) {
                                toolNameMap.put(tool.toolCallId(), tool.toolName());
                            }
                        });
        return streamingExecutor
                .executeEager(trackedTools)
                .collectList()
                .flatMap(
                        toolResults -> {
                            if (toolResults.isEmpty()) {
                                // No tools detected — stream was text-only
                                // Fall back to non-streaming to get a proper ModelResponse
                                return fallbackModelCall(messages, modelConfig);
                            }
                            log.debug(
                                    "Streaming execution completed {} tool results",
                                    toolResults.size());
                            return Mono.just(
                                    buildSyntheticStreamingResponse(
                                            toolResults, toolNameMap, modelConfig.model()));
                        })
                .transform(guards::withCooperativeCancellation);
    }

    private ModelResponse buildSyntheticStreamingResponse(
            List<ToolResult> toolResults, Map<String, String> toolNameMap, String modelName) {
        var contents = new ArrayList<Content>();
        for (var tr : toolResults) {
            String toolName = toolNameMap.getOrDefault(tr.toolUseId(), tr.toolUseId());
            contents.add(
                    new Content.ToolUseContent(
                            tr.toolUseId(), toolName, Map.of("_streaming_result", tr.content())));
        }
        return new ModelResponse(
                "streaming",
                contents,
                new ModelResponse.Usage(0, 0, 0, 0),
                ModelResponse.StopReason.TOOL_USE,
                modelName);
    }

    // ---- Guardrail helpers ----

    private Mono<GuardrailDecision> evaluatePreModelGuardrail(
            List<Msg> messages, ModelConfig config) {
        GuardrailChain chain = ctx.guardrailChain();
        if (chain == null) {
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
}
