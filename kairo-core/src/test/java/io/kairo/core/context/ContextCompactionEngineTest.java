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
package io.kairo.core.context;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.context.BoundaryMarker;
import io.kairo.api.context.CompactionConfig;
import io.kairo.api.context.CompactionResult;
import io.kairo.api.context.CompactionStrategy;
import io.kairo.api.context.ContextState;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.core.context.compaction.CompactionPipeline;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Integration-style tests for the 6-stage compaction engine (CompactionPipeline). Tests focus on
 * threshold configuration and needsCompaction semantics.
 */
class ContextCompactionEngineTest {

    private CompactionConfig config;

    @BeforeEach
    void setUp() {
        config = new CompactionConfig(100_000, true, null);
    }

    private List<Msg> messages(int count) {
        List<Msg> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(
                    Msg.builder()
                            .id("msg-" + i)
                            .role(MsgRole.USER)
                            .addContent(new Content.TextContent("text " + i))
                            .tokenCount(100)
                            .build());
        }
        return result;
    }

    private CompactionStrategy countingStrategy(
            String name, int priority, float threshold, AtomicInteger counter) {
        return new CompactionStrategy() {
            @Override
            public boolean shouldTrigger(ContextState state) {
                return state.pressure() >= threshold;
            }

            @Override
            public Mono<CompactionResult> compact(List<Msg> msgs, CompactionConfig cfg) {
                counter.incrementAndGet();
                BoundaryMarker m =
                        new BoundaryMarker(Instant.now(), name, msgs.size(), msgs.size(), 50);
                return Mono.just(new CompactionResult(msgs, 50, m));
            }

            @Override
            public int priority() {
                return priority;
            }

            @Override
            public String name() {
                return name;
            }
        };
    }

    @Test
    @DisplayName("Pressure below all thresholds: no stage triggers, returns empty")
    void pressure_belowAllThresholds_noCompaction() {
        AtomicInteger calls = new AtomicInteger();
        CompactionStrategy stage = countingStrategy("snip", 100, 0.80f, calls);
        CompactionPipeline pipeline = new CompactionPipeline(List.of(stage));

        // pressure 0.50 — well below 0.80 threshold
        StepVerifier.create(pipeline.execute(messages(5), Set.of(), 0.50f, config))
                .verifyComplete();

        assertEquals(0, calls.get());
    }

    @Test
    @DisplayName("Pressure at exactly trigger threshold: stage fires")
    void pressure_atThreshold_stageTriggers() {
        AtomicInteger calls = new AtomicInteger();
        CompactionStrategy stage = countingStrategy("snip", 100, 0.80f, calls);
        CompactionPipeline pipeline = new CompactionPipeline(List.of(stage));

        StepVerifier.create(pipeline.execute(messages(5), Set.of(), 0.80f, config))
                .assertNext(r -> assertEquals(50, r.tokensSaved()))
                .verifyComplete();

        assertEquals(1, calls.get());
    }

    @Test
    @DisplayName("Custom thresholds: lower snip pressure triggers at 0.50")
    void customThresholds_lowerSnipPressure_triggersEarlier() {
        CompactionThresholds custom = CompactionThresholds.builder().snipPressure(0.50f).build();

        AtomicInteger calls = new AtomicInteger();
        CompactionStrategy customStage = countingStrategy("custom-snip", 100, 0.50f, calls);
        CompactionPipeline pipeline =
                new CompactionPipeline(List.of(customStage), null, null, custom);

        StepVerifier.create(pipeline.execute(messages(3), Set.of(), 0.55f, config))
                .assertNext(r -> assertTrue(r.tokensSaved() > 0))
                .verifyComplete();

        assertEquals(1, calls.get());
    }

    @Test
    @DisplayName("Only stages above current pressure threshold do NOT fire")
    void pressure_belowHigherThreshold_heavierStageSkipped() {
        AtomicInteger lowCalls = new AtomicInteger();
        AtomicInteger highCalls = new AtomicInteger();

        CompactionStrategy low = countingStrategy("low", 100, 0.80f, lowCalls);
        CompactionStrategy high = countingStrategy("high", 200, 0.95f, highCalls);
        CompactionPipeline pipeline = new CompactionPipeline(List.of(low, high));

        // pressure 0.85 — above low (0.80) but below high (0.95)
        StepVerifier.create(pipeline.execute(messages(4), Set.of(), 0.85f, config))
                .assertNext(r -> assertEquals(50, r.tokensSaved()))
                .verifyComplete();

        assertEquals(1, lowCalls.get());
        assertEquals(0, highCalls.get());
    }

    @Test
    @DisplayName("Empty pipeline: no stages, execute returns empty Mono")
    void emptyPipeline_returnsEmpty() {
        CompactionPipeline pipeline = new CompactionPipeline(List.of());

        StepVerifier.create(pipeline.execute(messages(3), Set.of(), 0.99f, config))
                .verifyComplete();
    }

    @Test
    @DisplayName("Circuit breaker opens after repeated failures and blocks further compaction")
    void circuitBreaker_opensAfterFailures_skipsCompaction() {
        CompactionStrategy failing =
                new CompactionStrategy() {
                    @Override
                    public boolean shouldTrigger(ContextState state) {
                        return true;
                    }

                    @Override
                    public Mono<CompactionResult> compact(List<Msg> msgs, CompactionConfig cfg) {
                        return Mono.error(new RuntimeException("fail"));
                    }

                    @Override
                    public int priority() {
                        return 100;
                    }

                    @Override
                    public String name() {
                        return "fail";
                    }
                };

        // Use custom thresholds with cbFailureLimit=2 so circuit opens faster
        CompactionThresholds thresholds = CompactionThresholds.builder().cbFailureLimit(2).build();
        CompactionPipeline pipeline =
                new CompactionPipeline(List.of(failing), null, null, thresholds);

        assertFalse(pipeline.isCircuitBreakerOpen());

        for (int i = 0; i < 2; i++) {
            StepVerifier.create(pipeline.execute(messages(1), Set.of(), 0.90f, config))
                    .expectError(RuntimeException.class)
                    .verify();
        }

        assertTrue(pipeline.isCircuitBreakerOpen());

        // Pipeline now returns empty (skips compaction)
        StepVerifier.create(pipeline.execute(messages(1), Set.of(), 0.90f, config))
                .verifyComplete();
    }

    @Test
    @DisplayName("Circuit breaker resets via resetCircuitBreaker()")
    void circuitBreaker_resetRestoresNormalOperation() {
        CompactionThresholds thresholds = CompactionThresholds.builder().cbFailureLimit(2).build();
        CompactionStrategy failing =
                new CompactionStrategy() {
                    @Override
                    public boolean shouldTrigger(ContextState state) {
                        return true;
                    }

                    @Override
                    public Mono<CompactionResult> compact(List<Msg> msgs, CompactionConfig cfg) {
                        return Mono.error(new RuntimeException("fail"));
                    }

                    @Override
                    public int priority() {
                        return 100;
                    }

                    @Override
                    public String name() {
                        return "fail";
                    }
                };

        CompactionPipeline pipeline =
                new CompactionPipeline(List.of(failing), null, null, thresholds);

        for (int i = 0; i < 2; i++) {
            StepVerifier.create(pipeline.execute(messages(1), Set.of(), 0.90f, config))
                    .expectError()
                    .verify();
        }
        assertTrue(pipeline.isCircuitBreakerOpen());

        pipeline.resetCircuitBreaker();
        assertFalse(pipeline.isCircuitBreakerOpen());
    }
}
