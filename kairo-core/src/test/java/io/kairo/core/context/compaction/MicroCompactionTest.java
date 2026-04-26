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
import java.util.List;
import org.junit.jupiter.api.Test;

class MicroCompactionTest {

    private static CompactionConfig config() {
        return new CompactionConfig(
                50_000,
                false,
                mock(ModelProvider.class),
                CompactionConfig.PartialDirection.FROM,
                null);
    }

    private static Msg toolResultMsg(String toolUseId, String content) {
        return Msg.builder()
                .role(MsgRole.TOOL)
                .addContent(new Content.ToolResultContent(toolUseId, content, false))
                .tokenCount(500)
                .build();
    }

    private static Msg userMsg(String text) {
        return Msg.builder()
                .role(MsgRole.USER)
                .addContent(new Content.TextContent(text))
                .tokenCount(10)
                .build();
    }

    @Test
    void implementsCompactionStrategy() {
        assertThat(new MicroCompaction()).isInstanceOf(CompactionStrategy.class);
    }

    @Test
    void nameIsMicro() {
        assertThat(new MicroCompaction().name()).isEqualTo("micro");
    }

    @Test
    void priorityIs200() {
        assertThat(new MicroCompaction().priority()).isEqualTo(200);
    }

    @Test
    void compactEmptyListReturnsEmpty() {
        CompactionResult result = new MicroCompaction().compact(List.of(), config()).block();
        assertThat(result).isNotNull();
        assertThat(result.compactedMessages()).isEmpty();
    }

    @Test
    void compactReplacesToolResultContentWithSummary() {
        List<Msg> messages = List.of(toolResultMsg("tool-1", "A".repeat(1000)));
        CompactionResult result = new MicroCompaction().compact(messages, config()).block();
        assertThat(result.compactedMessages()).hasSize(1);
        Msg compacted = result.compactedMessages().get(0);
        Content c = compacted.contents().get(0);
        assertThat(c).isInstanceOf(Content.ToolResultContent.class);
        String summary = ((Content.ToolResultContent) c).content();
        assertThat(summary).contains("[Result:").contains("success").contains("bytes]");
    }

    @Test
    void compactSavesTokens() {
        List<Msg> messages = List.of(toolResultMsg("tool-1", "A".repeat(1000)));
        CompactionResult result = new MicroCompaction().compact(messages, config()).block();
        assertThat(result.tokensSaved()).isPositive();
    }

    @Test
    void compactPreservesUserMessages() {
        List<Msg> messages = List.of(userMsg("hello"), userMsg("world"));
        CompactionResult result = new MicroCompaction().compact(messages, config()).block();
        assertThat(result.compactedMessages()).hasSize(2);
        assertThat(result.compactedMessages().get(0).role()).isEqualTo(MsgRole.USER);
    }

    @Test
    void compactReturnsBoundaryMarker() {
        CompactionResult result =
                new MicroCompaction().compact(List.of(userMsg("hi")), config()).block();
        assertThat(result.marker()).isNotNull();
        assertThat(result.marker().strategyName()).isEqualTo("micro");
    }

    @Test
    void shouldTriggerWithHighPressure() {
        ContextState state = new ContextState(0, 0, 1.0f, 10);
        assertThat(new MicroCompaction(0.85f).shouldTrigger(state)).isTrue();
    }

    @Test
    void shouldNotTriggerWithLowPressure() {
        ContextState state = new ContextState(0, 0, 0.50f, 10);
        assertThat(new MicroCompaction(0.85f).shouldTrigger(state)).isFalse();
    }
}
