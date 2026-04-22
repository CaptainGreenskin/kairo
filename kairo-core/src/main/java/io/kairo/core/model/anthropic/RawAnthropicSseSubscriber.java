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
import io.kairo.api.model.StreamChunk;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Flow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Sinks;

/**
 * A line-based SSE subscriber that emits raw {@link StreamChunk} objects for incremental
 * processing.
 */
public class RawAnthropicSseSubscriber implements Flow.Subscriber<String> {

    private static final Logger log = LoggerFactory.getLogger(RawAnthropicSseSubscriber.class);

    private final Sinks.Many<StreamChunk> sink;
    private final ObjectMapper objectMapper;
    private Flow.Subscription subscription;

    // Track current content block index to tool-call-id mapping
    private final Map<Integer, String> blockToolIds = new HashMap<>();
    private final Map<Integer, String> blockTypes = new HashMap<>();

    public RawAnthropicSseSubscriber(Sinks.Many<StreamChunk> sink, ObjectMapper objectMapper) {
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
            log.warn("Failed to parse SSE event: {}", data, e);
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
