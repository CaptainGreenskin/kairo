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
package io.kairo.api.model;

import io.kairo.api.Stable;
import java.util.Map;

/**
 * A chunk emitted during streaming model responses.
 *
 * @param type the chunk type
 * @param content text/thinking content, or tool args delta
 * @param toolCallId for TOOL_USE_* types, the tool call ID
 * @param toolName for TOOL_USE_START, the tool name
 * @param metadata additional metadata
 */
@Stable(value = "Streaming chunk record; shape frozen since v0.3", since = "1.0.0")
public record StreamChunk(
        StreamChunkType type,
        String content,
        String toolCallId,
        String toolName,
        Map<String, Object> metadata) {

    /** Create a text content chunk. */
    public static StreamChunk text(String content) {
        return new StreamChunk(StreamChunkType.TEXT, content, null, null, null);
    }

    /** Create a thinking content chunk. */
    public static StreamChunk thinking(String content) {
        return new StreamChunk(StreamChunkType.THINKING, content, null, null, null);
    }

    /** Create a tool use start chunk. */
    public static StreamChunk toolUseStart(String id, String name) {
        return new StreamChunk(StreamChunkType.TOOL_USE_START, null, id, name, null);
    }

    /** Create a tool use argument delta chunk. */
    public static StreamChunk toolUseDelta(String id, String argsDelta) {
        return new StreamChunk(StreamChunkType.TOOL_USE_DELTA, argsDelta, id, null, null);
    }

    /** Create a tool use end chunk. */
    public static StreamChunk toolUseEnd(String id) {
        return new StreamChunk(StreamChunkType.TOOL_USE_END, null, id, null, null);
    }

    /** Create a done chunk. */
    public static StreamChunk done() {
        return new StreamChunk(StreamChunkType.DONE, null, null, null, null);
    }

    /** Create an error chunk. */
    public static StreamChunk error(String message) {
        return new StreamChunk(StreamChunkType.ERROR, message, null, null, null);
    }
}
