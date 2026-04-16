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
    void handlesEmptyToolCallIdFromQwen() {
        // Qwen/DashScope sends real ID only on START; DELTA and END have empty IDs
        var chunks =
                Flux.just(
                        StreamChunk.toolUseStart("call_abc", "read_file"),
                        StreamChunk.toolUseDelta("", "{\"path\":"),
                        StreamChunk.toolUseDelta("", "\"/tmp/f\"}"),
                        StreamChunk.toolUseEnd(""));

        StepVerifier.create(detector.detect(chunks))
                .assertNext(
                        tool -> {
                            assertEquals("call_abc", tool.toolCallId());
                            assertEquals("read_file", tool.toolName());
                            assertEquals("/tmp/f", tool.args().get("path"));
                        })
                .verifyComplete();
    }

    @Test
    void handlesNullToolCallIdFromQwen() {
        // Some providers send null instead of empty string
        var chunks =
                Flux.just(
                        StreamChunk.toolUseStart("call_xyz", "grep"),
                        StreamChunk.toolUseDelta(null, "{\"pattern\":\"err\"}"),
                        StreamChunk.toolUseEnd(null));

        StepVerifier.create(detector.detect(chunks))
                .assertNext(
                        tool -> {
                            assertEquals("call_xyz", tool.toolCallId());
                            assertEquals("grep", tool.toolName());
                            assertEquals("err", tool.args().get("pattern"));
                        })
                .verifyComplete();
    }

    @Test
    void handlesParallelToolCallsWithDuplicateEnds() {
        // Simulates OpenAI-format provider behavior: index-transition emits END for tool 1,
        // then flushRemainingTools emits END for ALL tools (including tool 1 again).
        var chunks =
                Flux.just(
                        StreamChunk.toolUseStart("call_1", "read_file"),
                        StreamChunk.toolUseDelta("call_1", "{\"path\":\"/a\"}"),
                        StreamChunk.toolUseEnd("call_1"), // from index transition
                        StreamChunk.toolUseStart("call_2", "grep"),
                        StreamChunk.toolUseDelta("call_2", "{\"pattern\":\"err\"}"),
                        StreamChunk.toolUseEnd("call_1"), // duplicate from flush
                        StreamChunk.toolUseEnd("call_2")); // from flush

        StepVerifier.create(detector.detect(chunks))
                .assertNext(
                        tool -> {
                            assertEquals("call_1", tool.toolCallId());
                            assertEquals("read_file", tool.toolName());
                            assertEquals("/a", tool.args().get("path"));
                        })
                .assertNext(
                        tool -> {
                            assertEquals("call_2", tool.toolCallId());
                            assertEquals("grep", tool.toolName());
                            assertEquals("err", tool.args().get("pattern"));
                        })
                .verifyComplete();
    }

    @Test
    void handlesThreeParallelToolCalls() {
        // Three parallel tool calls, all flushed at end (triple duplicate ENDs)
        var chunks =
                Flux.just(
                        StreamChunk.toolUseStart("c1", "tool_a"),
                        StreamChunk.toolUseDelta("c1", "{\"x\":1}"),
                        StreamChunk.toolUseEnd("c1"), // index transition
                        StreamChunk.toolUseStart("c2", "tool_b"),
                        StreamChunk.toolUseDelta("c2", "{\"y\":2}"),
                        StreamChunk.toolUseEnd("c2"), // index transition
                        StreamChunk.toolUseStart("c3", "tool_c"),
                        StreamChunk.toolUseDelta("c3", "{\"z\":3}"),
                        // flush emits END for all three (c1, c2 are duplicates)
                        StreamChunk.toolUseEnd("c1"),
                        StreamChunk.toolUseEnd("c2"),
                        StreamChunk.toolUseEnd("c3"));

        StepVerifier.create(detector.detect(chunks))
                .assertNext(tool -> assertEquals("tool_a", tool.toolName()))
                .assertNext(tool -> assertEquals("tool_b", tool.toolName()))
                .assertNext(tool -> assertEquals("tool_c", tool.toolName()))
                .verifyComplete();
    }

    @Test
    void handlesParallelToolsWithEmptyIds() {
        // Qwen sends ID only on START, DELTA/END have empty IDs.
        // With parallel tools, resolve empty IDs to oldest active tool.
        var chunks =
                Flux.just(
                        StreamChunk.toolUseStart("call_a", "read_file"),
                        StreamChunk.toolUseDelta("", "{\"path\":\"/a\"}"),
                        StreamChunk.toolUseEnd(""), // completes call_a (oldest active)
                        StreamChunk.toolUseStart("call_b", "grep"),
                        StreamChunk.toolUseDelta("", "{\"pattern\":\"err\"}"),
                        StreamChunk.toolUseEnd("")); // completes call_b

        StepVerifier.create(detector.detect(chunks))
                .assertNext(
                        tool -> {
                            assertEquals("call_a", tool.toolCallId());
                            assertEquals("read_file", tool.toolName());
                        })
                .assertNext(
                        tool -> {
                            assertEquals("call_b", tool.toolCallId());
                            assertEquals("grep", tool.toolName());
                        })
                .verifyComplete();
    }

    @Test
    void handlesStartToolWithEmptyName() {
        // Qwen may send empty name — should not produce a tool call
        var chunks =
                Flux.just(
                        StreamChunk.toolUseStart("call_ghost", ""),
                        StreamChunk.toolUseDelta("call_ghost", "{}"),
                        StreamChunk.toolUseEnd("call_ghost"));

        StepVerifier.create(detector.detect(chunks)).verifyComplete(); // no tool emitted
    }

    @Test
    void handlesStartToolWithNullId() {
        // startTool with null id should be ignored gracefully
        var chunks =
                Flux.just(
                        StreamChunk.toolUseStart(null, "read_file"),
                        StreamChunk.toolUseDelta(null, "{}"),
                        StreamChunk.toolUseEnd(null));

        StepVerifier.create(detector.detect(chunks)).verifyComplete(); // no tool emitted
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
