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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * {@link ModelProvider} implementation for OpenAI Chat Completions API.
 *
 * <p>Supports OpenAI and all compatible APIs (DeepSeek, Together, Groq, etc.) through configurable
 * base URL.
 */
public class OpenAIProvider implements ModelProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAIProvider.class);
    private static final String DEFAULT_BASE_URL = "https://api.openai.com";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

    private final String apiKey;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String chatCompletionsPath;
    private final ObjectMapper objectMapper;
    private final ToolDescriptionAdapter toolAdapter = new ToolDescriptionAdapter();

    /**
     * Create an OpenAIProvider with default settings.
     *
     * @param apiKey the OpenAI API key
     */
    public OpenAIProvider(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL);
    }

    /**
     * Create an OpenAIProvider with a custom base URL for compatible APIs.
     *
     * @param apiKey the API key
     * @param baseUrl the base URL (e.g. "https://api.deepseek.com")
     */
    public OpenAIProvider(String apiKey, String baseUrl) {
        this(apiKey, baseUrl, "/v1/chat/completions");
    }

    /**
     * Create an OpenAIProvider with a custom base URL and chat completions path.
     *
     * @param apiKey the API key
     * @param baseUrl the base URL (e.g. "https://open.bigmodel.cn/api/paas/v4")
     * @param chatCompletionsPath the path for chat completions (e.g. "/chat/completions")
     */
    public OpenAIProvider(String apiKey, String baseUrl, String chatCompletionsPath) {
        this(
                apiKey,
                baseUrl,
                chatCompletionsPath,
                ModelProviderUtils.createHttpClient(Duration.ofSeconds(30)));
    }

    /**
     * Create an OpenAIProvider with full customization.
     *
     * @param apiKey the API key
     * @param baseUrl the base URL
     * @param httpClient the HTTP client
     */
    /**
     * Create an OpenAIProvider with full customization.
     *
     * @param apiKey the API key
     * @param baseUrl the base URL
     * @param chatCompletionsPath the path for chat completions
     * @param httpClient the HTTP client
     */
    public OpenAIProvider(
            String apiKey, String baseUrl, String chatCompletionsPath, HttpClient httpClient) {
        ModelProviderUtils.validateApiKey(apiKey, "OpenAI");
        ModelProviderUtils.validateBaseUrl(baseUrl, "OpenAI");
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.chatCompletionsPath = chatCompletionsPath;
        this.httpClient = httpClient;
        this.objectMapper = ModelProviderUtils.createObjectMapper();
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public Mono<ModelResponse> call(List<Msg> messages, ModelConfig config) {
        return Mono.fromCallable(() -> buildRequestBody(messages, config, false))
                .flatMap(
                        body -> {
                            HttpRequest request = buildHttpRequest(body);
                            return Mono.fromFuture(
                                    () ->
                                            httpClient.sendAsync(
                                                    request, HttpResponse.BodyHandlers.ofString()));
                        })
                .flatMap(
                        response -> {
                            if (response.statusCode() == 429) {
                                String retryAfter =
                                        response.headers().firstValue("retry-after").orElse(null);
                                Long retryAfterSec = null;
                                if (retryAfter != null) {
                                    try {
                                        retryAfterSec = Long.parseLong(retryAfter.trim());
                                    } catch (NumberFormatException ignored) {
                                    }
                                }
                                return Mono.error(
                                        new AnthropicProvider.RateLimitException(
                                                "OpenAI API rate limited (429)", retryAfterSec));
                            }
                            if (response.statusCode() != 200) {
                                return Mono.error(
                                        new AnthropicProvider.ApiException(
                                                "OpenAI API error: HTTP "
                                                        + response.statusCode()
                                                        + " - "
                                                        + ModelProviderUtils.sanitizeForLogging(response.body())));
                            }
                            try {
                                return Mono.just(parseResponse(response.body()));
                            } catch (Exception e) {
                                return Mono.error(
                                        new AnthropicProvider.ApiException(
                                                "Failed to parse OpenAI response", e));
                            }
                        })
                .retryWhen(
                        reactor.util.retry.Retry.backoff(3, Duration.ofSeconds(1))
                                .filter(AnthropicProvider.RateLimitException.class::isInstance))
                .timeout(DEFAULT_TIMEOUT);
    }

    @Override
    public Flux<ModelResponse> stream(List<Msg> messages, ModelConfig config) {
        return Flux.defer(
                        () -> {
                            try {
                                String body = buildRequestBody(messages, config, true);
                                HttpRequest request = buildHttpRequest(body);

                                Sinks.Many<ModelResponse> sink =
                                        Sinks.many().unicast().onBackpressureBuffer();

                                httpClient
                                        .sendAsync(
                                                request,
                                                HttpResponse.BodyHandlers.fromLineSubscriber(
                                                        new OpenAISseSubscriber(
                                                                sink, objectMapper)))
                                        .whenComplete(
                                                (resp, err) -> {
                                                    if (err != null) {
                                                        sink.tryEmitError(
                                                                new AnthropicProvider.ApiException(
                                                                        "Streaming request failed",
                                                                        err));
                                                    }
                                                });

                                return sink.asFlux();
                            } catch (Exception e) {
                                return Flux.error(
                                        new AnthropicProvider.ApiException(
                                                "Failed to build streaming request", e));
                            }
                        })
                .timeout(DEFAULT_TIMEOUT);
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
                                HttpRequest request = buildHttpRequest(body);

                                Sinks.Many<StreamChunk> sink =
                                        Sinks.many().unicast().onBackpressureBuffer();

                                httpClient
                                        .sendAsync(
                                                request,
                                                HttpResponse.BodyHandlers.fromLineSubscriber(
                                                        new RawOpenAISseSubscriber(
                                                                sink, objectMapper)))
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
                                        new AnthropicProvider.ApiException(
                                                "Failed to build streaming request", e));
                            }
                        })
                .timeout(DEFAULT_TIMEOUT);
    }

    // ---- Request building ----

    private HttpRequest buildHttpRequest(String jsonBody) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + chatCompletionsPath))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(DEFAULT_TIMEOUT)
                .build();
    }

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

        // Build messages array
        ArrayNode messagesNode = root.putArray("messages");

        // Add system prompt as first message if present
        String systemPrompt = config.systemPrompt();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode sysMsg = objectMapper.createObjectNode();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messagesNode.add(sysMsg);
        }

        for (Msg msg : messages) {
            // OpenAI requires each tool result as a separate message
            if (msg.role() == MsgRole.TOOL) {
                for (Content c : msg.contents()) {
                    if (c instanceof Content.ToolResultContent tr) {
                        ObjectNode toolMsg = objectMapper.createObjectNode();
                        toolMsg.put("role", "tool");
                        toolMsg.put("content", tr.content());
                        toolMsg.put("tool_call_id", tr.toolUseId());
                        messagesNode.add(toolMsg);
                    }
                }
            } else {
                messagesNode.add(convertMsg(msg));
            }
        }

        // Tools — adapt descriptions by model verbosity
        List<ToolDefinition> toolsToSerialize = config.tools();
        ModelCapability capability = ModelCapabilityRegistry.lookup(config.model());
        ToolVerbosity verbosity =
                config.toolVerbosity() != null
                        ? config.toolVerbosity()
                        : capability.toolVerbosity();
        toolsToSerialize = toolAdapter.adaptForModel(toolsToSerialize, verbosity);
        if (toolsToSerialize != null && !toolsToSerialize.isEmpty()) {
            ArrayNode toolsNode = root.putArray("tools");
            for (ToolDefinition tool : toolsToSerialize) {
                toolsNode.add(convertTool(tool));
            }
        }

        return objectMapper.writeValueAsString(root);
    }

    private ObjectNode convertMsg(Msg msg) {
        ObjectNode msgNode = objectMapper.createObjectNode();
        String role =
                switch (msg.role()) {
                    case SYSTEM -> "system";
                    case USER -> "user";
                    case ASSISTANT -> "assistant";
                    case TOOL -> "tool";
                };
        msgNode.put("role", role);

        List<Content> contents = msg.contents();

        // Handle tool result messages
        if (msg.role() == MsgRole.TOOL
                && contents.size() == 1
                && contents.get(0) instanceof Content.ToolResultContent tr) {
            msgNode.put("content", tr.content());
            msgNode.put("tool_call_id", tr.toolUseId());
            return msgNode;
        }

        // Handle assistant messages with tool calls — OpenAI requires content as string (or null) +
        // tool_calls
        if (msg.role() == MsgRole.ASSISTANT) {
            List<Content.ToolUseContent> toolUses =
                    contents.stream()
                            .filter(Content.ToolUseContent.class::isInstance)
                            .map(Content.ToolUseContent.class::cast)
                            .toList();

            // Extract text content (skip ThinkingContent which GLM/OpenAI don't support)
            String textContent =
                    contents.stream()
                            .filter(Content.TextContent.class::isInstance)
                            .map(c -> ((Content.TextContent) c).text())
                            .reduce((a, b) -> a + "\n" + b)
                            .orElse(null);

            if (!toolUses.isEmpty()) {
                // Assistant message with tool calls
                if (textContent != null) {
                    msgNode.put("content", textContent);
                } else {
                    msgNode.putNull("content");
                }
                ArrayNode toolCallsArray = msgNode.putArray("tool_calls");
                for (Content.ToolUseContent tu : toolUses) {
                    ObjectNode tc = objectMapper.createObjectNode();
                    tc.put("id", tu.toolId());
                    tc.put("type", "function");
                    ObjectNode fn = tc.putObject("function");
                    fn.put("name", tu.toolName());
                    try {
                        fn.put("arguments", objectMapper.writeValueAsString(tu.input()));
                    } catch (JsonProcessingException e) {
                        fn.put("arguments", "{}");
                    }
                    toolCallsArray.add(tc);
                }
                return msgNode;
            }

            // Assistant message without tool calls — just text
            if (textContent != null) {
                msgNode.put("content", textContent);
            } else {
                msgNode.put("content", "");
            }
            return msgNode;
        }

        // User/System messages
        if (contents.size() == 1 && contents.get(0) instanceof Content.TextContent tc) {
            msgNode.put("content", tc.text());
        } else {
            ArrayNode contentArray = msgNode.putArray("content");
            for (Content content : contents) {
                if (content instanceof Content.TextContent tc) {
                    ObjectNode n = objectMapper.createObjectNode();
                    n.put("type", "text");
                    n.put("text", tc.text());
                    contentArray.add(n);
                } else if (content instanceof Content.ImageContent ic && ic.url() != null) {
                    ObjectNode n = objectMapper.createObjectNode();
                    n.put("type", "image_url");
                    ObjectNode imageUrl = n.putObject("image_url");
                    imageUrl.put("url", ic.url());
                    contentArray.add(n);
                }
            }
        }
        return msgNode;
    }

    private ObjectNode convertTool(ToolDefinition tool) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "function");
        ObjectNode fn = node.putObject("function");
        fn.put("name", tool.name());
        fn.put("description", tool.description());

        ObjectNode parameters = objectMapper.createObjectNode();
        parameters.put("type", tool.inputSchema().type());
        if (tool.inputSchema().properties() != null) {
            ObjectNode props = parameters.putObject("properties");
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
            ArrayNode reqArray = parameters.putArray("required");
            tool.inputSchema().required().forEach(reqArray::add);
        }
        fn.set("parameters", parameters);
        return node;
    }

    // ---- Response parsing ----

    ModelResponse parseResponse(String responseBody) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        String id = root.path("id").asText();
        String model = root.path("model").asText();

        List<Content> contents = new ArrayList<>();
        JsonNode choices = root.path("choices");
        String finishReason = null;

        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode choice = choices.get(0);
            finishReason = choice.path("finish_reason").asText(null);
            JsonNode message = choice.path("message");

            // Text content
            String textContent = message.path("content").asText(null);
            if (textContent != null) {
                contents.add(new Content.TextContent(textContent));
            }

            // Tool calls
            JsonNode toolCalls = message.path("tool_calls");
            if (toolCalls.isArray()) {
                for (JsonNode tc : toolCalls) {
                    String toolCallId = tc.path("id").asText();
                    JsonNode fn = tc.path("function");
                    String fnName = fn.path("name").asText();
                    String argsStr = fn.path("arguments").asText("{}");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> args = objectMapper.readValue(argsStr, Map.class);
                    contents.add(new Content.ToolUseContent(toolCallId, fnName, args));
                }
            }
        }

        ModelResponse.StopReason stopReason = parseFinishReason(finishReason);

        // Parse usage
        JsonNode usageNode = root.path("usage");
        ModelResponse.Usage usage =
                new ModelResponse.Usage(
                        usageNode.path("prompt_tokens").asInt(0),
                        usageNode.path("completion_tokens").asInt(0),
                        0,
                        0);

        return new ModelResponse(id, contents, usage, stopReason, model);
    }

    private ModelResponse.StopReason parseFinishReason(String reason) {
        if (reason == null) return null;
        return switch (reason) {
            case "stop" -> ModelResponse.StopReason.END_TURN;
            case "tool_calls" -> ModelResponse.StopReason.TOOL_USE;
            case "length" -> ModelResponse.StopReason.MAX_TOKENS;
            default -> ModelResponse.StopReason.END_TURN;
        };
    }

    // ---- SSE stream parsing ----

    /** SSE subscriber for OpenAI streaming responses. */
    static class OpenAISseSubscriber implements Flow.Subscriber<String> {

        private final Sinks.Many<ModelResponse> sink;
        private final ObjectMapper objectMapper;
        private Flow.Subscription subscription;

        private String responseId;
        private String responseModel;
        private final StringBuilder textAccumulator = new StringBuilder();
        private final List<ToolCallAccumulator> toolAccumulators = new ArrayList<>();
        private ModelResponse.StopReason stopReason;

        OpenAISseSubscriber(Sinks.Many<ModelResponse> sink, ObjectMapper objectMapper) {
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
            if (!line.startsWith("data:")) return;

            String data = line.substring(5).trim();
            if ("[DONE]".equals(data)) {
                emitFinalResponse();
                sink.tryEmitComplete();
                return;
            }

            try {
                JsonNode event = objectMapper.readTree(data);
                responseId = event.path("id").asText(responseId);
                responseModel = event.path("model").asText(responseModel);

                JsonNode choices = event.path("choices");
                if (choices.isArray() && !choices.isEmpty()) {
                    JsonNode choice = choices.get(0);
                    String finishReason = choice.path("finish_reason").asText(null);
                    if (finishReason != null) {
                        stopReason =
                                switch (finishReason) {
                                    case "stop" -> ModelResponse.StopReason.END_TURN;
                                    case "tool_calls" -> ModelResponse.StopReason.TOOL_USE;
                                    case "length" -> ModelResponse.StopReason.MAX_TOKENS;
                                    default -> ModelResponse.StopReason.END_TURN;
                                };
                    }

                    JsonNode delta = choice.path("delta");

                    // Text delta
                    String textDelta = delta.path("content").asText(null);
                    if (textDelta != null) {
                        textAccumulator.append(textDelta);
                        // Emit partial text
                        sink.tryEmitNext(
                                new ModelResponse(
                                        responseId,
                                        List.of(new Content.TextContent(textDelta)),
                                        new ModelResponse.Usage(0, 0, 0, 0),
                                        null,
                                        responseModel));
                    }

                    // Tool call deltas
                    JsonNode toolCalls = delta.path("tool_calls");
                    if (toolCalls.isArray()) {
                        for (JsonNode tc : toolCalls) {
                            int idx = tc.path("index").asInt();
                            while (toolAccumulators.size() <= idx) {
                                toolAccumulators.add(new ToolCallAccumulator());
                            }
                            ToolCallAccumulator acc = toolAccumulators.get(idx);
                            String tcId = tc.path("id").asText(null);
                            if (tcId != null) acc.id = tcId;
                            JsonNode fn = tc.path("function");
                            String fnName = fn.path("name").asText(null);
                            if (fnName != null) acc.name = fnName;
                            String fnArgs = fn.path("arguments").asText(null);
                            if (fnArgs != null) acc.arguments.append(fnArgs);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse SSE chunk: {}", data, e);
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

        @SuppressWarnings("unchecked")
        private void emitFinalResponse() {
            List<Content> contents = new ArrayList<>();
            String text = textAccumulator.toString();
            if (!text.isEmpty()) {
                contents.add(new Content.TextContent(text));
            }
            for (ToolCallAccumulator acc : toolAccumulators) {
                Map<String, Object> args;
                try {
                    String argsStr = acc.arguments.toString();
                    args =
                            argsStr.isBlank()
                                    ? Map.of()
                                    : objectMapper.readValue(argsStr, Map.class);
                } catch (Exception e) {
                    args = Map.of();
                }
                contents.add(new Content.ToolUseContent(acc.id, acc.name, args));
            }
            if (!contents.isEmpty()) {
                sink.tryEmitNext(
                        new ModelResponse(
                                responseId,
                                contents,
                                new ModelResponse.Usage(0, 0, 0, 0),
                                stopReason,
                                responseModel));
            }
        }
    }

    static class ToolCallAccumulator {
        String id;
        String name;
        final StringBuilder arguments = new StringBuilder();
    }


    /**
     * A line-based SSE subscriber that emits raw {@link StreamChunk} objects for OpenAI-format
     * streaming responses.
     */
    static class RawOpenAISseSubscriber implements Flow.Subscriber<String> {

        private final Sinks.Many<StreamChunk> sink;
        private final ObjectMapper objectMapper;
        private Flow.Subscription subscription;

        // Track tool calls by index — detect when a tool block is complete
        private final Map<Integer, String> toolIds = new ConcurrentHashMap<>();
        private final Map<Integer, String> toolNames = new ConcurrentHashMap<>();
        private int lastSeenToolIndex = -1;

        RawOpenAISseSubscriber(Sinks.Many<StreamChunk> sink, ObjectMapper objectMapper) {
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
            if (!line.startsWith("data:")) return;

            String data = line.substring(5).trim();
            if ("[DONE]".equals(data)) {
                // End all open tool blocks, then emit DONE
                flushRemainingTools();
                sink.tryEmitNext(StreamChunk.done());
                sink.tryEmitComplete();
                return;
            }

            try {
                JsonNode event = objectMapper.readTree(data);
                JsonNode choices = event.path("choices");
                if (!choices.isArray() || choices.isEmpty()) return;

                JsonNode choice = choices.get(0);
                String finishReason = choice.path("finish_reason").asText(null);
                JsonNode delta = choice.path("delta");

                // Text delta
                String textDelta = delta.path("content").asText(null);
                if (textDelta != null) {
                    sink.tryEmitNext(StreamChunk.text(textDelta));
                }

                // Tool call deltas
                JsonNode toolCalls = delta.path("tool_calls");
                if (toolCalls.isArray()) {
                    for (JsonNode tc : toolCalls) {
                        int idx = tc.path("index").asInt();

                        // If we see a new index, the previous tool block is complete
                        if (idx > lastSeenToolIndex && lastSeenToolIndex >= 0) {
                            String prevId = toolIds.get(lastSeenToolIndex);
                            if (prevId != null) {
                                sink.tryEmitNext(StreamChunk.toolUseEnd(prevId));
                            }
                        }

                        String tcId = tc.path("id").asText(null);
                        if (tcId != null) {
                            toolIds.put(idx, tcId);
                        }

                        JsonNode fn = tc.path("function");
                        String fnName = fn.path("name").asText(null);
                        if (fnName != null) {
                            toolNames.put(idx, fnName);
                            String id = toolIds.getOrDefault(idx, "tool_" + idx);
                            log.debug("toolUseStart id={} name={}", id, fnName);
                            sink.tryEmitNext(StreamChunk.toolUseStart(id, fnName));
                        }

                        String fnArgs = fn.path("arguments").asText(null);
                        if (fnArgs != null) {
                            String id = toolIds.getOrDefault(idx, "tool_" + idx);
                            sink.tryEmitNext(StreamChunk.toolUseDelta(id, fnArgs));
                        }

                        lastSeenToolIndex = Math.max(lastSeenToolIndex, idx);
                    }
                }

                // finish_reason signals end of tool calls
                if ("tool_calls".equals(finishReason)) {
                    flushRemainingTools();
                } else if ("stop".equals(finishReason) || "length".equals(finishReason)) {
                    flushRemainingTools();
                }
            } catch (Exception e) {
                log.warn("Failed to parse SSE chunk: {}", data, e);
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

        /** Emit TOOL_USE_END for ALL pending tool blocks. */
        private void flushRemainingTools() {
            for (var entry : toolIds.entrySet()) {
                sink.tryEmitNext(StreamChunk.toolUseEnd(entry.getValue()));
            }
            toolIds.clear();
            toolNames.clear();
            lastSeenToolIndex = -1;
        }
    }
}
