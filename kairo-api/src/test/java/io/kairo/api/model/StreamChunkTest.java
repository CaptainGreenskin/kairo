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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StreamChunkTest {

    @Test
    void textChunk() {
        StreamChunk chunk = StreamChunk.text("hello");
        assertEquals(StreamChunkType.TEXT, chunk.type());
        assertEquals("hello", chunk.content());
        assertNull(chunk.toolCallId());
        assertNull(chunk.toolName());
        assertNull(chunk.metadata());
    }

    @Test
    void thinkingChunk() {
        StreamChunk chunk = StreamChunk.thinking("reasoning");
        assertEquals(StreamChunkType.THINKING, chunk.type());
        assertEquals("reasoning", chunk.content());
        assertNull(chunk.toolCallId());
    }

    @Test
    void toolUseStartChunk() {
        StreamChunk chunk = StreamChunk.toolUseStart("call-1", "read_file");
        assertEquals(StreamChunkType.TOOL_USE_START, chunk.type());
        assertNull(chunk.content());
        assertEquals("call-1", chunk.toolCallId());
        assertEquals("read_file", chunk.toolName());
    }

    @Test
    void toolUseDeltaChunk() {
        StreamChunk chunk = StreamChunk.toolUseDelta("call-1", "{\"path\":");
        assertEquals(StreamChunkType.TOOL_USE_DELTA, chunk.type());
        assertEquals("{\"path\":", chunk.content());
        assertEquals("call-1", chunk.toolCallId());
        assertNull(chunk.toolName());
    }

    @Test
    void toolUseEndChunk() {
        StreamChunk chunk = StreamChunk.toolUseEnd("call-1");
        assertEquals(StreamChunkType.TOOL_USE_END, chunk.type());
        assertNull(chunk.content());
        assertEquals("call-1", chunk.toolCallId());
    }

    @Test
    void doneChunk() {
        StreamChunk chunk = StreamChunk.done();
        assertEquals(StreamChunkType.DONE, chunk.type());
        assertNull(chunk.content());
        assertNull(chunk.toolCallId());
        assertNull(chunk.toolName());
    }

    @Test
    void errorChunk() {
        StreamChunk chunk = StreamChunk.error("connection reset");
        assertEquals(StreamChunkType.ERROR, chunk.type());
        assertEquals("connection reset", chunk.content());
        assertNull(chunk.toolCallId());
    }
}
