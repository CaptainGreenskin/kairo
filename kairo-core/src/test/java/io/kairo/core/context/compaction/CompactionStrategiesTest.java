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
package io.kairo.core.context.compaction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.kairo.api.context.CompactionConfig;
import io.kairo.api.context.ContextState;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Comprehensive unit tests for all 5 compaction strategies:
 * SnipCompaction, MicroCompaction, CollapseCompaction, AutoCompaction, PartialCompaction.
 */
class CompactionStrategiesTest {

    private static final CompactionConfig DEFAULT_CONFIG =
            new CompactionConfig(100_000, true, null);

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private static Msg userMsg(String id, String text, int tokens) {
        return Msg.builder()
                .id(id)
                .role(MsgRole.USER)
                .addContent(new Content.TextContent(text))
                .tokenCount(tokens)
                .build();
    }

    private static Msg assistantMsg(String id, String text, int tokens) {
        return Msg.builder()
                .id(id)
                .role(MsgRole.ASSISTANT)
                .addContent(new Content.TextContent(text))
                .tokenCount(tokens)
                .build();
    }

    private static Msg systemMsg(String id, String text, int tokens) {
        return Msg.builder()
                .id(id)
                .role(MsgRole.SYSTEM)
                .addContent(new Content.TextContent(text))
                .tokenCount(tokens)
                .build();
    }

    private static Msg toolResultMsg(String id, String toolUseId, String content, int tokens) {
        return Msg.builder()
                .id(id)
                .role(MsgRole.TOOL)
                .addContent(new Content.ToolResultContent(toolUseId, content, false))
                .tokenCount(tokens)
                .build();
    }

    private static Msg toolUseMsg(String id, String toolName, int tokens) {
        return Msg.builder()
                .id(id)
                .role(MsgRole.ASSISTANT)
                .addContent(
                        new Content.ToolUseContent(
                                "call-" + id, toolName, Map.of("path", "/tmp/test")))
                .tokenCount(tokens)
                .build();
    }

    private static Msg verbatimMsg(String id, String text, int tokens) {
        return Msg.builder()
                .id(id)
                .role(MsgRole.USER)
                .addContent(new Content.TextContent(text))
                .tokenCount(tokens)
                .verbatimPreserved(true)
                .build();
    }

    private static Msg thinkingMsg(String id, String thinkingText, int tokens) {
        return Msg.builder()
                .id(id)
                .role(MsgRole.ASSISTANT)
                .addContent(new Content.ThinkingContent(thinkingText, 1024))
                .addContent(new Content.TextContent("Result after thinking"))
                .tokenCount(tokens)
                .build();
    }

    /** Build a realistic conversation with system, user, assistant, tool_use, tool_result msgs. */
    private static List<Msg> realisticConversation(int toolRoundTrips) {
        List<Msg> msgs = new ArrayList<>();
        msgs.add(systemMsg("sys-1", "You are a helpful coding assistant.", 50));
        msgs.add(userMsg("u-1", "Please read the file /src/main/App.java", 20));

        for (int i = 0; i < toolRoundTrips; i++) {
            msgs.add(toolUseMsg("tu-" + i, "read_file", 30));
            msgs.add(
                    toolResultMsg(
                            "tr-" + i,
                            "call-tu-" + i,
                            "public class App { public static void main(String[] args) {"
                                    + " System.out.println(\"Hello world " + i + "\"); } }",
                            500));
            msgs.add(
                    assistantMsg(
                            "a-" + i,
                            "I've read file " + i + ". The file contains a main method.",
                            40));
        }

        msgs.add(userMsg("u-final", "Now refactor the code", 15));
        return msgs;
    }

    private static int totalTokens(List<Msg> msgs) {
        return msgs.stream().mapToInt(Msg::tokenCount).sum();
    }

    private static ModelProvider mockProvider(String summaryText) {
        ModelProvider provider = mock(ModelProvider.class);
        when(provider.name()).thenReturn("anthropic");
        ModelResponse response =
                new ModelResponse(
                        "resp-1",
                        List.of(new Content.TextContent(summaryText)),
                        new ModelResponse.Usage(1000, 500, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "claude-sonnet-4-20250514");
        when(provider.call(any(), any())).thenReturn(Mono.just(response));
        return provider;
    }

    // =========================================================================
    // SnipCompaction Tests
    // =========================================================================

    @Nested
    @DisplayName("SnipCompaction")
    class SnipCompactionTests {

        private final SnipCompaction strategy = new SnipCompaction();

        @Test
        @DisplayName("shouldTrigger at 80% pressure (percentage-only, no context window)")
        void testShouldTriggerThreshold() {
            assertFalse(strategy.shouldTrigger(new ContextState(0, 0, 0.79f, 10)));
            assertTrue(strategy.shouldTrigger(new ContextState(0, 0, 0.80f, 10)));
            assertTrue(strategy.shouldTrigger(new ContextState(0, 0, 0.90f, 10)));
        }

        @Test
        @DisplayName("Snip reduces total token count when there are old tool results")
        void testSnipCompactionReducesLength() {
            List<Msg> msgs = realisticConversation(8);
            int originalTokens = totalTokens(msgs);

            StepVerifier.create(strategy.compact(msgs, DEFAULT_CONFIG))
                    .assertNext(
                            result -> {
                                assertTrue(
                                        totalTokens(result.compactedMessages()) < originalTokens,
                                        "Snip should reduce total tokens");
                                assertTrue(result.tokensSaved() > 0);
                                assertNotNull(result.marker());
                                assertEquals("snip", result.marker().strategyName());
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Snipped messages preserve structure (same count, role)")
        void testSnipCompactionPreservesMessageStructure() {
            List<Msg> msgs = realisticConversation(8);

            StepVerifier.create(strategy.compact(msgs, DEFAULT_CONFIG))
                    .assertNext(
                            result -> {
                                List<Msg> compacted = result.compactedMessages();
                                assertEquals(msgs.size(), compacted.size(),
                                        "Snip replaces content, not removes messages");
                                for (Msg m : compacted) {
                                    assertFalse(m.contents().isEmpty());
                                    assertNotNull(m.role());
                                }
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Recent 5 tool results are preserved intact")
        void testSnipPreservesRecentToolResults() {
            List<Msg> msgs = realisticConversation(10);

            StepVerifier.create(strategy.compact(msgs, DEFAULT_CONFIG))
                    .assertNext(
                            result -> {
                                List<Msg> compacted = result.compactedMessages();
                                long snippedCount =
                                        compacted.stream()
                                                .filter(m -> m.metadata().containsKey("snipped"))
                                                .count();
                                // 10 tool results, preserve last 5 -> 5 snipped
                                assertEquals(5, snippedCount);
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Thinking content is compressed in non-recent messages")
        void testSnipCompressesThinkingContent() {
            List<Msg> msgs = new ArrayList<>();
            msgs.add(systemMsg("sys", "System prompt", 20));
            msgs.add(userMsg("u1", "Question", 10));
            msgs.add(thinkingMsg("think-1", "Let me analyze this step by step carefully", 200));
            msgs.add(userMsg("u2", "Follow up", 10));
            msgs.add(thinkingMsg("think-2", "Another deep analysis of the problem", 200));
            msgs.add(userMsg("u3", "More", 10));
            msgs.add(assistantMsg("a3", "Reply", 10));
            msgs.add(userMsg("u4", "Final", 10));

            StepVerifier.create(strategy.compact(msgs, DEFAULT_CONFIG))
                    .assertNext(
                            result -> {
                                int newTokens = totalTokens(result.compactedMessages());
                                int origTokens = totalTokens(msgs);
                                assertTrue(newTokens <= origTokens,
                                        "Thinking content should be compressed");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Empty context handled gracefully")
        void testSnipWithEmptyContext() {
            StepVerifier.create(strategy.compact(List.of(), DEFAULT_CONFIG))
                    .assertNext(
                            result -> {
                                assertTrue(result.compactedMessages().isEmpty());
                                assertEquals(0, result.tokensSaved());
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Single message handled gracefully")
        void testSnipWithSingleMessage() {
            List<Msg> msgs = List.of(userMsg("u1", "Hello", 10));

            StepVerifier.create(strategy.compact(msgs, DEFAULT_CONFIG))
                    .assertNext(
                            result -> {
                                assertEquals(1, result.compactedMessages().size());
                                assertEquals(0, result.tokensSaved());
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Priority is 100 and name is 'snip'")
        void testMetadata() {
            assertEquals(100, strategy.priority());
            assertEquals("snip", strategy.name());
        }
    }

    // =========================================================================
    // MicroCompaction Tests
    // =========================================================================

    @Nested
    @DisplayName("MicroCompaction")
    class MicroCompactionTests {

        private final MicroCompaction strategy = new MicroCompaction();

        @Test
        @DisplayName("shouldTrigger at 85% pressure (percentage-only, no context window)")
        void testShouldTriggerThreshold() {
            assertFalse(strategy.shouldTrigger(new ContextState(0, 0, 0.84f, 10)));
            assertTrue(strategy.shouldTrigger(new ContextState(0, 0, 0.85f, 10)));
        }

        @Test
        @DisplayName("Micro compaction reduces token count for tool results")
        void testMicroCompactionReducesLength() {
            List<Msg> msgs = realisticConversation(5);
            int originalTokens = totalTokens(msgs);

            StepVerifier.create(strategy.compact(msgs, DEFAULT_CONFIG))
                    .assertNext(
                            result -> {
                                assertTrue(
                                        totalTokens(result.compactedMessages()) < originalTokens);
                                assertTrue(result.tokensSaved() > 0);
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Micro compaction keeps ToolResultContent type in tool messages")
        void testMicroCompactionMinimalImpact() {
            List<Msg> msgs = realisticConversation(3);

            StepVerifier.create(strategy.compact(msgs, DEFAULT_CONFIG))
                    .assertNext(
                            result -> {
                                List<Msg> compacted = result.compactedMessages();
                                assertEquals(msgs.size(), compacted.size());
                                for (Msg m : compacted) {
                                    if (m.role() == MsgRole.TOOL) {
                                        assertTrue(
                                                m.contents().stream()
                                                        .anyMatch(
                                                                c ->
                                                                        c instanceof Content.ToolResultContent),
                                                "Tool messages must retain ToolResultContent");
                                    }
                                }
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Compacted tool results contain summary format [Result: ...]")
        void testMicroCompactionSummaryFormat() {
            List<Msg> msgs = List.of(
                    toolResultMsg("tr-1", "call-1", "Very long output from tool execution...", 500));

            StepVerifier.create(strategy.compact(msgs, DEFAULT_CONFIG))
                    .assertNext(
                            result -> {
                                Msg compacted = result.compactedMessages().get(0);
                                Content.ToolResultContent trc =
                                        compacted.contents().stream()
                                                .filter(Content.ToolResultContent.class::isInstance)
                                                .map(Content.ToolResultContent.class::cast)
                                                .findFirst()
                                                .orElseThrow();
                                assertTrue(trc.content().startsWith("[Result:"));
                                assertTrue(trc.content().contains("success"));
                                assertTrue(trc.content().contains("bytes]"));
                                assertTrue(compacted.metadata().containsKey("micro-compacted"));
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Error tool results preserve error status")
        void testMicroCompactionPreservesErrorStatus() {
            Msg errorResult =
                    Msg.builder()
                            .id("err-1")
                            .role(MsgRole.TOOL)
                            .addContent(
                                    new Content.ToolResultContent(
                                            "call-err", "FileNotFoundException: not found", true))
                            .tokenCount(100)
                            .build();

            StepVerifier.create(strategy.compact(List.of(errorResult), DEFAULT_CONFIG))
                    .assertNext(
                            result -> {
                                Content.ToolResultContent trc =
                                        result.compactedMessages().get(0).contents().stream()
                                                .filter(Content.ToolResultContent.class::isInstance)
                                                .map(Content.ToolResultContent.class::cast)
                                                .findFirst()
                                                .orElseThrow();
                                assertTrue(trc.content().contains("error"));
                                assertTrue(trc.isError());
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Empty context handled gracefully")
        void testMicroWithEmptyContext() {
            StepVerifier.create(strategy.compact(List.of(), DEFAULT_CONFIG))
                    .assertNext(
                            result -> {
                                assertTrue(result.compactedMessages().isEmpty());
                                assertEquals(0, result.tokensSaved());
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Single non-tool message unchanged")
        void testMicroWithSingleMessage() {
            Msg single = userMsg("u1", "Hello world", 10);

            StepVerifier.create(strategy.compact(List.of(single), DEFAULT_CONFIG))
                    .assertNext(
                            result -> {
                                assertEquals(1, result.compactedMessages().size());
                                assertEquals("Hello world",
                                        result.compactedMessages().get(0).text());
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Priority is 200 and name is 'micro'")
        void testMetadata() {
            assertEquals(200, strategy.priority());
            assertEquals("micro", strategy.name());
        }
    }

    // =========================================================================
    // CollapseCompaction Tests
    // =========================================================================

    @Nested
    @DisplayName("CollapseCompaction")
    class CollapseCompactionTests {

        private final CollapseCompaction strategy = new CollapseCompaction();

        @Test
        @DisplayName("shouldTrigger at 90% pressure (percentage-only, no context window)")
        void testShouldTriggerThreshold() {
            assertFalse(strategy.shouldTrigger(new ContextState(0, 0, 0.89f, 10)));
            assertTrue(strategy.shouldTrigger(new ContextState(0, 0, 0.90f, 10)));
        }

        @Test
        @DisplayName("Collapse reduces message count for consecutive tool groups >= 3")
        void testCollapseCompactionReducesLength() {
            List<Msg> msgs = new ArrayList<>();
            msgs.add(userMsg("u1", "Do several file operations", 20));
            for (int i = 0; i < 5; i++) {
                msgs.add(toolUseMsg("tu-" + i, "read_file", 30));
                msgs.add(toolResultMsg("tr-" + i, "call-tu-" + i, "File content " + i, 200));
            }
            msgs.add(assistantMsg("a1", "Done with file operations", 15));

            int originalTokens = totalTokens(msgs);

            StepVerifier.create(strategy.compact(msgs, DEFAULT_CONFIG))
                    .assertNext(
                            result -> {
                                assertTrue(result.compactedMessages().size() < msgs.size());
                                assertTrue(
                                        totalTokens(result.compactedMessages()) < originalTokens);
                                assertTrue(result.tokensSaved() > 0);
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Collapsed summary contains tool names and success/error counts")
        void testCollapseCompactionMergesRelatedMessages() {
            List<Msg> msgs = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                msgs.add(toolUseMsg("tu-" + i, "read_file", 20));
                msgs.add(toolResultMsg("tr-" + i, "call-tu-" + i, "content " + i, 100));
            }

            StepVerifier.create(strategy.compact(msgs, DEFAULT_CONFIG))
                    .assertNext(
                            result -> {
                                assertEquals(1, result.compactedMessages().size());
                                Msg collapsed = result.compactedMessages().get(0);
                                assertTrue(collapsed.text().contains("[Collapsed:"));
                                assertTrue(collapsed.text().contains("read_file"));
                                assertTrue(collapsed.text().contains("all successful"));
                                assertTrue(collapsed.metadata().containsKey("collapsed"));
                                assertEquals(6, collapsed.metadata().get("collapsed-count"));
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Groups smaller than MIN_GROUP_SIZE (3) are kept as-is")
        void testCollapseSmallGroupKeptIntact() {
            List<Msg> msgs = List.of(
                    toolUseMsg("tu-0", "write_file", 20),
                    toolResultMsg("tr-0", "call-tu-0", "ok", 10));

            StepVerifier.create(strategy.compact(msgs, DEFAULT_CONFIG))
                    .assertNext(
                            result -> {
                                assertEquals(2, result.compactedMessages().size());
                                assertEquals(0, result.tokensSaved());
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Error tool results are counted in the summary")
        void testCollapseWithErrors() {
            List<Msg> msgs = new ArrayList<>();
            msgs.add(toolUseMsg("tu-0", "read_file", 20));
            msgs.add(toolResultMsg("tr-0", "call-tu-0", "content", 50));
            msgs.add(toolUseMsg("tu-1", "write_file", 20));
            msgs.add(
                    Msg.builder()
                            .id("tr-err")
                            .role(MsgRole.TOOL)
                            .addContent(
                                    new Content.ToolResultContent(
                                            "call-tu-1", "Permission denied", true))
                            .tokenCount(50)
                            .build());

            StepVerifier.create(strategy.compact(msgs, DEFAULT_CONFIG))
                    .assertNext(
                            result -> {
                                assertEquals(1, result.compactedMessages().size());
                                String text = result.compactedMessages().get(0).text();
                                assertTrue(text.contains("1 errors"));
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Non-tool messages between tool groups break the group")
        void testCollapseNonToolBreaksGroup() {
            List<Msg> msgs = new ArrayList<>();
            msgs.add(toolUseMsg("tu-0", "read_file", 20));
            msgs.add(toolResultMsg("tr-0", "call-tu-0", "content", 50));
            msgs.add(userMsg("u1", "What did you find?", 10));
            msgs.add(toolUseMsg("tu-1", "read_file", 20));
            msgs.add(toolResultMsg("tr-1", "call-tu-1", "more content", 50));

            StepVerifier.create(strategy.compact(msgs, DEFAULT_CONFIG))
                    .assertNext(
                            result -> {
                                assertEquals(5, result.compactedMessages().size());
                                assertEquals(0, result.tokensSaved());
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Empty context handled gracefully")
        void testCollapseWithEmptyContext() {
            StepVerifier.create(strategy.compact(List.of(), DEFAULT_CONFIG))
                    .assertNext(
                            result -> {
                                assertTrue(result.compactedMessages().isEmpty());
                                assertEquals(0, result.tokensSaved());
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Single message handled gracefully")
        void testCollapseWithSingleMessage() {
            StepVerifier.create(
                            strategy.compact(
                                    List.of(userMsg("u1", "Hello", 10)), DEFAULT_CONFIG))
                    .assertNext(
                            result -> {
                                assertEquals(1, result.compactedMessages().size());
                                assertEquals(0, result.tokensSaved());
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Priority is 300 and name is 'collapse'")
        void testMetadata() {
            assertEquals(300, strategy.priority());
            assertEquals("collapse", strategy.name());
        }
    }

    // =========================================================================
    // AutoCompaction Tests
    // =========================================================================

    @Nested
    @DisplayName("AutoCompaction")
    class AutoCompactionTests {

        @Test
        @DisplayName("shouldTrigger at 95% pressure with provider present")
        void testAutoCompactionTriggerCondition() {
            AutoCompaction strategy = new AutoCompaction(mockProvider("test"));

            assertFalse(
                    strategy.shouldTrigger(new ContextState(100_000, 94_000, 0.94f, 10)));
            assertTrue(
                    strategy.shouldTrigger(new ContextState(100_000, 95_000, 0.95f, 10)));
            assertTrue(
                    strategy.shouldTrigger(new ContextState(100_000, 99_000, 0.99f, 10)));
        }

        @Test
        @DisplayName("shouldTrigger returns false when no ModelProvider")
        void testAutoCompactionNoTriggerBelowThreshold() {
            AutoCompaction strategy = new AutoCompaction(null);
            assertFalse(
                    strategy.shouldTrigger(new ContextState(100_000, 99_000, 0.99f, 10)));
        }

        @Test
        @DisplayName("Preserves system messages and last 3 non-system messages")
        void testAutoCompactionPreservesRecentMessages() {
            AutoCompaction strategy =
                    new AutoCompaction(mockProvider("Summary of conversation"));

            List<Msg> msgs = new ArrayList<>();
            msgs.add(systemMsg("sys-1", "System prompt", 50));
            for (int i = 0; i < 10; i++) {
                msgs.add(
                        (i % 2 == 0)
                                ? userMsg("u-" + i, "User message " + i, 100)
                                : assistantMsg("a-" + i, "Assistant message " + i, 100));
            }

            StepVerifier.create(strategy.compact(msgs, DEFAULT_CONFIG))
                    .assertNext(
                            result -> {
                                List<Msg> compacted = result.compactedMessages();
                                // System messages preserved
                                long systemCount =
                                        compacted.stream()
                                                .filter(m -> m.role() == MsgRole.SYSTEM)
                                                .count();
                                assertTrue(systemCount >= 1, "System msg + summary msg");

                                // Summary message present
                                assertTrue(
                                        compacted.stream()
                                                .anyMatch(
                                                        m ->
                                                                m.text()
                                                                        .contains(
                                                                                "[Auto-compacted summary]")));
                                // Fewer messages than original
                                assertTrue(compacted.size() < msgs.size());
                                assertTrue(result.tokensSaved() > 0);
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("compact without provider returns original unchanged")
        void testAutoCompactionWithoutProvider() {
            AutoCompaction strategy = new AutoCompaction(null);
            List<Msg> msgs = realisticConversation(3);

            StepVerifier.create(strategy.compact(msgs, DEFAULT_CONFIG))
                    .assertNext(
                            result -> {
                                assertEquals(msgs, result.compactedMessages());
                                assertEquals(0, result.tokensSaved());
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Empty context with provider handled gracefully")
        void testAutoWithEmptyContext() {
            AutoCompaction strategy = new AutoCompaction(mockProvider("empty summary"));

            StepVerifier.create(strategy.compact(List.of(), DEFAULT_CONFIG))
                    .assertNext(result -> assertNotNull(result.compactedMessages()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Priority is 400 and name is 'auto'")
        void testMetadata() {
            AutoCompaction strategy = new AutoCompaction(null);
            assertEquals(400, strategy.priority());
            assertEquals("auto", strategy.name());
        }
    }

    // =========================================================================
    // PartialCompaction Tests
    // =========================================================================

    @Nested
    @DisplayName("PartialCompaction")
    class PartialCompactionTests {

        private final PartialCompaction strategy = new PartialCompaction();

        @Test
        @DisplayName("shouldTrigger at 98% pressure (percentage-only, no context window)")
        void testShouldTriggerThreshold() {
            assertFalse(strategy.shouldTrigger(new ContextState(0, 0, 0.97f, 10)));
            assertTrue(strategy.shouldTrigger(new ContextState(0, 0, 0.98f, 10)));
        }

        @Test
        @DisplayName("Partial compaction reduces total tokens with enough messages")
        void testPartialCompactionReducesLength() {
            List<Msg> msgs = realisticConversation(10);
            int originalTokens = totalTokens(msgs);

            StepVerifier.create(strategy.compact(msgs, DEFAULT_CONFIG))
                    .assertNext(
                            result -> {
                                assertTrue(
                                        totalTokens(result.compactedMessages()) < originalTokens);
                                assertTrue(result.tokensSaved() > 0);
                                assertEquals("partial", result.marker().strategyName());
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Verbatim-preserved messages survive partial compaction (Facts First)")
        void testPartialCompactionPreservesFactsFirst() {
            List<Msg> msgs = new ArrayList<>();
            msgs.add(systemMsg("sys", "System prompt", 50));
            for (int i = 0; i < 10; i++) {
                msgs.add(userMsg("u-" + i, "Regular message " + i, 100));
            }
            msgs.add(verbatimMsg("v-1", "CRITICAL: This must be preserved", 100));
            for (int i = 10; i < 15; i++) {
                msgs.add(assistantMsg("a-" + i, "Reply " + i, 50));
            }

            StepVerifier.create(strategy.compact(msgs, DEFAULT_CONFIG))
                    .assertNext(
                            result -> {
                                List<Msg> compacted = result.compactedMessages();
                                // Verbatim message must be in the result
                                assertTrue(
                                        compacted.stream().anyMatch(m -> "v-1".equals(m.id())),
                                        "Verbatim message must survive compaction");
                                // System message must be in the result
                                assertTrue(
                                        compacted.stream()
                                                .anyMatch(m -> m.role() == MsgRole.SYSTEM));
                                // Result is smaller than original
                                assertTrue(compacted.size() < msgs.size());
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Last 5 non-system, non-verbatim messages preserved as tail")
        void testPartialCompactionPreservesTail() {
            List<Msg> msgs = new ArrayList<>();
            msgs.add(systemMsg("sys", "System", 20));
            for (int i = 0; i < 15; i++) {
                msgs.add(userMsg("u-" + i, "Message " + i, 100));
            }

            StepVerifier.create(strategy.compact(msgs, DEFAULT_CONFIG))
                    .assertNext(
                            result -> {
                                List<Msg> compacted = result.compactedMessages();
                                List<String> userIds =
                                        compacted.stream()
                                                .filter(m -> m.role() == MsgRole.USER)
                                                .map(Msg::id)
                                                .toList();
                                assertTrue(userIds.contains("u-10"));
                                assertTrue(userIds.contains("u-11"));
                                assertTrue(userIds.contains("u-12"));
                                assertTrue(userIds.contains("u-13"));
                                assertTrue(userIds.contains("u-14"));
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("UP_TO direction compresses messages before a boundary marker")
        void testPartialCompactionUpTo() {
            List<Msg> msgs = new ArrayList<>();
            msgs.add(systemMsg("sys", "System prompt", 20));
            for (int i = 0; i < 5; i++) {
                msgs.add(userMsg("u-" + i, "Old message " + i, 200));
            }
            msgs.add(userMsg("marker-id", "Marker point", 20));
            for (int i = 5; i < 8; i++) {
                msgs.add(userMsg("u-" + i, "New message " + i, 50));
            }

            CompactionConfig upToConfig =
                    new CompactionConfig(
                            100_000,
                            true,
                            null,
                            CompactionConfig.PartialDirection.UP_TO,
                            "marker-id");

            StepVerifier.create(strategy.compact(msgs, upToConfig))
                    .assertNext(
                            result -> {
                                List<Msg> compacted = result.compactedMessages();
                                assertTrue(
                                        compacted.stream()
                                                .anyMatch(m -> "marker-id".equals(m.id())),
                                        "Marker and beyond must be preserved");
                                assertTrue(result.tokensSaved() > 0);
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Summary message is added for compressed middle section")
        void testPartialCompactionAddsSummary() {
            List<Msg> msgs = new ArrayList<>();
            msgs.add(systemMsg("sys", "System", 20));
            for (int i = 0; i < 20; i++) {
                msgs.add(userMsg("u-" + i, "Message " + i, 100));
            }

            StepVerifier.create(strategy.compact(msgs, DEFAULT_CONFIG))
                    .assertNext(
                            result -> {
                                boolean hasSummary =
                                        result.compactedMessages().stream()
                                                .anyMatch(
                                                        m ->
                                                                m.text()
                                                                        .contains(
                                                                                "[Partial compaction:"));
                                assertTrue(hasSummary, "Should contain partial compaction summary");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Empty context handled gracefully")
        void testPartialWithEmptyContext() {
            StepVerifier.create(strategy.compact(List.of(), DEFAULT_CONFIG))
                    .assertNext(
                            result -> {
                                assertTrue(result.compactedMessages().isEmpty());
                                assertEquals(0, result.tokensSaved());
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Single message handled gracefully")
        void testPartialWithSingleMessage() {
            StepVerifier.create(
                            strategy.compact(
                                    List.of(userMsg("u1", "Only message", 10)), DEFAULT_CONFIG))
                    .assertNext(
                            result -> assertEquals(1, result.compactedMessages().size()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Priority is 500 and name is 'partial'")
        void testMetadata() {
            assertEquals(500, strategy.priority());
            assertEquals("partial", strategy.name());
        }
    }

    // =========================================================================
    // Cross-cutting: HybridThreshold with context window
    // =========================================================================

    @Nested
    @DisplayName("HybridThreshold with context window")
    class HybridThresholdTests {

        @Test
        @DisplayName("With context window, absolute buffer can lower effective threshold")
        void testHybridThresholdWithContextWindow() {
            SnipCompaction snip = new SnipCompaction(); // 80% + 40k buffer
            // 200k context: 80%=160k, abs=200k-40k=160k -> effective=160k
            assertTrue(
                    snip.shouldTrigger(new ContextState(200_000, 160_000, 0.80f, 50, 200_000)));
            assertFalse(
                    snip.shouldTrigger(new ContextState(200_000, 150_000, 0.75f, 50, 200_000)));
        }

        @Test
        @DisplayName("Small context window: absolute buffer dominates")
        void testSmallContextWindow() {
            CollapseCompaction collapse = new CollapseCompaction(); // 90% + 20k buffer
            // 50k window: 90%=45k, abs=50k-20k=30k -> effective=30k
            assertTrue(
                    collapse.shouldTrigger(new ContextState(50_000, 31_000, 0.62f, 20, 50_000)));
        }
    }
}
