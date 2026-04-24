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
package io.kairo.core.model.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.message.Content;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.model.ProviderPipeline;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Sinks;

/**
 * A line-based subscriber for SSE event streams from the Anthropic API. Parses SSE events and emits
 * {@link ModelResponse} fragments via a Reactor Sink.
 */
public class AnthropicSseSubscriber
        implements Flow.Subscriber<String>, ProviderPipeline.StreamSubscriber<String> {

    private static final Logger log = LoggerFactory.getLogger(AnthropicSseSubscriber.class);

    private final Sinks.Many<ModelResponse> sink;
    private final ObjectMapper objectMapper;
    private Flow.Subscription subscription;

    // Accumulator state for building partial responses
    private String responseId;
    private String responseModel;
    private final List<ContentAccumulator> contentAccumulators = new ArrayList<>();
    private ModelResponse.StopReason stopReason;
    private ModelResponse.Usage usage;

    public AnthropicSseSubscriber(Sinks.Many<ModelResponse> sink, ObjectMapper objectMapper) {
        this.sink = sink;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(Long.MAX_VALUE);
    }

    /** SPI entry point — routes an SSE line to {@link #onNext(String)} for parsing. */
    @Override
    public void onChunk(String chunk) {
        onNext(chunk);
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
            log.warn("Failed to parse SSE event: {}", data, e);
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
            default -> log.debug("Unknown SSE event type: {}", type);
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
}
