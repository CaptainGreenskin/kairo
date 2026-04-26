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
package io.kairo.core.model.openai;

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
 * SSE subscriber for OpenAI streaming responses.
 *
 * <p>Accumulates text deltas and tool call deltas from the SSE stream, emitting partial {@link
 * ModelResponse} objects for text chunks, and a final aggregated response on {@code [DONE]}.
 */
public class OpenAISseSubscriber
        implements Flow.Subscriber<String>, ProviderPipeline.StreamSubscriber<String> {

    private static final Logger log = LoggerFactory.getLogger(OpenAISseSubscriber.class);

    private final Sinks.Many<ModelResponse> sink;
    private final ObjectMapper objectMapper;
    private Flow.Subscription subscription;

    private String responseId;
    private String responseModel;
    private final StringBuilder textAccumulator = new StringBuilder();
    private final List<ToolCallAccumulator> toolAccumulators = new ArrayList<>();
    private ModelResponse.StopReason stopReason;

    public OpenAISseSubscriber(Sinks.Many<ModelResponse> sink, ObjectMapper objectMapper) {
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
                args = argsStr.isBlank() ? Map.of() : objectMapper.readValue(argsStr, Map.class);
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
