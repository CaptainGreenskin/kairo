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

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.model.StreamChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class StreamingToolDetectorTest {

    private StreamingToolDetector detector;

    @BeforeEach
    void setUp() {
        detector = new StreamingToolDetector();
    }

    @Test
    void detectsSingleToolFromStream() {
        var chunks =
                Flux.just(
                        StreamChunk.toolUseStart("tc1", "read_file"),
                        StreamChunk.toolUseDelta("tc1", "{\"path\":"),
                        StreamChunk.toolUseDelta("tc1", "\"/tmp/f\"}"),
                        StreamChunk.toolUseEnd("tc1"));

        StepVerifier.create(detector.detect(chunks))
                .assertNext(
                        tool -> {
                            assertEquals("tc1", tool.toolCallId());
                            assertEquals("read_file", tool.toolName());
                            assertEquals("/tmp/f", tool.args().get("path"));
                        })
                .verifyComplete();
    }

    @Test
    void detectsMultipleToolsInSequence() {
        var chunks =
                Flux.just(
                        StreamChunk.toolUseStart("tc1", "read_file"),
                        StreamChunk.toolUseDelta("tc1", "{\"path\":\"/a\"}"),
                        StreamChunk.toolUseEnd("tc1"),
                        StreamChunk.toolUseStart("tc2", "grep"),
                        StreamChunk.toolUseDelta("tc2", "{\"pattern\":\"foo\"}"),
                        StreamChunk.toolUseEnd("tc2"));

        StepVerifier.create(detector.detect(chunks))
                .assertNext(
                        tool -> {
                            assertEquals("tc1", tool.toolCallId());
                            assertEquals("read_file", tool.toolName());
                        })
                .assertNext(
                        tool -> {
                            assertEquals("tc2", tool.toolCallId());
                            assertEquals("grep", tool.toolName());
                        })
                .verifyComplete();
    }

    @Test
    void handlesTextChunksBetweenTools() {
        var chunks =
                Flux.just(
                        StreamChunk.text("Let me read the file."),
                        StreamChunk.toolUseStart("tc1", "read_file"),
                        StreamChunk.toolUseDelta("tc1", "{\"path\":\"/tmp\"}"),
                        StreamChunk.toolUseEnd("tc1"),
                        StreamChunk.text("Now I'll search."));

        StepVerifier.create(detector.detect(chunks))
                .assertNext(tool -> assertEquals("read_file", tool.toolName()))
                .verifyComplete();
    }

    @Test
    void handlesThinkingChunksBetweenTools() {
        var chunks =
                Flux.just(
                        StreamChunk.thinking("I need to read the file..."),
                        StreamChunk.toolUseStart("tc1", "read_file"),
                        StreamChunk.toolUseDelta("tc1", "{\"path\":\"/f\"}"),
                        StreamChunk.toolUseEnd("tc1"),
                        StreamChunk.thinking("Now I should write."));

        StepVerifier.create(detector.detect(chunks))
                .assertNext(tool -> assertEquals("read_file", tool.toolName()))
                .verifyComplete();
    }

    @Test
    void parsesJsonArgsFromDeltas() {
        // Split JSON across many small delta chunks
        var chunks =
                Flux.just(
                        StreamChunk.toolUseStart("tc1", "write_file"),
                        StreamChunk.toolUseDelta("tc1", "{"),
                        StreamChunk.toolUseDelta("tc1", "\"path\""),
                        StreamChunk.toolUseDelta("tc1", ":"),
                        StreamChunk.toolUseDelta("tc1", "\"/tmp/out\""),
                        StreamChunk.toolUseDelta("tc1", ",\"content\":\"hello\""),
                        StreamChunk.toolUseDelta("tc1", "}"),
                        StreamChunk.toolUseEnd("tc1"));

        StepVerifier.create(detector.detect(chunks))
                .assertNext(
                        tool -> {
                            assertEquals("/tmp/out", tool.args().get("path"));
                            assertEquals("hello", tool.args().get("content"));
                        })
                .verifyComplete();
    }

    @Test
    void handlesInvalidJsonArgs() {
        var chunks =
                Flux.just(
                        StreamChunk.toolUseStart("tc1", "broken"),
                        StreamChunk.toolUseDelta("tc1", "not valid json {{{"),
                        StreamChunk.toolUseEnd("tc1"));

        StepVerifier.create(detector.detect(chunks))
                .assertNext(
                        tool -> {
                            assertEquals("broken", tool.toolName());
                            // Invalid JSON should be stored as _raw_json with _parse_error
                            assertTrue(tool.args().containsKey("_raw_json"));
                            assertTrue(tool.args().containsKey("_parse_error"));
                        })
                .verifyComplete();
    }

    @Test
    void emptyStreamProducesNoTools() {
        StepVerifier.create(detector.detect(Flux.empty())).verifyComplete();
    }

    @Test
    void doneChunkSetsIsLastTool() {
        var chunks =
                Flux.just(
                        StreamChunk.toolUseStart("tc1", "read_file"),
                        StreamChunk.toolUseDelta("tc1", "{}"),
                        StreamChunk.done(),
                        StreamChunk.toolUseEnd("tc1"));

        StepVerifier.create(detector.detect(chunks))
                .assertNext(
                        tool -> {
                            assertEquals("read_file", tool.toolName());
                            assertTrue(tool.isLastTool());
                        })
                .verifyComplete();
    }

    @Test
    void toolWithoutDoneIsNotLast() {
        var chunks =
                Flux.just(
                        StreamChunk.toolUseStart("tc1", "read_file"),
                        StreamChunk.toolUseDelta("tc1", "{}"),
                        StreamChunk.toolUseEnd("tc1"));

        StepVerifier.create(detector.detect(chunks))
                .assertNext(tool -> assertFalse(tool.isLastTool()))
                .verifyComplete();
    }

    @Test
    void singleDeltaWithCompleteArgs() {
        var chunks =
                Flux.just(
                        StreamChunk.toolUseStart("tc1", "grep"),
                        StreamChunk.toolUseDelta(
                                "tc1", "{\"pattern\":\"error\",\"path\":\"/var/log\"}"),
                        StreamChunk.toolUseEnd("tc1"));

        StepVerifier.create(detector.detect(chunks))
                .assertNext(
                        tool -> {
                            assertEquals("grep", tool.toolName());
                            assertEquals("error", tool.args().get("pattern"));
                            assertEquals("/var/log", tool.args().get("path"));
                        })
                .verifyComplete();
    }

    @Test
    void toolWithNoArgs() {
        var chunks =
                Flux.just(
                        StreamChunk.toolUseStart("tc1", "list_tools"),
                        StreamChunk.toolUseEnd("tc1"));

        StepVerifier.create(detector.detect(chunks))
                .assertNext(
                        tool -> {
                            assertEquals("list_tools", tool.toolName());
                            assertTrue(tool.args().isEmpty());
                        })
                .verifyComplete();
    }
}
