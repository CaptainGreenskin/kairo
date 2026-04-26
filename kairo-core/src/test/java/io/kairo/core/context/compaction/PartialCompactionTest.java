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
import io.kairo.api.context.CompactionConfig.PartialDirection;
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

class PartialCompactionTest {

    private static CompactionConfig fromConfig() {
        return new CompactionConfig(
                50_000, false, mock(ModelProvider.class), PartialDirection.FROM, null);
    }

    private static CompactionConfig upToConfig(String markerId) {
        return new CompactionConfig(
                50_000, false, mock(ModelProvider.class), PartialDirection.UP_TO, markerId);
    }

    private static Msg userMsg(String id, String text) {
        return Msg.builder()
                .id(id)
                .role(MsgRole.USER)
                .addContent(new Content.TextContent(text))
                .tokenCount(100)
                .build();
    }

    private static Msg systemMsg(String text) {
        return Msg.builder()
                .role(MsgRole.SYSTEM)
                .addContent(new Content.TextContent(text))
                .tokenCount(50)
                .build();
    }

    @Test
    void implementsCompactionStrategy() {
        assertThat(new PartialCompaction()).isInstanceOf(CompactionStrategy.class);
    }

    @Test
    void nameIsPartial() {
        assertThat(new PartialCompaction().name()).isEqualTo("partial");
    }

    @Test
    void priorityIs500() {
        assertThat(new PartialCompaction().priority()).isEqualTo(500);
    }

    @Test
    void compactEmptyListReturnsEmpty() {
        CompactionResult result = new PartialCompaction().compact(List.of(), fromConfig()).block();
        assertThat(result).isNotNull();
        assertThat(result.compactedMessages()).isEmpty();
    }

    @Test
    void compactFromPreservesSystemMessages() {
        List<Msg> messages = new ArrayList<>();
        messages.add(systemMsg("You are a helpful assistant."));
        for (int i = 0; i < 7; i++) {
            messages.add(userMsg("msg-" + i, "message " + i));
        }
        CompactionResult result = new PartialCompaction().compact(messages, fromConfig()).block();
        long systemCount =
                result.compactedMessages().stream().filter(m -> m.role() == MsgRole.SYSTEM).count();
        // original system msg + at least 1 summary SYSTEM msg
        assertThat(systemCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void compactFromPreservesTailMessages() {
        List<Msg> messages = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            messages.add(userMsg("msg-" + i, "message " + i));
        }
        CompactionResult result = new PartialCompaction().compact(messages, fromConfig()).block();
        // Last 5 messages must be preserved intact as non-system USER messages
        List<Msg> compacted = result.compactedMessages();
        long userCount = compacted.stream().filter(m -> m.role() == MsgRole.USER).count();
        assertThat(userCount).isEqualTo(5);
    }

    @Test
    void compactFromReducesMessageCount() {
        List<Msg> messages = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            messages.add(userMsg("msg-" + i, "message " + i));
        }
        CompactionResult result = new PartialCompaction().compact(messages, fromConfig()).block();
        assertThat(result.compactedMessages().size()).isLessThan(messages.size());
    }

    @Test
    void compactUpToCompressesBeforeMarker() {
        List<Msg> messages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            messages.add(userMsg("msg-" + i, "message " + i));
        }
        // marker is at index 3 → compress [0..2], keep [3..4]
        CompactionResult result =
                new PartialCompaction().compact(messages, upToConfig("msg-3")).block();
        List<Msg> compacted = result.compactedMessages();
        // msg-3 and msg-4 must be present
        boolean hasMsg3 = compacted.stream().anyMatch(m -> "msg-3".equals(m.id()));
        boolean hasMsg4 = compacted.stream().anyMatch(m -> "msg-4".equals(m.id()));
        assertThat(hasMsg3).isTrue();
        assertThat(hasMsg4).isTrue();
    }

    @Test
    void compactUpToMarkerNotFoundFallsBackToFrom() {
        List<Msg> messages = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            messages.add(userMsg("msg-" + i, "message " + i));
        }
        // non-existent marker → fallback to FROM behavior
        CompactionResult result =
                new PartialCompaction().compact(messages, upToConfig("no-such-id")).block();
        assertThat(result.compactedMessages()).isNotEmpty();
    }

    @Test
    void returnsBoundaryMarker() {
        CompactionResult result =
                new PartialCompaction().compact(List.of(userMsg("id", "hi")), fromConfig()).block();
        assertThat(result.marker()).isNotNull();
        assertThat(result.marker().strategyName()).isEqualTo("partial");
    }

    @Test
    void shouldTriggerWithHighPressure() {
        ContextState state = new ContextState(0, 0, 1.0f, 5);
        assertThat(new PartialCompaction(0.98f).shouldTrigger(state)).isTrue();
    }

    @Test
    void shouldNotTriggerWithLowPressure() {
        ContextState state = new ContextState(0, 0, 0.80f, 5);
        assertThat(new PartialCompaction(0.98f).shouldTrigger(state)).isFalse();
    }
}
