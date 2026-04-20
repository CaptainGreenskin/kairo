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
package io.kairo.core.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kairo.api.context.SystemPromptSegment;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelCapability;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.model.StreamChunk;
import io.kairo.api.model.ToolVerbosity;
import io.kairo.api.tool.ToolDefinition;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;

/**
 * {@link ModelProvider} implementation for the Anthropic Messages API.
 *
 * <p>Supports both synchronous and streaming calls to Claude models, including extended thinking,
 * tool use, and prompt caching.
 *
 * <p>Uses JDK 11+ built-in {@link HttpClient} for non-blocking HTTP calls.
 */
public class AnthropicProvider implements ModelProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicProvider.class);
    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration STREAM_IDLE_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Duration RETRY_MIN_BACKOFF = Duration.ofSeconds(1);
    private static final Duration RETRY_MAX_BACKOFF = Duration.ofSeconds(4);

    private final String apiKey;
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final AnthropicHttpClient anthropicHttpClient;
    private final AnthropicResponseParser responseParser;
    private final ComplexityEstimator complexityEstimator = new ComplexityEstimator();
    private final ToolDescriptionAdapter toolAdapter = new ToolDescriptionAdapter();
    private final CacheBreakDetector cacheDetector = new CacheBreakDetector();

    /**
     * Create an AnthropicProvider with default settings.
     *
     * @param apiKey the Anthropic API key
     */
    public AnthropicProvider(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL);
    }

    /**
     * Create an AnthropicProvider with a custom base URL.
     *
     * @param apiKey the Anthropic API key
     * @param baseUrl the API base URL (e.g. for proxy)
     */
    public AnthropicProvider(String apiKey, String baseUrl) {
        this(apiKey, baseUrl, ModelProviderUtils.createHttpClient(Duration.ofSeconds(30)));
    }

    /**
     * Create an AnthropicProvider with full customization.
     *
     * @param apiKey the Anthropic API key
     * @param baseUrl the API base URL
     * @param httpClient the HTTP client to use
     */
    public AnthropicProvider(String apiKey, String baseUrl, HttpClient httpClient) {
        ModelProviderUtils.validateApiKey(apiKey, "Anthropic");
        ModelProviderUtils.validateBaseUrl(baseUrl, "Anthropic");
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = ModelProviderUtils.createObjectMapper();
        this.anthropicHttpClient = new AnthropicHttpClient(httpClient, this.baseUrl, apiKey);
        this.responseParser = new AnthropicResponseParser(this.objectMapper);
    }

    @Override
    public String name() {
        return "anthropic";
    }

    @Override
    public Mono<ModelResponse> call(List<Msg> messages, ModelConfig config) {
        return Mono.fromCallable(() -> buildRequestBody(messages, config, false))
                .flatMap(anthropicHttpClient::sendRequest)
                .flatMap(
                        body -> {
                            try {
                                return Mono.just(responseParser.parseResponse(body));
                            } catch (Exception e) {
                                return Mono.error(
                                        new ModelProviderException.ApiException(
                                                "Failed to parse Anthropic response", e));
                            }
                        })
                .timeout(CALL_TIMEOUT)
                .retryWhen(
                        Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_MIN_BACKOFF)
                                .maxBackoff(RETRY_MAX_BACKOFF)
                                .jitter(0.25)
                                .filter(this::isRetryableError)
                                .doBeforeRetry(
                                        signal -> {
                                            Throwable failure = signal.failure();
                                            if (failure
                                                            instanceof
                                                            ModelProviderException
                                                                            .RateLimitException
                                                                    rle
                                                    && rle.getRetryAfterSeconds() != null) {
                                                log.warn(
                                                        "Rate limited, retry {} (server suggests"
                                                                + " {}s wait)",
                                                        signal.totalRetries() + 1,
                                                        rle.getRetryAfterSeconds());
                                            } else {
                                                log.warn(
                                                        "Retrying API call, attempt {}: {}",
                                                        signal.totalRetries() + 1,
                                                        failure.getMessage());
                                            }
                                        }))
                .doOnNext(
                        response -> {
                            String sysPrompt = resolveSystemPrompt(messages, config);
                            List<ToolDefinition> tools =
                                    config.tools() != null ? config.tools() : List.of();
                            CacheCheckResult cacheResult =
                                    cacheDetector.check(response.usage(), sysPrompt, tools);
                            if (cacheResult.isCacheBroken()) {
                                log.warn(
                                        "Cache break detected: reasons={}, hitRatio={}",
                                        cacheResult.reasons(),
                                        String.format("%.2f", cacheResult.hitRatio()));
                            }
                        });
    }

    @Override
    public Flux<ModelResponse> stream(List<Msg> messages, ModelConfig config) {
        return Flux.defer(
                        () -> {
                            try {
                                String body = buildRequestBody(messages, config, true);

                                Sinks.Many<ModelResponse> sink =
                                        Sinks.many().unicast().onBackpressureBuffer();

                                anthropicHttpClient
                                        .sendStreamingRequest(
                                                body, new SseLineSubscriber(sink, objectMapper))
                                        .whenComplete(
                                                (resp, err) -> {
                                                    if (err != null) {
                                                        sink.tryEmitError(
                                                                new ModelProviderException
                                                                        .ApiException(
                                                                        "Streaming request failed",
                                                                        err));
                                                    }
                                                });

                                return sink.asFlux();
                            } catch (Exception e) {
                                return Flux.error(
                                        new ModelProviderException.ApiException(
                                                "Failed to build streaming request", e));
                            }
                        })
                .timeout(STREAM_IDLE_TIMEOUT)
                .retryWhen(
                        Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_MIN_BACKOFF)
                                .maxBackoff(RETRY_MAX_BACKOFF)
                                .filter(this::isRetryableError)
                                .doBeforeRetry(
                                        signal ->
                                                log.warn(
                                                        "Stream retry attempt {} due to: {}",
                                                        signal.totalRetries() + 1,
                                                        signal.failure().getMessage())));
    }

    /**
     * Stream raw chunks for incremental processing.
     *
     * <p>Unlike {@link #stream(List, ModelConfig)} which collects the full response, this emits
     * {@link StreamChunk} objects as they arrive from the SSE stream. Consumers can use {@link
     * StreamingToolDetector} to detect complete tool_use blocks for early execution.
     *
     * @param messages the conversation history
     * @param config model configuration
     * @return a Flux of raw streaming chunks
     */
    public Flux<StreamChunk> streamRaw(List<Msg> messages, ModelConfig config) {
        return Flux.defer(
                        () -> {
                            try {
                                String body = buildRequestBody(messages, config, true);

                                Sinks.Many<StreamChunk> sink =
                                        Sinks.many().unicast().onBackpressureBuffer();

                                anthropicHttpClient
                                        .sendStreamingRequest(
                                                body, new RawSseLineSubscriber(sink, objectMapper))
                                        .whenComplete(
                                                (resp, err) -> {
                                                    if (err != null) {
                                                        sink.tryEmitNext(
                                                                StreamChunk.error(
                                                                        "Streaming request failed: "
                                                                                + err
                                                                                        .getMessage()));
                                                        sink.tryEmitComplete();
                                                    }
                                                });

                                return sink.asFlux();
                            } catch (Exception e) {
                                return Flux.error(
                                        new ModelProviderException.ApiException(
                                                "Failed to build streaming request", e));
                            }
                        })
                .timeout(STREAM_IDLE_TIMEOUT)
                .retryWhen(
                        Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_MIN_BACKOFF)
                                .maxBackoff(RETRY_MAX_BACKOFF)
                                .filter(this::isRetryableError)
                                .doBeforeRetry(
                                        signal ->
                                                log.warn(
                                                        "Stream retry attempt {} due to: {}",
                                                        signal.totalRetries() + 1,
                                                        signal.failure().getMessage())));
    }

    /**
     * Run cache break detection on a response and record results to a span.
     *
     * @param response the model response containing usage data
     * @param systemPrompt the system prompt sent in this call
     * @param tools the tool definitions sent in this call
     * @param span the tracing span to record cache metrics on
     * @return the cache check result
     */
    CacheCheckResult checkCacheAndRecord(
            ModelResponse response,
            String systemPrompt,
            List<ToolDefinition> tools,
            io.kairo.api.tracing.Span span) {
        CacheCheckResult result = cacheDetector.check(response.usage(), systemPrompt, tools);
        span.setAttribute("cache.hit_ratio", result.hitRatio());
        span.setAttribute("cache.read_tokens", result.cacheReadTokens());
        span.setAttribute("cache.creation_tokens", result.cacheCreationTokens());
        if (result.isCacheBroken()) {
            span.setAttribute("cache.broken", true);
            span.setAttribute("cache.break_reasons", String.join(",", result.reasons()));
        }
        return result;
    }

    private static String resolveSystemPrompt(List<Msg> messages, ModelConfig config) {
        String sp = config.systemPrompt();
        if (sp != null && !sp.isBlank()) return sp;
        return messages.stream()
                .filter(m -> m.role() == MsgRole.SYSTEM)
                .map(Msg::text)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    /**
     * Determine if an error is retryable (transient).
     *
     * <p>Retries on: timeouts, rate limits (429), and server errors (500/502/503).
     */
    private boolean isRetryableError(Throwable t) {
        if (t instanceof TimeoutException) return true;
        if (t instanceof ModelProviderException.RateLimitException) return true;
        if (t instanceof ModelProviderException.ApiException ae) {
            String msg = ae.getMessage();
            if (msg != null) {
                return msg.contains("server error")
                        || msg.contains("HTTP 500")
                        || msg.contains("HTTP 502")
                        || msg.contains("HTTP 503");
            }
        }
        return false;
    }

    // ---- Request building ----

    /**
     * Build the Anthropic Messages API request body.
     *
     * @param messages the conversation messages
     * @param config model configuration
     * @param stream whether this is a streaming request
     * @return JSON string of the request body
     */
    String buildRequestBody(List<Msg> messages, ModelConfig config, boolean stream)
            throws JsonProcessingException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", config.model());
        root.put("max_tokens", config.maxTokens());

        if (config.temperature() >= 0) {
            root.put("temperature", config.temperature());
        }
        if (stream) {
            root.put("stream", true);
        }

        // Extract system prompt: from config or from SYSTEM messages
        String systemPrompt = config.systemPrompt();
        if (systemPrompt == null || systemPrompt.isBlank()) {
            systemPrompt =
                    messages.stream()
                            .filter(m -> m.role() == MsgRole.SYSTEM)
                            .map(Msg::text)
                            .reduce((a, b) -> a + "\n" + b)
                            .orElse(null);
        }

        // Structured output: inject JSON schema constraint into system prompt
        if (config.responseSchema() != null) {
            String schemaJson =
                    JsonSchemaGenerator.generateSchema(config.responseSchema(), objectMapper)
                            .toString();
            String schemaInstruction =
                    "\n\nYou MUST respond with valid JSON matching this schema: "
                            + schemaJson
                            + "\nDo NOT include any text outside the JSON object.";
            systemPrompt = (systemPrompt != null ? systemPrompt : "") + schemaInstruction;
        }

        // Priority 1: Multi-segment system prompt
        if (config.systemPromptSegments() != null && !config.systemPromptSegments().isEmpty()) {
            ArrayNode systemArray = objectMapper.createArrayNode();

            for (SystemPromptSegment segment : config.systemPromptSegments()) {
                ObjectNode block = objectMapper.createObjectNode();
                block.put("type", "text");
                block.put("text", segment.content());

                // Add cache_control for cacheable segments (GLOBAL and SESSION)
                if (segment.isCacheable()) {
                    ObjectNode cacheControl = objectMapper.createObjectNode();
                    cacheControl.put("type", "ephemeral");
                    block.set("cache_control", cacheControl);
                }

                systemArray.add(block);
            }

            root.set("system", systemArray);
        }
        // Priority 2: Legacy static/dynamic parts
        else if (systemPrompt != null && !systemPrompt.isBlank()) {
            Map<String, String> parts = config.systemPromptParts();
            if (parts != null
                    && parts.containsKey("staticPrefix")
                    && parts.containsKey("dynamicSuffix")
                    && !parts.get("staticPrefix").isBlank()
                    && !parts.get("dynamicSuffix").isBlank()) {
                // Serialize as array with cache_control on static prefix
                ArrayNode systemArray = objectMapper.createArrayNode();

                ObjectNode staticBlock = objectMapper.createObjectNode();
                staticBlock.put("type", "text");
                staticBlock.put("text", parts.get("staticPrefix"));
                ObjectNode cacheControl = objectMapper.createObjectNode();
                cacheControl.put("type", "ephemeral");
                staticBlock.set("cache_control", cacheControl);
                systemArray.add(staticBlock);

                ObjectNode dynamicBlock = objectMapper.createObjectNode();
                dynamicBlock.put("type", "text");
                dynamicBlock.put("text", parts.get("dynamicSuffix"));
                systemArray.add(dynamicBlock);

                root.set("system", systemArray);
            } else {
                // Priority 3: Single string
                root.put("system", systemPrompt);
            }
        }

        // Build messages array (exclude SYSTEM role)
        ArrayNode messagesNode = root.putArray("messages");
        for (Msg msg : messages) {
            if (msg.role() == MsgRole.SYSTEM) continue;
            messagesNode.add(convertMsg(msg));
        }

        // Tools — adapt descriptions by model verbosity and add cache_control on last tool
        List<ToolDefinition> toolsToSerialize = config.tools();
        ModelCapability capability = ModelCapabilityRegistry.lookup(config.model());
        ToolVerbosity verbosity =
                config.toolVerbosity() != null
                        ? config.toolVerbosity()
                        : capability.toolVerbosity();
        toolsToSerialize = toolAdapter.adaptForModel(toolsToSerialize, verbosity);
        if (toolsToSerialize != null && !toolsToSerialize.isEmpty()) {
            ArrayNode toolsNode = root.putArray("tools");
            for (int i = 0; i < toolsToSerialize.size(); i++) {
                ObjectNode toolNode = convertTool(toolsToSerialize.get(i));
                // Add cache_control on the last tool definition for prompt caching
                if (i == toolsToSerialize.size() - 1) {
                    ObjectNode cacheCtl = objectMapper.createObjectNode();
                    cacheCtl.put("type", "ephemeral");
                    toolNode.set("cache_control", cacheCtl);
                }
                toolsNode.add(toolNode);
            }
        }

        // Extended thinking — use dynamic budget from complexity if not explicitly configured
        if (config.thinking() != null && config.thinking().enabled()) {
            ObjectNode thinkingNode = root.putObject("thinking");
            thinkingNode.put("type", "enabled");
            thinkingNode.put("budget_tokens", config.thinking().budgetTokens());
        } else if (capability.supportsThinking() && capability.thinkingBudgetRange() != null) {
            // Auto-enable thinking with dynamic budget based on conversation complexity
            Integer explicitBudget = config.thinkingBudget();
            int budget;
            if (explicitBudget != null && explicitBudget > 0) {
                budget = capability.thinkingBudgetRange().clamp(explicitBudget);
            } else {
                int complexity = complexityEstimator.estimateComplexity(messages);
                budget = complexityEstimator.thinkingBudget(capability, complexity);
            }
            if (budget > 0) {
                ObjectNode thinkingNode = root.putObject("thinking");
                thinkingNode.put("type", "enabled");
                thinkingNode.put("budget_tokens", budget);
            }
        }

        // Effort parameter — maps to Anthropic thinking budget_tokens scaling
        if (config.effort() != null) {
            double effort = config.effort();
            if (effort >= 0.0 && effort <= 1.0) {
                // Map effort 0.0-1.0 to a thinking budget scale
                // If thinking is not already enabled, enable it with effort-derived budget
                if (!root.has("thinking")) {
                    int maxBudget = config.maxTokens() * 4; // up to 4x output tokens for thinking
                    int effortBudget = (int) (effort * maxBudget);
                    if (effortBudget > 0) {
                        ObjectNode thinkingNode = root.putObject("thinking");
                        thinkingNode.put("type", "enabled");
                        thinkingNode.put("budget_tokens", Math.max(1024, effortBudget));
                    }
                }
            }
        }

        return objectMapper.writeValueAsString(root);
    }

    private ObjectNode convertMsg(Msg msg) {
        ObjectNode msgNode = objectMapper.createObjectNode();
        String role =
                switch (msg.role()) {
                    case USER, TOOL -> "user";
                    case ASSISTANT -> "assistant";
                    case SYSTEM ->
                            throw new IllegalArgumentException(
                                    "SYSTEM messages should be extracted separately");
                };
        msgNode.put("role", role);

        List<Content> contents = msg.contents();
        boolean hasCacheControl =
                msg.metadata() != null && msg.metadata().containsKey("cache_control");
        // Single text content -> use string form (unless cache_control is needed)
        if (contents.size() == 1
                && contents.get(0) instanceof Content.TextContent tc
                && !hasCacheControl) {
            msgNode.put("content", tc.text());
        } else {
            ArrayNode contentArray = msgNode.putArray("content");
            for (int i = 0; i < contents.size(); i++) {
                ObjectNode contentNode = convertContent(contents.get(i));
                // Add cache_control to the last content block of a cache-hinted message
                if (hasCacheControl && i == contents.size() - 1) {
                    ObjectNode cc = objectMapper.createObjectNode();
                    cc.put("type", msg.metadata().get("cache_control").toString());
                    contentNode.set("cache_control", cc);
                }
                contentArray.add(contentNode);
            }
        }
        return msgNode;
    }

    private ObjectNode convertContent(Content content) {
        ObjectNode node = objectMapper.createObjectNode();
        if (content instanceof Content.TextContent tc) {
            node.put("type", "text");
            node.put("text", tc.text());
        } else if (content instanceof Content.ImageContent ic) {
            node.put("type", "image");
            ObjectNode source = node.putObject("source");
            if (ic.url() != null) {
                source.put("type", "url");
                source.put("url", ic.url());
            } else if (ic.data() != null) {
                source.put("type", "base64");
                source.put("media_type", ic.mediaType());
                source.put("data", java.util.Base64.getEncoder().encodeToString(ic.data()));
            }
        } else if (content instanceof Content.ToolUseContent tu) {
            node.put("type", "tool_use");
            node.put("id", tu.toolId());
            node.put("name", tu.toolName());
            node.set("input", objectMapper.valueToTree(tu.input()));
        } else if (content instanceof Content.ToolResultContent tr) {
            node.put("type", "tool_result");
            node.put("tool_use_id", tr.toolUseId());
            node.put("content", tr.content());
            if (tr.isError()) {
                node.put("is_error", true);
            }
        } else if (content instanceof Content.ThinkingContent tc) {
            node.put("type", "thinking");
            node.put("thinking", tc.thinking());
        }
        return node;
    }

    private ObjectNode convertTool(ToolDefinition tool) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", tool.name());
        node.put("description", tool.description());

        ObjectNode inputSchema = objectMapper.createObjectNode();
        inputSchema.put("type", tool.inputSchema().type());
        if (tool.inputSchema().properties() != null) {
            ObjectNode props = inputSchema.putObject("properties");
            tool.inputSchema()
                    .properties()
                    .forEach(
                            (key, schema) -> {
                                ObjectNode propNode = props.putObject(key);
                                propNode.put("type", schema.type());
                                if (schema.description() != null) {
                                    propNode.put("description", schema.description());
                                }
                            });
        }
        if (tool.inputSchema().required() != null) {
            ArrayNode reqArray = inputSchema.putArray("required");
            tool.inputSchema().required().forEach(reqArray::add);
        }
        node.set("input_schema", inputSchema);
        return node;
    }

    // ---- Response parsing (delegated to AnthropicResponseParser) ----

    /**
     * Parse a non-streaming Anthropic API response. Delegates to {@link AnthropicResponseParser}.
     */
    ModelResponse parseResponse(String responseBody) throws JsonProcessingException {
        return responseParser.parseResponse(responseBody);
    }

    // ---- SSE stream parsing ----

    /**
     * A line-based subscriber for SSE event streams from the Anthropic API. Parses SSE events and
     * emits {@link ModelResponse} fragments via a Reactor Sink.
     */
    static class SseLineSubscriber implements Flow.Subscriber<String> {

        private final Sinks.Many<ModelResponse> sink;
        private final ObjectMapper objectMapper;
        private Flow.Subscription subscription;

        // Accumulator state for building partial responses
        private String responseId;
        private String responseModel;
        private final List<ContentAccumulator> contentAccumulators = new ArrayList<>();
        private ModelResponse.StopReason stopReason;
        private ModelResponse.Usage usage;

        SseLineSubscriber(Sinks.Many<ModelResponse> sink, ObjectMapper objectMapper) {
            this.sink = sink;
            this.objectMapper = objectMapper;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(String line) {
            if (line == null || line.isBlank()) return;

            // SSE format: "event: <type>" or "data: <json>"
            if (line.startsWith("event:")) {
                // Event type line - we process data lines instead
                return;
            }
            if (!line.startsWith("data:")) return;

            String data = line.substring(5).trim();
            if (data.equals("[DONE]")) {
                sink.tryEmitComplete();
                return;
            }

            try {
                JsonNode event = objectMapper.readTree(data);
                String type = event.path("type").asText();
                processEvent(type, event);
            } catch (Exception e) {
                LoggerFactory.getLogger(SseLineSubscriber.class)
                        .warn("Failed to parse SSE event: {}", data, e);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            sink.tryEmitError(throwable);
        }

        @Override
        public void onComplete() {
            sink.tryEmitComplete();
        }

        private void processEvent(String type, JsonNode event) {
            switch (type) {
                case "message_start" -> {
                    JsonNode message = event.path("message");
                    responseId = message.path("id").asText();
                    responseModel = message.path("model").asText();
                    JsonNode usageNode = message.path("usage");
                    if (!usageNode.isMissingNode()) {
                        usage =
                                new ModelResponse.Usage(
                                        usageNode.path("input_tokens").asInt(0),
                                        usageNode.path("output_tokens").asInt(0),
                                        usageNode.path("cache_read_input_tokens").asInt(0),
                                        usageNode.path("cache_creation_input_tokens").asInt(0));
                    }
                }
                case "content_block_start" -> {
                    int index = event.path("index").asInt();
                    JsonNode block = event.path("content_block");
                    String blockType = block.path("type").asText();
                    while (contentAccumulators.size() <= index) {
                        contentAccumulators.add(new ContentAccumulator());
                    }
                    ContentAccumulator acc = contentAccumulators.get(index);
                    acc.type = blockType;
                    // For tool_use, capture id and name from start block
                    if ("tool_use".equals(blockType)) {
                        acc.toolId = block.path("id").asText();
                        acc.toolName = block.path("name").asText();
                    }
                }
                case "content_block_delta" -> {
                    int index = event.path("index").asInt();
                    JsonNode delta = event.path("delta");
                    String deltaType = delta.path("type").asText();

                    if (index < contentAccumulators.size()) {
                        ContentAccumulator acc = contentAccumulators.get(index);
                        switch (deltaType) {
                            case "text_delta" -> {
                                String text = delta.path("text").asText("");
                                acc.textBuilder.append(text);
                                // Emit partial text response for streaming consumers
                                emitPartial(text, acc.type);
                            }
                            case "thinking_delta" -> {
                                String thinking = delta.path("thinking").asText("");
                                acc.textBuilder.append(thinking);
                                emitPartial(thinking, "thinking");
                            }
                            case "input_json_delta" -> {
                                String partial = delta.path("partial_json").asText("");
                                acc.textBuilder.append(partial);
                            }
                        }
                    }
                }
                case "content_block_stop" -> {
                    // Block complete - no action needed, accumulator holds full data
                }
                case "message_delta" -> {
                    JsonNode delta = event.path("delta");
                    String reason = delta.path("stop_reason").asText(null);
                    if (reason != null) {
                        stopReason =
                                switch (reason) {
                                    case "end_turn" -> ModelResponse.StopReason.END_TURN;
                                    case "tool_use" -> ModelResponse.StopReason.TOOL_USE;
                                    case "max_tokens" -> ModelResponse.StopReason.MAX_TOKENS;
                                    case "stop_sequence" -> ModelResponse.StopReason.STOP_SEQUENCE;
                                    default -> ModelResponse.StopReason.END_TURN;
                                };
                    }
                    JsonNode usageNode = event.path("usage");
                    if (!usageNode.isMissingNode()) {
                        int outputTokens = usageNode.path("output_tokens").asInt(0);
                        usage =
                                new ModelResponse.Usage(
                                        usage != null ? usage.inputTokens() : 0,
                                        outputTokens,
                                        usage != null ? usage.cacheReadTokens() : 0,
                                        usage != null ? usage.cacheCreationTokens() : 0);
                    }
                }
                case "message_stop" -> {
                    // Emit the final assembled response
                    List<Content> finalContents = new ArrayList<>();
                    for (ContentAccumulator acc : contentAccumulators) {
                        Content c = acc.toContent(objectMapper);
                        if (c != null) finalContents.add(c);
                    }
                    ModelResponse finalResponse =
                            new ModelResponse(
                                    responseId,
                                    finalContents,
                                    usage != null ? usage : new ModelResponse.Usage(0, 0, 0, 0),
                                    stopReason,
                                    responseModel);
                    sink.tryEmitNext(finalResponse);
                    sink.tryEmitComplete();
                }
                default ->
                        LoggerFactory.getLogger(SseLineSubscriber.class)
                                .debug("Unknown SSE event type: {}", type);
            }
        }

        private void emitPartial(String text, String contentType) {
            Content partialContent =
                    "thinking".equals(contentType)
                            ? new Content.ThinkingContent(text, 0)
                            : new Content.TextContent(text);
            ModelResponse partial =
                    new ModelResponse(
                            responseId,
                            List.of(partialContent),
                            new ModelResponse.Usage(0, 0, 0, 0),
                            null,
                            responseModel);
            sink.tryEmitNext(partial);
        }
    }

    /** Accumulates content block data during streaming. */
    static class ContentAccumulator {
        String type;
        StringBuilder textBuilder = new StringBuilder();
        String toolId;
        String toolName;

        @SuppressWarnings("unchecked")
        Content toContent(ObjectMapper objectMapper) {
            return switch (type) {
                case "text" -> new Content.TextContent(textBuilder.toString());
                case "thinking" -> new Content.ThinkingContent(textBuilder.toString(), 0);
                case "tool_use" -> {
                    Map<String, Object> input;
                    try {
                        String json = textBuilder.toString();
                        if (json.isBlank()) {
                            input = Map.of();
                        } else {
                            input = objectMapper.readValue(json, Map.class);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse tool_use input JSON", e);
                        input = Map.of();
                    }
                    yield new Content.ToolUseContent(toolId, toolName, input);
                }
                default -> null;
            };
        }
    }

    /**
     * A line-based SSE subscriber that emits raw {@link StreamChunk} objects for incremental
     * processing.
     */
    static class RawSseLineSubscriber implements Flow.Subscriber<String> {

        private final Sinks.Many<StreamChunk> sink;
        private final ObjectMapper objectMapper;
        private Flow.Subscription subscription;

        // Track current content block index to tool-call-id mapping
        private final Map<Integer, String> blockToolIds = new HashMap<>();
        private final Map<Integer, String> blockTypes = new HashMap<>();

        RawSseLineSubscriber(Sinks.Many<StreamChunk> sink, ObjectMapper objectMapper) {
            this.sink = sink;
            this.objectMapper = objectMapper;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(String line) {
            if (line == null || line.isBlank()) return;
            if (line.startsWith("event:")) return;
            if (!line.startsWith("data:")) return;

            String data = line.substring(5).trim();
            if (data.equals("[DONE]")) {
                sink.tryEmitComplete();
                return;
            }

            try {
                JsonNode event = objectMapper.readTree(data);
                String type = event.path("type").asText();
                processRawEvent(type, event);
            } catch (Exception e) {
                LoggerFactory.getLogger(RawSseLineSubscriber.class)
                        .warn("Failed to parse SSE event: {}", data, e);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            sink.tryEmitNext(StreamChunk.error(throwable.getMessage()));
            sink.tryEmitComplete();
        }

        @Override
        public void onComplete() {
            sink.tryEmitComplete();
        }

        private void processRawEvent(String type, JsonNode event) {
            switch (type) {
                case "content_block_start" -> {
                    int index = event.path("index").asInt();
                    JsonNode block = event.path("content_block");
                    String blockType = block.path("type").asText();
                    blockTypes.put(index, blockType);
                    if ("tool_use".equals(blockType)) {
                        String toolId = block.path("id").asText();
                        String toolName = block.path("name").asText();
                        blockToolIds.put(index, toolId);
                        sink.tryEmitNext(StreamChunk.toolUseStart(toolId, toolName));
                    }
                }
                case "content_block_delta" -> {
                    int index = event.path("index").asInt();
                    JsonNode delta = event.path("delta");
                    String deltaType = delta.path("type").asText();
                    switch (deltaType) {
                        case "text_delta" ->
                                sink.tryEmitNext(StreamChunk.text(delta.path("text").asText("")));
                        case "thinking_delta" ->
                                sink.tryEmitNext(
                                        StreamChunk.thinking(delta.path("thinking").asText("")));
                        case "input_json_delta" -> {
                            String toolId = blockToolIds.get(index);
                            String partial = delta.path("partial_json").asText("");
                            if (toolId != null) {
                                sink.tryEmitNext(StreamChunk.toolUseDelta(toolId, partial));
                            }
                        }
                    }
                }
                case "content_block_stop" -> {
                    int index = event.path("index").asInt();
                    String blockType = blockTypes.get(index);
                    if ("tool_use".equals(blockType)) {
                        String toolId = blockToolIds.get(index);
                        if (toolId != null) {
                            sink.tryEmitNext(StreamChunk.toolUseEnd(toolId));
                        }
                    }
                }
                case "message_stop" -> sink.tryEmitNext(StreamChunk.done());
                case "error" -> {
                    String msg = event.path("error").path("message").asText("Unknown error");
                    sink.tryEmitNext(StreamChunk.error(msg));
                }
                default -> {
                    // message_start, message_delta, ping — ignored for raw chunks
                }
            }
        }
    }

    // ---- Exception types ----

    /** Parse the Retry-After header value to seconds. */
    private static Long parseRetryAfter(String retryAfter) {
        return ModelProviderUtils.parseRetryAfter(retryAfter);
    }

    /**
     * Thrown when the API returns a 429 rate limit response.
     *
     * @deprecated Use {@link ModelProviderException.RateLimitException} directly.
     */
    public static class RateLimitException extends ModelProviderException.RateLimitException {
        public RateLimitException(String message, Long retryAfterSeconds) {
            super(message, retryAfterSeconds);
        }
    }

    /**
     * General API error.
     *
     * @deprecated Use {@link ModelProviderException.ApiException} directly.
     */
    public static class ApiException extends ModelProviderException.ApiException {
        public ApiException(String message) {
            super(message);
        }

        public ApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ---- Cache break detection ----

    /**
     * Detects KV-cache breaks by comparing consecutive API call usage metrics. Tracks system prompt
     * hash, tool schema hash, and call timestamps to diagnose why cache_read_tokens dropped.
     */
    static class CacheBreakDetector {
        private long lastSystemPromptHash;
        private long lastToolSchemaHash;
        private int lastCacheReadTokens;
        private Instant lastCallTime;
        private boolean hasPreviousCall;

        /** Allow injecting a custom clock for testing TTL expiry. */
        private Instant nowOverride;

        void setNowOverride(Instant now) {
            this.nowOverride = now;
        }

        private Instant now() {
            return nowOverride != null ? nowOverride : Instant.now();
        }

        CacheCheckResult check(
                ModelResponse.Usage usage, String systemPrompt, List<ToolDefinition> tools) {
            long currentSysHash = systemPrompt != null ? systemPrompt.hashCode() : 0;
            long currentToolHash = tools != null ? tools.hashCode() : 0;
            int currentCacheRead = usage.cacheReadTokens();

            CacheCheckResult result =
                    new CacheCheckResult(
                            currentCacheRead, usage.cacheCreationTokens(), usage.inputTokens());

            if (hasPreviousCall
                    && lastCacheReadTokens > 0
                    && currentCacheRead < lastCacheReadTokens * 0.95) {
                if (currentSysHash != lastSystemPromptHash) {
                    result.addReason("system_prompt_changed");
                }
                if (currentToolHash != lastToolSchemaHash) {
                    result.addReason("tool_schema_changed");
                }
                if (lastCallTime != null && Duration.between(lastCallTime, now()).toMinutes() > 5) {
                    result.addReason("ttl_expired");
                }
                result.setCacheBroken(true);
            }

            // Update state
            lastSystemPromptHash = currentSysHash;
            lastToolSchemaHash = currentToolHash;
            lastCacheReadTokens = currentCacheRead;
            lastCallTime = now();
            hasPreviousCall = true;

            return result;
        }
    }

    /** Result of a cache break detection check. */
    static class CacheCheckResult {
        private final int cacheReadTokens;
        private final int cacheCreationTokens;
        private final int inputTokens;
        private final List<String> reasons = new ArrayList<>();
        private boolean cacheBroken;

        CacheCheckResult(int cacheReadTokens, int cacheCreationTokens, int inputTokens) {
            this.cacheReadTokens = cacheReadTokens;
            this.cacheCreationTokens = cacheCreationTokens;
            this.inputTokens = inputTokens;
        }

        double hitRatio() {
            int total = cacheReadTokens + cacheCreationTokens;
            return total == 0 ? 0.0 : (double) cacheReadTokens / total;
        }

        int cacheReadTokens() {
            return cacheReadTokens;
        }

        int cacheCreationTokens() {
            return cacheCreationTokens;
        }

        int inputTokens() {
            return inputTokens;
        }

        boolean isCacheBroken() {
            return cacheBroken;
        }

        List<String> reasons() {
            return reasons;
        }

        void addReason(String reason) {
            reasons.add(reason);
        }

        void setCacheBroken(boolean broken) {
            this.cacheBroken = broken;
        }
    }
}
