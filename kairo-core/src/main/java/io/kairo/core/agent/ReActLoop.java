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

import io.kairo.api.agent.CancellationSignal;
import io.kairo.api.exception.AgentInterruptedException;
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
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The core ReAct (Reasoning + Acting) iteration loop, extracted from {@link DefaultReActAgent}.
 *
 * <p>This class <b>owns</b> the conversation history — only it may {@code add()} to the list.
 * External collaborators use {@link #injectMessages} or {@link #replaceHistory} for controlled
 * mutations.
 *
 * <p>Package-private: not part of the public API.
 */
class ReActLoop {

    private static final Logger log = LoggerFactory.getLogger(ReActLoop.class);

    private final ReActLoopContext ctx;
    private final List<Msg> conversationHistory;
    private final AtomicBoolean interrupted;
    private final AtomicInteger currentIteration;
    private final AtomicLong totalTokensUsed;
    private final Supplier<ModelConfig> modelConfigSupplier;
    private volatile boolean streamingEnabled = false;
    private final AtomicBoolean danglingRecoveryDone = new AtomicBoolean(false);
    private CompactionTrigger compactionTrigger; // set after construction
    private final LoopDetector loopDetector;

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
        this.ctx = ctx;
        this.conversationHistory = new CopyOnWriteArrayList<>();
        this.interrupted = interrupted;
        this.currentIteration = currentIteration;
        this.totalTokensUsed = totalTokensUsed;
        this.modelConfigSupplier = modelConfigSupplier;
        // Initialize loop detector from config thresholds
        this.loopDetector =
                new LoopDetector(
                        ctx.config().loopHashWarnThreshold(),
                        ctx.config().loopHashHardLimit(),
                        ctx.config().loopFreqWarnThreshold(),
                        ctx.config().loopFreqHardLimit(),
                        ctx.config().loopFreqWindow());
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
        Msg guardResult = evaluateLoopGuards();
        if (guardResult != null) {
            return Mono.just(guardResult);
        }
        return executeReasoningIteration(modelConfigSupplier.get());
    }

    /** Evaluate guard conditions before each iteration and optionally return a final response. */
    private Msg evaluateLoopGuards() {
        if (interrupted.get()) {
            throw new AgentInterruptedException("Agent '" + ctx.agentName() + "' was interrupted");
        }

        if (!ctx.shutdownManager().isAcceptingRequests()) {
            log.info("Agent '{}' stopping due to system shutdown", ctx.agentName());
            return buildFinalResponse("Agent stopped due to system shutdown.");
        }

        if (currentIteration.get() >= ctx.config().maxIterations()) {
            log.warn(
                    "Agent '{}' reached max iterations ({})",
                    ctx.agentName(),
                    ctx.config().maxIterations());
            return buildFinalResponse(
                    "I've reached my maximum iteration limit. Here is what I have so far.");
        }

        long accountedTokens = ctx.tokenBudgetManager().totalAccountedTokens();
        if (accountedTokens >= ctx.config().tokenBudget()) {
            log.warn(
                    "Agent '{}' exceeded token budget ({}/{})",
                    ctx.agentName(),
                    accountedTokens,
                    ctx.config().tokenBudget());
            return buildFinalResponse("I've reached my token budget. Here is what I have so far.");
        }

        return null;
    }

    /** Execute one reasoning cycle: pre-hook -> model call -> post-hook -> response processing. */
    private Mono<Msg> executeReasoningIteration(ModelConfig modelConfig) {
        return firePreReasoning(modelConfig)
                .flatMap(hookResult -> handlePreReasoningHookResult(hookResult, modelConfig));
    }

    private Mono<HookResult<PreReasoningEvent>> firePreReasoning(ModelConfig modelConfig) {
        PreReasoningEvent preEvent =
                new PreReasoningEvent(
                        Collections.unmodifiableList(conversationHistory), modelConfig, false);
        return ctx.hookChain().<PreReasoningEvent>firePreReasoningWithResult(preEvent);
    }

    private Mono<Msg> handlePreReasoningHookResult(
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
            return Mono.just(buildFinalResponse(abortReason));
        }

        if (hookResult.shouldSkip()) {
            log.info(
                    "Agent '{}' reasoning skipped by hook: {}",
                    ctx.agentName(),
                    hookResult.reason());
            currentIteration.incrementAndGet();
            return runLoop();
        }

        PreReasoningEvent pre = hookResult.event();
        if (pre.cancelled()) {
            log.info("Agent '{}' reasoning cancelled by hook", ctx.agentName());
            return Mono.just(buildFinalResponse("Processing cancelled by hook."));
        }

        applyPreReasoningInjections(hookResult);

        ModelConfig effectiveConfig =
                hookResult.hasModifiedInput()
                        ? applyReasoningConfigOverrides(baseModelConfig, hookResult.modifiedInput())
                        : baseModelConfig;

        log.debug(
                "Agent '{}' iteration {}: calling model", ctx.agentName(), currentIteration.get());
        Mono<ModelResponse> responseMono =
                streamingEnabled
                        ? callModelStreamingWithFallback(conversationHistory, effectiveConfig)
                        : withCancellationSignal(
                                ctx.errorRecovery()
                                        .callModelWithRecovery(
                                                conversationHistory, effectiveConfig, 0));

        return withCooperativeCancellation(responseMono)
                .flatMap(this::handleModelResponseWithPostHook);
    }

    private void applyPreReasoningInjections(HookResult<PreReasoningEvent> hookResult) {
        if (hookResult.hasInjectedMessage()) {
            Msg injected =
                    Msg.builder()
                            .role(hookResult.injectedMessage().role())
                            .contents(hookResult.injectedMessage().contents())
                            .metadata("hook_source", hookResult.hookSource())
                            .metadata("hook_decision", "INJECT")
                            .verbatimPreserved(true)
                            .build();
            conversationHistory.add(injected);
        }
        if (hookResult.hasInjectedContext()) {
            conversationHistory.add(systemHookContextMessage(hookResult.injectedContext()));
        }
    }

    /** Apply hook-provided override fields onto the base model config. */
    private ModelConfig applyReasoningConfigOverrides(
            ModelConfig modelConfig, Map<String, Object> overrides) {
        ModelConfig.Builder cfgBuilder =
                ModelConfig.builder()
                        .model(modelConfig.model())
                        .maxTokens(modelConfig.maxTokens())
                        .temperature(modelConfig.temperature())
                        .systemPrompt(modelConfig.systemPrompt())
                        .tools(modelConfig.tools());
        if (modelConfig.systemPromptParts() != null) {
            cfgBuilder.systemPromptParts(modelConfig.systemPromptParts());
        }
        if (modelConfig.systemPromptSegments() != null) {
            cfgBuilder.segments(modelConfig.systemPromptSegments());
        }

        Object modelOverride = overrides.get("model");
        if (modelOverride instanceof String m) {
            cfgBuilder.model(m);
        }

        Object maxTokensOverride = overrides.get("maxTokens");
        if (maxTokensOverride instanceof Number n) {
            cfgBuilder.maxTokens(n.intValue());
        }

        Object tempOverride = overrides.get("temperature");
        if (tempOverride instanceof Number n) {
            cfgBuilder.temperature(n.doubleValue());
        }
        return cfgBuilder.build();
    }

    /** Update budgets, run post-reasoning hook, then process tool/final response flow. */
    private Mono<Msg> handleModelResponseWithPostHook(ModelResponse response) {
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

    private Mono<Msg> firePostReasoning(ModelResponse response) {
        return ctx.hookChain()
                .<PostReasoningEvent>firePostReasoningWithResult(
                        new PostReasoningEvent(response, false))
                .flatMap(postResult -> handlePostReasoningHookResult(postResult, response));
    }

    private Mono<Msg> handlePostReasoningHookResult(
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
            return Mono.just(buildFinalResponse(reason));
        }

        if (postResult.hasInjectedContext()) {
            conversationHistory.add(systemHookContextMessage(postResult.injectedContext()));
        }
        return processModelResponse(response);
    }

    /** Process the model response: convert to Msg, check for tool calls, execute if needed. */
    private Mono<Msg> processModelResponse(ModelResponse response) {
        Msg assistantMsg = appendAssistantResponse(response);
        List<Content.ToolUseContent> toolCalls = extractToolCalls(response);
        return routeModelResponseByToolCalls(assistantMsg, toolCalls);
    }

    private Msg appendAssistantResponse(ModelResponse response) {
        Msg assistantMsg = convertResponseToMsg(response);
        conversationHistory.add(assistantMsg);
        return assistantMsg;
    }

    private Mono<Msg> routeModelResponseByToolCalls(
            Msg assistantMsg, List<Content.ToolUseContent> toolCalls) {
        if (toolCalls.isEmpty()) {
            log.debug("Agent '{}' produced final answer (no tool calls)", ctx.agentName());
            return Mono.just(assistantMsg);
        }
        if (isStreamingPreExecuted(toolCalls)) {
            return handleStreamingPreExecutedTools(toolCalls);
        }
        return executeToolCallsWithGuards(toolCalls);
    }

    private boolean isStreamingPreExecuted(List<Content.ToolUseContent> toolCalls) {
        return toolCalls.stream()
                .allMatch(tc -> tc.input() != null && tc.input().containsKey("_streaming_result"));
    }

    private Mono<Msg> handleStreamingPreExecutedTools(List<Content.ToolUseContent> toolCalls) {
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
        conversationHistory.add(buildToolResultMsg(preResults));
        currentIteration.incrementAndGet();
        return runLoop();
    }

    private Mono<Msg> executeToolCallsWithGuards(List<Content.ToolUseContent> toolCalls) {
        log.debug(
                "Agent '{}' requesting {} tool call(s): {}",
                ctx.agentName(),
                toolCalls.size(),
                toolCalls.stream()
                        .map(Content.ToolUseContent::toolName)
                        .collect(Collectors.joining(", ")));

        Mono<Msg> loopDecision = evaluateLoopDetection(toolCalls);
        if (loopDecision != null) {
            return loopDecision;
        }

        return runToolExecutionPipeline(toolCalls);
    }

    private Mono<Msg> runToolExecutionPipeline(List<Content.ToolUseContent> toolCalls) {
        return checkCancelled()
                .then(executeToolsWithHooks(toolCalls))
                .flatMap(toolResults -> continueAfterToolExecution(toolCalls, toolResults));
    }

    private Mono<Msg> evaluateLoopDetection(List<Content.ToolUseContent> toolCalls) {
        var detection = loopDetector.check(toolCalls);
        if (detection.level() == LoopDetector.DetectionResult.Level.HARD_STOP) {
            return Mono.error(new LoopDetectionException(detection.message()));
        }
        if (detection.level() == LoopDetector.DetectionResult.Level.WARN) {
            conversationHistory.add(Msg.of(MsgRole.USER, "[Loop Warning] " + detection.message()));
            return runLoop();
        }
        return null;
    }

    private Mono<Msg> continueAfterToolExecution(
            List<Content.ToolUseContent> toolCalls, List<ToolResult> toolResults) {
        applySkillToolRestrictions(toolCalls, toolResults);
        conversationHistory.add(buildToolResultMsg(toolResults));
        currentIteration.incrementAndGet();

        return proceedWithCompactionIfNeeded();
    }

    private Mono<Msg> proceedWithCompactionIfNeeded() {
        if (compactionTrigger != null) {
            return checkCancelled()
                    .then(compactionTrigger.checkAndCompact(conversationHistory))
                    .flatMap(compacted -> runLoop());
        }
        return checkCancelled().then(runLoop());
    }

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
                .concatMap(toolCall -> checkCancelled().then(executeSingleToolWithHooks(toolCall)))
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
                                finishToolExecutionPipeline(
                                        toolCall.toolName(), result, toolStart));
    }

    private Mono<HookResult<PreActingEvent>> firePreActing(Content.ToolUseContent toolCall) {
        return checkCancelled()
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

        applyPreActingInjections(hookResult);

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
                Map.of());
    }

    private ToolResult skippedByHookToolResult(String toolUseId) {
        return new ToolResult(
                toolUseId,
                "Tool execution skipped by hook",
                false,
                Map.of("skipped_by_hook", true));
    }

    private ToolResult cancelledByHookToolResult(String toolUseId) {
        return new ToolResult(toolUseId, "Tool execution cancelled by hook", true, Map.of());
    }

    private void applyPreActingInjections(HookResult<PreActingEvent> hookResult) {
        if (hookResult.hasInjectedMessage()) {
            Msg injected =
                    Msg.builder()
                            .role(hookResult.injectedMessage().role())
                            .contents(hookResult.injectedMessage().contents())
                            .metadata("hook_source", hookResult.hookSource())
                            .metadata("hook_decision", "INJECT")
                            .verbatimPreserved(true)
                            .build();
            conversationHistory.add(injected);
        }
        if (hookResult.hasInjectedContext()) {
            conversationHistory.add(systemHookContextMessage(hookResult.injectedContext()));
        }
    }

    private Mono<ToolResult> executeToolCall(
            String toolUseId, String toolName, Map<String, Object> input) {
        return checkCancelled()
                .then(
                        withCancellationSignal(
                                Mono.defer(() -> ctx.toolExecutor().execute(toolName, input))))
                .transform(this::withCooperativeCancellation)
                .map(
                        result ->
                                new ToolResult(
                                        toolUseId,
                                        result.content(),
                                        result.isError(),
                                        result.metadata()))
                .onErrorResume(
                        e -> {
                            log.error("Tool '{}' execution failed: {}", toolName, e.getMessage());
                            return Mono.just(
                                    new ToolResult(
                                            toolUseId,
                                            "Error executing tool: " + e.getMessage(),
                                            true,
                                            Map.of()));
                        });
    }

    private Mono<ToolResult> firePostActingAndReturn(String toolName, ToolResult result) {
        PostActingEvent postEvent = new PostActingEvent(toolName, result);
        return checkCancelled()
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
                                                                        systemHookContextMessage(
                                                                                postResult
                                                                                        .injectedContext()));
                                                            }
                                                            return result;
                                                        })));
    }

    private Msg systemHookContextMessage(String injectedContext) {
        return Msg.builder()
                .role(MsgRole.SYSTEM)
                .addContent(new Content.TextContent("[Hook Context] " + injectedContext))
                .build();
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
                            log.warn(
                                    "OnToolResult hook failed for tool '{}': {}",
                                    toolName,
                                    e.getMessage());
                            return Mono.empty();
                        })
                .thenReturn(toolResult);
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
        Msg toolMsg = buildToolResultMsg(errorResults);
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
                withCancellationSignal(rawProvider.streamRaw(messages, modelConfig));

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
        return withCancellationSignal(
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
                .transform(this::withCooperativeCancellation);
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

    // ---- Conversion helpers ----

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

    /**
     * Build a tool result {@link Msg} from a list of tool results. Each tool result is added as a
     * {@link Content.ToolResultContent} block.
     */
    private Msg buildToolResultMsg(List<ToolResult> results) {
        MsgBuilder builder = MsgBuilder.create().role(MsgRole.TOOL).sourceAgentId(ctx.agentId());

        for (ToolResult result : results) {
            builder.addToolResult(result.toolUseId(), result.content(), result.isError());
        }

        return builder.build();
    }

    /** Build a final text response message. */
    private Msg buildFinalResponse(String text) {
        return MsgBuilder.create()
                .role(MsgRole.ASSISTANT)
                .sourceAgentId(ctx.agentId())
                .text(text)
                .build();
    }

    /**
     * Check if the agent has been interrupted and signal cancellation if so. Inserted at reactive
     * chain boundaries for cooperative cancellation.
     */
    private Mono<Void> checkCancelled() {
        if (interrupted.get()) {
            return Mono.error(
                    new AgentInterruptedException(
                            "Agent '"
                                    + ctx.agentName()
                                    + "' interrupted at iteration "
                                    + currentIteration));
        }
        return Mono.empty();
    }

    private <T> Mono<T> withCancellationSignal(Mono<T> source) {
        return source.contextWrite(
                ctxView ->
                        ctxView.put(
                                CancellationSignal.CONTEXT_KEY,
                                (CancellationSignal) interrupted::get));
    }

    private <T> Flux<T> withCancellationSignal(Flux<T> source) {
        return source.contextWrite(
                ctxView ->
                        ctxView.put(
                                CancellationSignal.CONTEXT_KEY,
                                (CancellationSignal) interrupted::get));
    }

    private <T> Mono<T> withCooperativeCancellation(Mono<T> source) {
        return source.takeUntilOther(cancellationTrigger())
                .switchIfEmpty(
                        Mono.defer(
                                () ->
                                        interrupted.get()
                                                ? Mono.error(
                                                        new AgentInterruptedException(
                                                                "Agent '"
                                                                        + ctx.agentName()
                                                                        + "' interrupted at iteration "
                                                                        + currentIteration))
                                                : Mono.empty()));
    }

    private Mono<Long> cancellationTrigger() {
        if (interrupted.get()) {
            return Mono.just(0L);
        }
        return Flux.interval(Duration.ofMillis(50)).filter(tick -> interrupted.get()).next();
    }

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
