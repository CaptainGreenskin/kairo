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

class PartialCompactionTest {

    private static final CompactionConfig CONFIG = new CompactionConfig(100_000, true, null);

    @Test
    void nameIsPartial() {
        assertThat(new PartialCompaction().name()).isEqualTo("partial");
    }

    @Test
    void priorityIs500() {
        assertThat(new PartialCompaction().priority()).isEqualTo(500);
    }

    @Test
    void shouldNotTriggerBelowThreshold() {
        PartialCompaction strategy = new PartialCompaction();
        assertThat(strategy.shouldTrigger(new ContextState(0, 0, 0.97f, 10))).isFalse();
    }

    @Test
    void shouldTriggerAtThreshold() {
        PartialCompaction strategy = new PartialCompaction();
        assertThat(strategy.shouldTrigger(new ContextState(0, 0, 0.98f, 10))).isTrue();
    }

    @Test
    void customThresholdConstructor() {
        PartialCompaction strategy = new PartialCompaction(0.80f);
        assertThat(strategy.shouldTrigger(new ContextState(0, 0, 0.80f, 10))).isTrue();
        assertThat(strategy.shouldTrigger(new ContextState(0, 0, 0.79f, 10))).isFalse();
    }

    @Test
    void compactSingleMessageCompletesSuccessfully() {
        PartialCompaction strategy = new PartialCompaction();
        Msg userMsg = Msg.of(MsgRole.USER, "hello");
        StepVerifier.create(strategy.compact(List.of(userMsg), CONFIG))
                .assertNext(result -> assertThat(result.compactedMessages()).isNotEmpty())
                .verifyComplete();
    }

    @Test
    void compactPreservesSystemMessages() {
        PartialCompaction strategy = new PartialCompaction();
        Msg systemMsg = Msg.of(MsgRole.SYSTEM, "System instructions");
        Msg userMsg = Msg.of(MsgRole.USER, "hi");

        StepVerifier.create(strategy.compact(List.of(systemMsg, userMsg), CONFIG))
                .assertNext(
                        result -> {
                            boolean hasSystem =
                                    result.compactedMessages().stream()
                                            .anyMatch(m -> m.role() == MsgRole.SYSTEM);
                            assertThat(hasSystem).isTrue();
                        })
                .verifyComplete();
    }

    @Test
    void compactResultHasBoundaryMarker() {
        PartialCompaction strategy = new PartialCompaction();
        StepVerifier.create(strategy.compact(List.of(Msg.of(MsgRole.USER, "hi")), CONFIG))
                .assertNext(result -> assertThat(result.marker()).isNotNull())
                .verifyComplete();
    }
}
