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
import java.util.Map;
import org.junit.jupiter.api.Test;

class CollapseCompactionTest {

    private static CompactionConfig config() {
        return new CompactionConfig(
                50_000,
                false,
                mock(ModelProvider.class),
                CompactionConfig.PartialDirection.FROM,
                null);
    }

    private static Msg userMsg(String text) {
        return Msg.builder()
                .role(MsgRole.USER)
                .addContent(new Content.TextContent(text))
                .tokenCount(20)
                .build();
    }

    private static Msg toolUseMsg(String toolId, String toolName) {
        return Msg.builder()
                .role(MsgRole.ASSISTANT)
                .addContent(new Content.ToolUseContent(toolId, toolName, Map.of()))
                .tokenCount(30)
                .build();
    }

    private static Msg toolResultMsg(String toolUseId) {
        return Msg.builder()
                .role(MsgRole.TOOL)
                .addContent(new Content.ToolResultContent(toolUseId, "result content", false))
                .tokenCount(50)
                .build();
    }

    @Test
    void implementsCompactionStrategy() {
        assertThat(new CollapseCompaction()).isInstanceOf(CompactionStrategy.class);
    }

    @Test
    void nameIsCollapse() {
        assertThat(new CollapseCompaction().name()).isEqualTo("collapse");
    }

    @Test
    void priorityIs300() {
        assertThat(new CollapseCompaction().priority()).isEqualTo(300);
    }

    @Test
    void compactEmptyListReturnsEmpty() {
        CompactionResult result = new CollapseCompaction().compact(List.of(), config()).block();
        assertThat(result).isNotNull();
        assertThat(result.compactedMessages()).isEmpty();
    }

    @Test
    void compactUserOnlyMessagesUnchanged() {
        List<Msg> messages = List.of(userMsg("hello"), userMsg("world"));
        CompactionResult result = new CollapseCompaction().compact(messages, config()).block();
        assertThat(result.compactedMessages()).hasSize(2);
    }

    @Test
    void collapsesToolGroupOfThreeOrMore() {
        // 3 tool-related messages → group ≥ MIN_GROUP_SIZE → collapsed to 1
        List<Msg> messages = new ArrayList<>();
        messages.add(toolUseMsg("id1", "bash"));
        messages.add(toolResultMsg("id1"));
        messages.add(toolUseMsg("id2", "bash"));
        CompactionResult result = new CollapseCompaction().compact(messages, config()).block();
        assertThat(result.compactedMessages()).hasSize(1);
        Msg collapsed = result.compactedMessages().get(0);
        assertThat(collapsed.role()).isEqualTo(MsgRole.ASSISTANT);
        String text = ((Content.TextContent) collapsed.contents().get(0)).text();
        assertThat(text).contains("[Collapsed:");
    }

    @Test
    void keepsSmallGroupBelowMinSize() {
        // 2 tool messages < MIN_GROUP_SIZE(3) → kept as-is
        List<Msg> messages = List.of(toolUseMsg("id1", "bash"), toolResultMsg("id1"));
        CompactionResult result = new CollapseCompaction().compact(messages, config()).block();
        assertThat(result.compactedMessages()).hasSize(2);
    }

    @Test
    void collapsedSummarySaysAllSuccessful() {
        List<Msg> messages = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            messages.add(toolUseMsg("id" + i, "read"));
            messages.add(toolResultMsg("id" + i));
        }
        // 6 messages ≥ 3 → collapsed
        CompactionResult result = new CollapseCompaction().compact(messages, config()).block();
        assertThat(result.compactedMessages()).hasSize(1);
        String text =
                ((Content.TextContent) result.compactedMessages().get(0).contents().get(0)).text();
        assertThat(text).contains("all successful");
    }

    @Test
    void nonToolMessagesBetweenGroupsPreserved() {
        List<Msg> messages = new ArrayList<>();
        messages.add(userMsg("hi"));
        messages.add(toolUseMsg("id1", "bash"));
        messages.add(toolResultMsg("id1"));
        messages.add(toolUseMsg("id2", "bash"));
        messages.add(userMsg("bye"));
        CompactionResult result = new CollapseCompaction().compact(messages, config()).block();
        // user(1) + collapsed(1) + user(1) = 3
        assertThat(result.compactedMessages()).hasSize(3);
    }

    @Test
    void returnsBoundaryMarker() {
        CompactionResult result =
                new CollapseCompaction().compact(List.of(userMsg("hi")), config()).block();
        assertThat(result.marker()).isNotNull();
        assertThat(result.marker().strategyName()).isEqualTo("collapse");
    }

    @Test
    void shouldTriggerWithHighPressure() {
        ContextState state = new ContextState(0, 0, 1.0f, 5);
        assertThat(new CollapseCompaction(0.90f).shouldTrigger(state)).isTrue();
    }

    @Test
    void shouldNotTriggerWithLowPressure() {
        ContextState state = new ContextState(0, 0, 0.50f, 5);
        assertThat(new CollapseCompaction(0.90f).shouldTrigger(state)).isFalse();
    }
}
