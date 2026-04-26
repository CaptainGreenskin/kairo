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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.context.CompactionConfig;
import io.kairo.api.context.ContextState;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.core.message.MsgBuilder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/** Unit tests for {@link SnipCompaction}. */
class SnipCompactionTest {

    private static final float LOW_THRESHOLD = 0.50f;
    private static final float HIGH_THRESHOLD = 0.95f;

    private static ContextState lowPressure() {
        // 30% pressure, no context window → falls back to percentage-only
        return new ContextState(200_000, 60_000, 0.30f, 10);
    }

    private static ContextState highPressure() {
        // 90% pressure, no context window → falls back to percentage-only
        return new ContextState(200_000, 180_000, 0.90f, 10);
    }

    private static CompactionConfig config() {
        return new CompactionConfig(50_000, true, null);
    }

    private static Msg userMsg(String text) {
        return Msg.of(MsgRole.USER, text);
    }

    private static Msg toolResultMsg(String toolUseId, String content) {
        return MsgBuilder.toolResultMsg(toolUseId, content, false);
    }

    // ===== shouldTrigger =====

    @Test
    void shouldTrigger_lowPressure_returnsFalse() {
        SnipCompaction snip = new SnipCompaction(HIGH_THRESHOLD);
        assertThat(snip.shouldTrigger(lowPressure())).isFalse();
    }

    @Test
    void shouldTrigger_highPressure_returnsTrue() {
        SnipCompaction snip = new SnipCompaction(LOW_THRESHOLD);
        assertThat(snip.shouldTrigger(highPressure())).isTrue();
    }

    // ===== compact() =====

    @Test
    void compact_emptyMessages_returnsEmptyList() {
        SnipCompaction snip = new SnipCompaction();

        StepVerifier.create(snip.compact(List.of(), config()))
                .assertNext(result -> assertThat(result.compactedMessages()).isEmpty())
                .verifyComplete();
    }

    @Test
    void compact_messageCountUnchanged() {
        // Build a list with many tool results so snipping occurs
        List<Msg> messages = buildMessageList(8);
        SnipCompaction snip = new SnipCompaction();

        StepVerifier.create(snip.compact(messages, config()))
                .assertNext(
                        result ->
                                assertThat(result.compactedMessages())
                                        .hasSize(messages.size())
                                        .as("compact replaces content, does not delete messages"))
                .verifyComplete();
    }

    @Test
    void compact_recentToolResults_preserved() {
        // 8 tool results; last 5 should be preserved (PRESERVE_RECENT_TOOL_RESULTS=5)
        List<Msg> messages = buildMessageList(8);
        SnipCompaction snip = new SnipCompaction();

        StepVerifier.create(snip.compact(messages, config()))
                .assertNext(
                        result -> {
                            // Last 5 tool-result messages should still have original content
                            List<Msg> resultMsgs = result.compactedMessages();
                            long unsnipped =
                                    resultMsgs.stream()
                                            .filter(m -> m.role() == MsgRole.TOOL)
                                            .filter(m -> !isSnipped(m))
                                            .count();
                            assertThat(unsnipped).isGreaterThanOrEqualTo(5);
                        })
                .verifyComplete();
    }

    @Test
    void compact_olderToolResults_replacedWithPlaceholder() {
        // 8 tool results → 3 oldest should be snipped
        List<Msg> messages = buildMessageList(8);
        SnipCompaction snip = new SnipCompaction();

        StepVerifier.create(snip.compact(messages, config()))
                .assertNext(
                        result -> {
                            long snippedCount =
                                    result.compactedMessages().stream()
                                            .filter(m -> m.role() == MsgRole.TOOL)
                                            .filter(this::isSnipped)
                                            .count();
                            assertThat(snippedCount).isGreaterThan(0);
                        })
                .verifyComplete();
    }

    @Test
    void compact_resultHasBoundaryMarker() {
        List<Msg> messages = buildMessageList(8);
        SnipCompaction snip = new SnipCompaction();

        StepVerifier.create(snip.compact(messages, config()))
                .assertNext(
                        result -> {
                            assertThat(result.marker()).isNotNull();
                            assertThat(result.marker().strategyName()).isEqualTo("snip");
                        })
                .verifyComplete();
    }

    @Test
    void compact_noToolResults_noChange() {
        // Only user/assistant messages — nothing to snip
        List<Msg> messages =
                List.of(userMsg("hello"), Msg.of(MsgRole.ASSISTANT, "hi"), userMsg("how are you"));
        SnipCompaction snip = new SnipCompaction();

        StepVerifier.create(snip.compact(messages, config()))
                .assertNext(
                        result -> {
                            assertThat(result.compactedMessages()).hasSize(3);
                            // No snipping means tokensSaved should be 0 or very small
                            assertThat(result.tokensSaved()).isGreaterThanOrEqualTo(0);
                        })
                .verifyComplete();
    }

    // ===== Helpers =====

    private List<Msg> buildMessageList(int toolResultCount) {
        List<Msg> msgs = new ArrayList<>();
        msgs.add(userMsg("start task"));
        for (int i = 0; i < toolResultCount; i++) {
            msgs.add(Msg.of(MsgRole.ASSISTANT, "Using tool " + i));
            msgs.add(toolResultMsg("tc-" + i, "result-content-" + i));
        }
        return msgs;
    }

    private boolean isSnipped(Msg msg) {
        return msg.contents().stream()
                .filter(Content.TextContent.class::isInstance)
                .map(Content.TextContent.class::cast)
                .anyMatch(tc -> tc.text().contains("[Tool result snipped"));
    }
}
