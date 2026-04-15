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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.model.StreamChunk;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * Incrementally parses a stream of {@link StreamChunk} events to detect complete tool_use blocks.
 *
 * <p>Emits a {@link DetectedToolCall} as soon as a {@code TOOL_USE_END} chunk is received, meaning
 * the tool name and complete JSON args are available for immediate execution.
 */
public class StreamingToolDetector {

    private static final Logger log = LoggerFactory.getLogger(StreamingToolDetector.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Detect complete tool calls from a stream of chunks.
     *
     * <p>Uses {@link Flux#scan} to accumulate state across chunks. A {@link DetectedToolCall} is
     * emitted each time a {@code TOOL_USE_END} chunk completes a tool block.
     *
     * @param chunks the raw streaming chunks from a model provider
     * @return a Flux of detected, ready-to-execute tool calls
     */
    public Flux<DetectedToolCall> detect(Flux<StreamChunk> chunks) {
        return chunks.scan(new DetectorState(), this::accumulate)
                .filter(DetectorState::hasCompleteTool)
                .map(DetectorState::extractAndReset);
    }

    private DetectorState accumulate(DetectorState state, StreamChunk chunk) {
        return switch (chunk.type()) {
            case TOOL_USE_START -> {
                state.startTool(chunk.toolCallId(), chunk.toolName());
                yield state;
            }
            case TOOL_USE_DELTA -> {
                state.appendArgs(chunk.toolCallId(), chunk.content());
                yield state;
            }
            case TOOL_USE_END -> {
                state.completeTool(chunk.toolCallId());
                yield state;
            }
            case DONE -> {
                state.markDone();
                yield state;
            }
            default -> state; // TEXT, THINKING, ERROR — pass through
        };
    }

    /** Mutable accumulator for {@link Flux#scan}. Tracks in-progress and completed tool blocks. */
    private static class DetectorState {
        private final Map<String, String> toolNames = new HashMap<>();
        private final Map<String, StringBuilder> toolArgs = new HashMap<>();
        private DetectedToolCall completedTool = null;
        private boolean streamDone = false;
        private int pendingTools = 0;

        /**
         * The most recently started tool call ID. Used as a fallback when providers (e.g. Qwen /
         * DashScope) send DELTA or END chunks with empty or null tool call IDs.
         */
        private String lastStartedId = null;

        void startTool(String id, String name) {
            toolNames.put(id, name);
            toolArgs.put(id, new StringBuilder());
            lastStartedId = id;
            pendingTools++;
        }

        void appendArgs(String id, String delta) {
            String resolvedId = resolveId(id);
            var sb = toolArgs.get(resolvedId);
            if (sb != null && delta != null) {
                sb.append(delta);
            }
        }

        void completeTool(String id) {
            pendingTools--;
            String resolvedId = resolveId(id);
            String name = toolNames.remove(resolvedId);
            if (name == null) {
                log.warn("Tool completion for unknown id: {} — skipping", resolvedId);
                return; // Don't emit a broken DetectedToolCall
            }
            StringBuilder argsSb = toolArgs.remove(resolvedId);
            Map<String, Object> args = parseArgs(argsSb != null ? argsSb.toString() : "{}");
            completedTool =
                    new DetectedToolCall(
                            resolvedId, name, args, streamDone && pendingTools <= 0);
        }

        /**
         * Resolve a potentially empty/null tool call ID to the last-started tool call. Some
         * providers (Qwen via DashScope) only set the ID on the first chunk.
         */
        private String resolveId(String id) {
            if (id != null && !id.isEmpty()) {
                return id;
            }
            if (lastStartedId != null) {
                log.debug(
                        "Resolved empty tool call ID to last-started: {}", lastStartedId);
            }
            return lastStartedId;
        }

        void markDone() {
            streamDone = true;
        }

        boolean hasCompleteTool() {
            return completedTool != null;
        }

        DetectedToolCall extractAndReset() {
            var tool = completedTool;
            completedTool = null;
            return tool;
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> parseArgs(String json) {
            try {
                if (json == null || json.isBlank()) {
                    return Map.of();
                }
                return MAPPER.readValue(json, Map.class);
            } catch (Exception e) {
                log.warn("Failed to parse tool args: {}", e.getMessage());
                return Map.of("_parse_error", e.getMessage(), "_raw_json", json);
            }
        }
    }
}
