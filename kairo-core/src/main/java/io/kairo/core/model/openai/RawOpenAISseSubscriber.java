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
import io.kairo.api.model.StreamChunk;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Sinks;

/**
 * A line-based SSE subscriber that emits raw {@link StreamChunk} objects for OpenAI-format
 * streaming responses.
 *
 * <p>Unlike {@link OpenAISseSubscriber} which accumulates the full response, this subscriber emits
 * each chunk as it arrives for incremental processing.
 */
public class RawOpenAISseSubscriber implements Flow.Subscriber<String> {

    private static final Logger log = LoggerFactory.getLogger(RawOpenAISseSubscriber.class);

    private final Sinks.Many<StreamChunk> sink;
    private final ObjectMapper objectMapper;
    private Flow.Subscription subscription;

    // Track tool calls by index — detect when a tool block is complete
    private final Map<Integer, String> toolIds = new ConcurrentHashMap<>();
    private final Map<Integer, String> toolNames = new ConcurrentHashMap<>();
    private int lastSeenToolIndex = -1;

    public RawOpenAISseSubscriber(Sinks.Many<StreamChunk> sink, ObjectMapper objectMapper) {
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
