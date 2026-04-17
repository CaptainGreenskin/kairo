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

import io.kairo.api.hook.*;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelResponse;
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
    private CompactionTrigger compactionTrigger; // set after construction

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
        return Mono.defer(
                () -> {
                    // Check if interrupted
                    if (interrupted.get()) {
                        return Mono.error(
                                new AgentInterruptedException(
                                        "Agent '" + ctx.agentName() + "' was interrupted"));
                    }

                    // Check if system is shutting down
                    if (!ctx.shutdownManager().isAcceptingRequests()) {
                        log.info("Agent '{}' stopping due to system shutdown", ctx.agentName());
                        return Mono.just(
                                buildFinalResponse("Agent stopped due to system shutdown."));
                    }

                    // Check iteration limit
                    if (currentIteration.get() >= ctx.config().maxIterations()) {
                        log.warn(
                                "Agent '{}' reached max iterations ({})",
                                ctx.agentName(),
                                ctx.config().maxIterations());
                        return Mono.just(
                                buildFinalResponse(
                                        "I've reached my maximum iteration limit. Here is what I have so far."));
                    }

                    // Check token budget
                    if (totalTokensUsed.get() >= ctx.config().tokenBudget()) {
                        log.warn(
                                "Agent '{}' exceeded token budget ({}/{})",
                                ctx.agentName(),
                                totalTokensUsed.get(),
                                ctx.config().tokenBudget());
                        return Mono.just(
                                buildFinalResponse(
                                        "I've reached my token budget. Here is what I have so far."));
                    }

                    // 1. Build ModelConfig with system prompt and tool definitions
                    ModelConfig modelConfig = modelConfigSupplier.get();

                    // 2. Fire PreReasoning hook
                    PreReasoningEvent preEvent =
                            new PreReasoningEvent(
                                    Collections.unmodifiableList(conversationHistory),
                                    modelConfig,
                                    false);

                    return ctx.hookChain()
                            .<PreReasoningEvent>firePreReasoningWithResult(preEvent)
                            .flatMap(
                                    hookResult -> {
                                        // Handle ABORT decision
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

                                        // Handle SKIP — skip model call, continue loop
                                        if (hookResult.shouldSkip()) {
                                            log.info(
                                                    "Agent '{}' reasoning skipped by hook: {}",
                                                    ctx.agentName(),
                                                    hookResult.reason());
                                            currentIteration.incrementAndGet();
                                            return runLoop();
                                        }

                                        PreReasoningEvent pre = hookResult.event();

                                        // Handle cancelled event (backward compat)
                                        if (pre.cancelled()) {
                                            log.info(
                                                    "Agent '{}' reasoning cancelled by hook", ctx.agentName());
                                            return Mono.<Msg>just(
                                                    buildFinalResponse(
                                                            "Processing cancelled by hook."));
                                        }

                                        // Handle INJECT — add injected message to history
                                        if (hookResult.hasInjectedMessage()) {
                                            Msg injected =
                                                    Msg.builder()
                                                            .role(
                                                                    hookResult
                                                                            .injectedMessage()
                                                                            .role())
                                                            .contents(
                                                                    hookResult
                                                                            .injectedMessage()
                                                                            .contents())
                                                            .metadata(
                                                                    "hook_source",
                                                                    hookResult.hookSource())
                                                            .metadata("hook_decision", "INJECT")
                                                            .verbatimPreserved(true)
                                                            .build();
                                            conversationHistory.add(injected);
                                        }

                                        // Handle MODIFY decision — apply modifiedInput to
                                        // ModelConfig
                                        ModelConfig effectiveConfig;
                                        if (hookResult.hasModifiedInput()) {
                                            ModelConfig.Builder cfgBuilder =
                                                    ModelConfig.builder()
                                                            .model(modelConfig.model())
                                                            .maxTokens(modelConfig.maxTokens())
                                                            .temperature(modelConfig.temperature())
                                                            .systemPrompt(
                                                                    modelConfig.systemPrompt())
                                                            .tools(modelConfig.tools());
                                            if (modelConfig.systemPromptParts() != null) {
                                                cfgBuilder.systemPromptParts(
                                                        modelConfig.systemPromptParts());
                                            }
                                            if (modelConfig.systemPromptSegments() != null) {
                                                cfgBuilder.segments(
                                                        modelConfig.systemPromptSegments());
                                            }
                                            // Override model name if provided
                                            Object modelOverride =
                                                    hookResult.modifiedInput().get("model");
                                            if (modelOverride instanceof String m) {
                                                cfgBuilder.model(m);
                                            }
                                            // Override maxTokens if provided
                                            Object maxTokensOverride =
                                                    hookResult.modifiedInput().get("maxTokens");
                                            if (maxTokensOverride instanceof Number n) {
                                                cfgBuilder.maxTokens(n.intValue());
                                            }
                                            // Override temperature if provided
                                            Object tempOverride =
                                                    hookResult.modifiedInput().get("temperature");
                                            if (tempOverride instanceof Number n) {
                                                cfgBuilder.temperature(n.doubleValue());
                                            }
                                            effectiveConfig = cfgBuilder.build();
                                        } else {
                                            effectiveConfig = modelConfig;
                                        }

                                        // Inject additional context if provided
                                        if (hookResult.hasInjectedContext()) {
                                            Msg contextMsg =
                                                    Msg.builder()
                                                            .role(MsgRole.SYSTEM)
                                                            .addContent(
                                                                    new Content.TextContent(
                                                                            "[Hook Context] "
                                                                                    + hookResult
                                                                                            .injectedContext()))
                                                            .build();
                                            conversationHistory.add(contextMsg);
                                        }

                                        // 3. Call LLM with error recovery
                                        log.debug(
                                                "Agent '{}' iteration {}: calling model",
                                                ctx.agentName(),
                                                currentIteration.get());

                                        // Use streaming path if enabled
                                        Mono<ModelResponse> responseMono;
                                        if (streamingEnabled) {
                                            responseMono =
                                                    callModelStreamingWithFallback(
                                                            conversationHistory, effectiveConfig);
                                        } else {
                                            responseMono =
                                                    ctx.errorRecovery().callModelWithRecovery(
                                                            conversationHistory,
                                                            effectiveConfig,
                                                            0);
                                        }

                                        return responseMono.flatMap(
                                                response -> {
                                                    // Reset fallback on success
                                                    ctx.errorRecovery().fallbackManager().reset();

                                                    // Track token usage
                                                    if (response.usage() != null) {
                                                        ctx.tokenBudgetManager().updateFromApiUsage(
                                                                response.usage());
                                                        totalTokensUsed.addAndGet(
                                                                (long)
                                                                                response.usage()
                                                                                        .inputTokens()
                                                                        + response.usage()
                                                                                .outputTokens());

                                                        log.info(
                                                                "[Iteration {}] input={}"
                                                                        + " output={} cache_read={}"
                                                                        + " cache_write={}",
                                                                currentIteration.get(),
                                                                response.usage().inputTokens(),
                                                                response.usage().outputTokens(),
                                                                response.usage().cacheReadTokens(),
                                                                response.usage()
                                                                        .cacheCreationTokens());
                                                    } else {
                                                        log.warn(
                                                                "Model response missing usage"
                                                                        + " data — token tracking may"
                                                                        + " be inaccurate");
                                                    }
                                                    ctx.tokenBudgetManager().advanceTurn();

                                                    // 4. Fire PostReasoning hook with structured
                                                    // result
                                                    PostReasoningEvent postEvent =
                                                            new PostReasoningEvent(response, false);
                                                    return ctx.hookChain()
                                                            .<PostReasoningEvent>
                                                                    firePostReasoningWithResult(
                                                                            postEvent)
                                                            .flatMap(
                                                                    postResult -> {
                                                                        // Handle ABORT — skip tool
                                                                        // execution
                                                                        if (!postResult
                                                                                .shouldProceed()) {
                                                                            log.info(
                                                                                    "Agent '{}'"
                                                                                            + " post-reasoning"
                                                                                            + " aborted by"
                                                                                            + " hook: {}",
                                                                                    ctx.agentName(),
                                                                                    postResult
                                                                                            .reason());
                                                                            String reason =
                                                                                    postResult
                                                                                                            .reason()
                                                                                                    != null
                                                                                            ? postResult
                                                                                                    .reason()
                                                                                            : "Post-reasoning"
                                                                                                    + " aborted"
                                                                                                    + " by"
                                                                                                    + " hook.";
                                                                            return Mono.just(
                                                                                    buildFinalResponse(
                                                                                            reason));
                                                                        }

                                                                        // Inject post-reasoning
                                                                        // context if provided
                                                                        if (postResult
                                                                                .hasInjectedContext()) {
                                                                            Msg contextMsg =
                                                                                    Msg.builder()
                                                                                            .role(
                                                                                                    MsgRole
                                                                                                            .SYSTEM)
                                                                                            .addContent(
                                                                                                    new Content
                                                                                                            .TextContent(
                                                                                                            "[Hook"
                                                                                                                    + " Context]"
                                                                                                                    + " "
                                                                                                                    + postResult
                                                                                                                            .injectedContext()))
                                                                                            .build();
                                                                            conversationHistory.add(
                                                                                    contextMsg);
                                                                        }

                                                                        return processModelResponse(
                                                                                response);
                                                                    });
                                                });
                                    });
                });
    }

    /** Process the model response: convert to Msg, check for tool calls, execute if needed. */
    private Mono<Msg> processModelResponse(ModelResponse response) {
        // 5. Convert response to assistant message and add to history
        Msg assistantMsg = convertResponseToMsg(response);
        conversationHistory.add(assistantMsg);

        // 6. Check for tool calls
        List<Content.ToolUseContent> toolCalls = extractToolCalls(response);

        if (toolCalls.isEmpty()) {
            // No tool calls — this is the final answer
            log.debug("Agent '{}' produced final answer (no tool calls)", ctx.agentName());
            return Mono.just(assistantMsg);
        }

        // 6b. Check if these tool calls were already executed by the streaming path.
        // Streaming-executed tools have a "_streaming_result" key in their input.
        boolean streamingPreExecuted =
                toolCalls.stream()
                        .allMatch(
                                tc ->
                                        tc.input() != null
                                                && tc.input().containsKey("_streaming_result"));
        if (streamingPreExecuted) {
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
            Msg toolMsg = buildToolResultMsg(preResults);
            conversationHistory.add(toolMsg);

            currentIteration.incrementAndGet();
            return runLoop();
        }

        log.debug(
                "Agent '{}' requesting {} tool call(s): {}",
                ctx.agentName(),
                toolCalls.size(),
                toolCalls.stream()
                        .map(Content.ToolUseContent::toolName)
                        .collect(Collectors.joining(", ")));

        // 7. Execute tools with hooks
        return executeToolsWithHooks(toolCalls)
                .flatMap(
                        toolResults -> {
                            // 7b. Detect allowedTools from skill_load results
                            applySkillToolRestrictions(toolCalls, toolResults);

                            // 8. Build tool result message and add to history
                            Msg toolMsg = buildToolResultMsg(toolResults);
                            conversationHistory.add(toolMsg);

                            currentIteration.incrementAndGet();

                            // Auto-compaction check (delegated to CompactionTrigger)
                            if (compactionTrigger != null) {
                                return compactionTrigger
                                        .checkAndCompact(conversationHistory)
                                        .flatMap(compacted -> runLoop());
                            }

                            // 9. Recurse into next loop iteration
                            return runLoop();
                        });
    }

    /** Execute a list of tool calls, firing PreActing/PostActing hooks for each. */
    private Mono<List<ToolResult>> executeToolsWithHooks(List<Content.ToolUseContent> toolCalls) {
        if (ctx.toolExecutor() == null) {
            // No tool executor — return error results
            List<ToolResult> errors =
                    toolCalls.stream()
                            .map(
                                    tc ->
                                            new ToolResult(
                                                    tc.toolId(),
                                                    "No tool executor configured",
                                                    true,
                                                    Map.of()))
                            .toList();
            return Mono.just(errors);
        }

        // Execute tools sequentially with hooks
        return Flux.fromIterable(toolCalls)
                .concatMap(toolCall -> executeSingleToolWithHooks(toolCall))
                .collectList();
    }

    /** Execute a single tool call with PreActing and PostActing hooks. */
    private Mono<ToolResult> executeSingleToolWithHooks(Content.ToolUseContent toolCall) {
        // Fire PreActing hook with structured result support
        PreActingEvent preEvent = new PreActingEvent(toolCall.toolName(), toolCall.input(), false);

        return ctx.hookChain()
                .<PreActingEvent>firePreActingWithResult(preEvent)
                .flatMap(
                        hookResult -> {
                            // 1. Check ABORT decision
                            if (!hookResult.shouldProceed()) {
                                log.info(
                                        "Tool '{}' blocked by hook: {}",
                                        toolCall.toolName(),
                                        hookResult.reason());
                                return Mono.just(
                                        new ToolResult(
                                                toolCall.toolId(),
                                                "Tool execution blocked by hook: "
                                                        + (hookResult.reason() != null
                                                                ? hookResult.reason()
                                                                : "no reason given"),
                                                true,
                                                Map.of()));
                            }

                            // 1b. Check SKIP — return neutral result, continue loop
                            if (hookResult.shouldSkip()) {
                                log.info(
                                        "Tool '{}' skipped by hook: {}",
                                        toolCall.toolName(),
                                        hookResult.reason());
                                return Mono.just(
                                        new ToolResult(
                                                toolCall.toolId(),
                                                "Tool execution skipped by hook",
                                                false,
                                                Map.of("skipped_by_hook", true)));
                            }

                            // 1c. Handle INJECT — add message before tool execution
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

                            // 2. Check cancelled event (backward compat with old-style hooks)
                            PreActingEvent pre = hookResult.event();
                            if (pre.cancelled()) {
                                log.info(
                                        "Tool '{}' execution cancelled by hook",
                                        toolCall.toolName());
                                return Mono.just(
                                        new ToolResult(
                                                toolCall.toolId(),
                                                "Tool execution cancelled by hook",
                                                true,
                                                Map.of()));
                            }

                            // 3. Apply modified input if provided
                            Map<String, Object> effectiveInput =
                                    hookResult.hasModifiedInput()
                                            ? hookResult.modifiedInput()
                                            : toolCall.input();

                            // 4. Inject additional context if provided
                            if (hookResult.hasInjectedContext()) {
                                Msg contextMsg =
                                        Msg.builder()
                                                .role(MsgRole.SYSTEM)
                                                .addContent(
                                                        new Content.TextContent(
                                                                "[Hook Context] "
                                                                        + hookResult
                                                                                .injectedContext()))
                                                .build();
                                conversationHistory.add(contextMsg);
                            }

                            // 5. Execute the tool with (possibly modified) input
                            Instant toolStart = Instant.now();
                            return ctx.toolExecutor()
                                    .execute(toolCall.toolName(), effectiveInput)
                                    .map(
                                            result ->
                                                    new ToolResult(
                                                            toolCall.toolId(),
                                                            result.content(),
                                                            result.isError(),
                                                            result.metadata()))
                                    .onErrorResume(
                                            e -> {
                                                log.error(
                                                        "Tool '{}' execution failed: {}",
                                                        toolCall.toolName(),
                                                        e.getMessage());
                                                return Mono.just(
                                                        new ToolResult(
                                                                toolCall.toolId(),
                                                                "Error executing tool: "
                                                                        + e.getMessage(),
                                                                true,
                                                                Map.of()));
                                            })
                                    .doOnNext(
                                            toolResult -> {
                                                Duration toolDuration =
                                                        Duration.between(toolStart, Instant.now());
                                                ctx.hookChain()
                                                        .fireOnToolResult(
                                                                new ToolResultEvent(
                                                                        toolCall.toolName(),
                                                                        toolResult,
                                                                        toolDuration,
                                                                        !toolResult.isError()))
                                                        .subscribe();
                                            });
                        })
                .flatMap(
                        result -> {
                            // 6. Fire PostActing hook with structured result
                            PostActingEvent postEvent =
                                    new PostActingEvent(toolCall.toolName(), result);
                            return ctx.hookChain()
                                    .<PostActingEvent>firePostActingWithResult(postEvent)
                                    .map(
                                            postResult -> {
                                                // Inject post-acting context if provided
                                                if (postResult.hasInjectedContext()) {
                                                    Msg contextMsg =
                                                            Msg.builder()
                                                                    .role(MsgRole.SYSTEM)
                                                                    .addContent(
                                                                            new Content.TextContent(
                                                                                    "[Hook Context] "
                                                                                            + postResult
                                                                                                    .injectedContext()))
                                                                    .build();
                                                    conversationHistory.add(contextMsg);
                                                }
                                                return result;
                                            });
                        });
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

        Flux<StreamChunk> rawStream;
        if (provider instanceof io.kairo.core.model.AnthropicProvider anthropic) {
            rawStream = anthropic.streamRaw(messages, modelConfig);
        } else if (provider instanceof io.kairo.core.model.OpenAIProvider openai) {
            rawStream = openai.streamRaw(messages, modelConfig);
        } else {
            log.debug("Provider '{}' does not support streamRaw, falling back", provider.name());
            return ctx.errorRecovery().callModelWithRecovery(messages, modelConfig, 0);
        }

        // Detect tool calls incrementally
        var detector = new StreamingToolDetector();
        Flux<DetectedToolCall> detectedTools = detector.detect(rawStream);

        // If the tool executor supports streaming dispatch
        if (ctx.toolExecutor().supportsStreaming()) {
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
                                    return ctx.errorRecovery().callModelWithRecovery(
                                            messages, modelConfig, 0);
                                }
                                log.debug(
                                        "Streaming execution completed {} tool results",
                                        toolResults.size());
                                // Build a synthetic ModelResponse with tool_use contents
                                var contents = new ArrayList<Content>();
                                for (var tr : toolResults) {
                                    String toolName =
                                            toolNameMap.getOrDefault(
                                                    tr.toolUseId(), tr.toolUseId());
                                    contents.add(
                                            new Content.ToolUseContent(
                                                    tr.toolUseId(),
                                                    toolName,
                                                    Map.of("_streaming_result", tr.content())));
                                }
                                return Mono.just(
                                        new ModelResponse(
                                                "streaming",
                                                contents,
                                                new ModelResponse.Usage(0, 0, 0, 0),
                                                ModelResponse.StopReason.TOOL_USE,
                                                modelConfig.model()));
                            });
        }

        // No DefaultToolExecutor — fall back
        return ctx.errorRecovery().callModelWithRecovery(messages, modelConfig, 0);
    }

    // ---- Conversion helpers ----

    /** Convert a {@link ModelResponse} into an assistant {@link Msg}. */
    private Msg convertResponseToMsg(ModelResponse response) {
        MsgBuilder builder = MsgBuilder.create().role(MsgRole.ASSISTANT).sourceAgentId(ctx.agentId());

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
        return MsgBuilder.create().role(MsgRole.ASSISTANT).sourceAgentId(ctx.agentId()).text(text).build();
    }

    /** Apply skill_load tool restrictions from tool results. */
    private void applySkillToolRestrictions(
            List<Content.ToolUseContent> toolCalls, List<ToolResult> toolResults) {
        for (int i = 0; i < toolResults.size(); i++) {
            ToolResult tr = toolResults.get(i);
            String toolName = i < toolCalls.size() ? toolCalls.get(i).toolName() : null;
            if ("skill_load".equals(toolName) && tr.metadata() != null) {
                Object allowedTools = tr.metadata().get("allowedTools");
                if (allowedTools instanceof List<?> toolList) {
                    Set<String> allowed =
                            toolList.stream()
                                    .filter(String.class::isInstance)
                                    .map(String.class::cast)
                                    .collect(Collectors.toSet());
                    if (!allowed.isEmpty()) {
                        ctx.toolExecutor().setAllowedTools(allowed);
                        log.info("Skill tool restrictions activated: {}", allowed);
                    }
                }
            }
        }
    }
}
