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
import io.kairo.api.context.CompactionResult;
import io.kairo.api.context.ContextState;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class SnipCompactionTest {

    private static final CompactionConfig CONFIG = new CompactionConfig(100_000, true, null);

    @Test
    void nameIsSnip() {
        assertThat(new SnipCompaction().name()).isEqualTo("snip");
    }

    @Test
    void priorityIs100() {
        assertThat(new SnipCompaction().priority()).isEqualTo(100);
    }

    @Test
    void shouldNotTriggerBelowThreshold() {
        SnipCompaction strategy = new SnipCompaction();
        assertThat(strategy.shouldTrigger(new ContextState(0, 0, 0.79f, 10))).isFalse();
    }

    @Test
    void shouldTriggerAtThreshold() {
        SnipCompaction strategy = new SnipCompaction();
        assertThat(strategy.shouldTrigger(new ContextState(0, 0, 0.80f, 10))).isTrue();
    }

    @Test
    void customThresholdConstructor() {
        SnipCompaction strategy = new SnipCompaction(0.70f);
        assertThat(strategy.shouldTrigger(new ContextState(0, 0, 0.70f, 10))).isTrue();
        assertThat(strategy.shouldTrigger(new ContextState(0, 0, 0.69f, 10))).isFalse();
    }

    @Test
    void compactEmptyListCompletesSuccessfully() {
        SnipCompaction strategy = new SnipCompaction();
        StepVerifier.create(strategy.compact(List.of(), CONFIG))
                .assertNext(result -> assertThat(result.compactedMessages()).isEmpty())
                .verifyComplete();
    }

    @Test
    void compactPreservesUserMessage() {
        SnipCompaction strategy = new SnipCompaction();
        Msg userMsg = Msg.of(MsgRole.USER, "hello");

        StepVerifier.create(strategy.compact(List.of(userMsg), CONFIG))
                .assertNext(
                        result -> {
                            assertThat(result.compactedMessages()).hasSize(1);
                            assertThat(result.compactedMessages().get(0).role())
                                    .isEqualTo(MsgRole.USER);
                        })
                .verifyComplete();
    }

    @Test
    void compactResultHasBoundaryMarker() {
        SnipCompaction strategy = new SnipCompaction();
        StepVerifier.create(strategy.compact(List.of(Msg.of(MsgRole.USER, "hi")), CONFIG))
                .assertNext(result -> assertThat(result.marker()).isNotNull())
                .verifyComplete();
    }

    @Test
    void compactResultMarkerNameMatchesStrategy() {
        SnipCompaction strategy = new SnipCompaction();
        StepVerifier.create(strategy.compact(List.of(Msg.of(MsgRole.USER, "hi")), CONFIG))
                .assertNext(
                        (CompactionResult result) ->
                                assertThat(result.marker().strategyName()).isEqualTo("snip"))
                .verifyComplete();
    }
}
