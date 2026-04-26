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
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class CollapseCompactionTest {

    private static final CompactionConfig CONFIG = new CompactionConfig(100_000, true, null);

    @Test
    void nameIsCollapse() {
        assertThat(new CollapseCompaction().name()).isEqualTo("collapse");
    }

    @Test
    void priorityIs300() {
        assertThat(new CollapseCompaction().priority()).isEqualTo(300);
    }

    @Test
    void shouldNotTriggerBelowThreshold() {
        CollapseCompaction strategy = new CollapseCompaction();
        assertThat(strategy.shouldTrigger(new ContextState(0, 0, 0.89f, 10))).isFalse();
    }

    @Test
    void shouldTriggerAtThreshold() {
        CollapseCompaction strategy = new CollapseCompaction();
        assertThat(strategy.shouldTrigger(new ContextState(0, 0, 0.90f, 10))).isTrue();
    }

    @Test
    void customThresholdConstructor() {
        CollapseCompaction strategy = new CollapseCompaction(0.75f);
        assertThat(strategy.shouldTrigger(new ContextState(0, 0, 0.75f, 10))).isTrue();
        assertThat(strategy.shouldTrigger(new ContextState(0, 0, 0.74f, 10))).isFalse();
    }

    @Test
    void compactEmptyListCompletesSuccessfully() {
        CollapseCompaction strategy = new CollapseCompaction();
        StepVerifier.create(strategy.compact(List.of(), CONFIG))
                .assertNext(result -> assertThat(result.compactedMessages()).isEmpty())
                .verifyComplete();
    }

    @Test
    void compactNonToolMessagesArePreserved() {
        CollapseCompaction strategy = new CollapseCompaction();
        Msg userMsg = Msg.of(MsgRole.USER, "question");
        Msg assistantMsg = Msg.of(MsgRole.ASSISTANT, "answer");

        StepVerifier.create(strategy.compact(List.of(userMsg, assistantMsg), CONFIG))
                .assertNext(result -> assertThat(result.compactedMessages()).hasSize(2))
                .verifyComplete();
    }

    @Test
    void compactResultHasBoundaryMarker() {
        CollapseCompaction strategy = new CollapseCompaction();
        StepVerifier.create(strategy.compact(List.of(Msg.of(MsgRole.USER, "hi")), CONFIG))
                .assertNext(result -> assertThat(result.marker()).isNotNull())
                .verifyComplete();
    }
}
