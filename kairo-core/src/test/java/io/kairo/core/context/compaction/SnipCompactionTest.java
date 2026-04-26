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
import static org.mockito.Mockito.mock;

import io.kairo.api.context.CompactionConfig;
import io.kairo.api.context.CompactionResult;
import io.kairo.api.context.CompactionStrategy;
import io.kairo.api.context.ContextState;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelProvider;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnipCompactionTest {

    private static CompactionConfig config() {
        return new CompactionConfig(
                50_000,
                false,
                mock(ModelProvider.class),
                CompactionConfig.PartialDirection.FROM,
                null);
    }

    private static Msg textMsg(MsgRole role, String text) {
        return Msg.builder()
                .role(role)
                .addContent(new Content.TextContent(text))
                .tokenCount(100)
                .build();
    }

    private static Msg toolResultMsg(String toolUseId, String content) {
        return Msg.builder()
                .role(MsgRole.TOOL)
                .addContent(new Content.ToolResultContent(toolUseId, content, false))
                .tokenCount(200)
                .build();
    }

    @Test
    void implementsCompactionStrategy() {
        assertThat(new SnipCompaction()).isInstanceOf(CompactionStrategy.class);
    }

    @Test
    void nameIsSnip() {
        assertThat(new SnipCompaction().name()).isEqualTo("snip");
    }

    @Test
    void priorityIs100() {
        assertThat(new SnipCompaction().priority()).isEqualTo(100);
    }

    @Test
    void compactEmptyListReturnsEmpty() {
        CompactionResult result = new SnipCompaction().compact(List.of(), config()).block();
        assertThat(result).isNotNull();
        assertThat(result.compactedMessages()).isEmpty();
    }

    @Test
    void compactPreservesUserMessages() {
        List<Msg> messages =
                List.of(textMsg(MsgRole.USER, "hello"), textMsg(MsgRole.ASSISTANT, "hi"));
        CompactionResult result = new SnipCompaction().compact(messages, config()).block();
        assertThat(result.compactedMessages()).hasSize(2);
    }

    @Test
    void compactSnipsOldToolResults() {
        List<Msg> messages = new ArrayList<>();
        // Add 7 tool result messages (more than PRESERVE_RECENT_TOOL_RESULTS=5)
        for (int i = 0; i < 7; i++) {
            messages.add(toolResultMsg("tool-" + i, "long result content " + i));
        }
        CompactionResult result = new SnipCompaction().compact(messages, config()).block();
        assertThat(result.compactedMessages()).hasSize(7);
        // The first 2 (7-5=2) tool results should be snipped
        Msg first = result.compactedMessages().get(0);
        assertThat(first.contents().get(0)).isInstanceOf(Content.TextContent.class);
        String text = ((Content.TextContent) first.contents().get(0)).text();
        assertThat(text).contains("[Tool result snipped");
    }

    @Test
    void compactPreservesRecentToolResults() {
        List<Msg> messages = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            messages.add(toolResultMsg("tool-" + i, "result " + i));
        }
        CompactionResult result = new SnipCompaction().compact(messages, config()).block();
        // The last 5 tool results should be preserved as ToolResultContent
        List<Msg> compacted = result.compactedMessages();
        for (int i = 2; i < 7; i++) {
            Msg msg = compacted.get(i);
            assertThat(msg.contents().get(0)).isInstanceOf(Content.ToolResultContent.class);
        }
    }

    @Test
    void compactReturnsBoundaryMarker() {
        List<Msg> messages = List.of(textMsg(MsgRole.USER, "hi"));
        CompactionResult result = new SnipCompaction().compact(messages, config()).block();
        assertThat(result.marker()).isNotNull();
        assertThat(result.marker().strategyName()).isEqualTo("snip");
    }

    @Test
    void shouldTriggerWithHighPressure() {
        // contextWindow=0 → fallback to pressure comparison; pressure >= threshold triggers
        ContextState state = new ContextState(0, 0, 1.0f, 10);
        assertThat(new SnipCompaction(0.80f).shouldTrigger(state)).isTrue();
    }

    @Test
    void shouldNotTriggerWithLowPressure() {
        ContextState state = new ContextState(0, 0, 0.50f, 10);
        assertThat(new SnipCompaction(0.80f).shouldTrigger(state)).isFalse();
    }
}
