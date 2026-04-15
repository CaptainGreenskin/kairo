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

import io.kairo.api.context.*;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CompactionPipelineTest {

    private CompactionConfig config;

    @BeforeEach
    void setUp() {
        config = new CompactionConfig(100_000, true, null);
    }

    // ---- Helper to create a simple strategy stub ----

    private CompactionStrategy stubStrategy(
            String name, int priority, float triggerThreshold, int tokensSaved) {
        return new CompactionStrategy() {
            @Override
            public boolean shouldTrigger(ContextState state) {
                return state.pressure() >= triggerThreshold;
            }

            @Override
            public Mono<CompactionResult> compact(List<Msg> messages, CompactionConfig cfg) {
                BoundaryMarker marker =
                        new BoundaryMarker(
                                Instant.now(), name, messages.size(), messages.size(), tokensSaved);
                return Mono.just(new CompactionResult(messages, tokensSaved, marker));
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

    private CompactionStrategy failingStrategy(String name, int priority, float triggerThreshold) {
        return new CompactionStrategy() {
            @Override
            public boolean shouldTrigger(ContextState state) {
                return state.pressure() >= triggerThreshold;
            }

            @Override
            public Mono<CompactionResult> compact(List<Msg> messages, CompactionConfig cfg) {
                return Mono.error(new RuntimeException("compaction failed: " + name));
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

    private List<Msg> sampleMessages(int count) {
        List<Msg> msgs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            msgs.add(
                    Msg.builder()
                            .id("msg-" + i)
                            .role(MsgRole.USER)
                            .addContent(new Content.TextContent("Message " + i))
                            .tokenCount(100)
                            .build());
        }
        return msgs;
    }

    @Test
    @DisplayName("Pipeline with all 5 default stages should be sorted by priority")
    void testDefaultPipelineStageOrder() {
        // Create with default stages (passing null model provider)
        CompactionPipeline pipeline =
                new CompactionPipeline((io.kairo.api.model.ModelProvider) null);
        assertFalse(pipeline.isCircuitBreakerOpen());
    }

    @Test
    @DisplayName("Stages should execute in priority order")
    void testStagesExecuteInOrder() {
        List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());

        CompactionStrategy stage1 =
                new CompactionStrategy() {
                    @Override
                    public boolean shouldTrigger(ContextState state) {
                        return true;
                    }

                    @Override
                    public Mono<CompactionResult> compact(
                            List<Msg> messages, CompactionConfig cfg) {
                        executionOrder.add("stage1");
                        BoundaryMarker m = new BoundaryMarker(Instant.now(), "s1", 5, 5, 10);
                        return Mono.just(new CompactionResult(messages, 10, m));
                    }

                    @Override
                    public int priority() {
                        return 100;
                    }

                    @Override
                    public String name() {
                        return "stage1";
                    }
                };

        CompactionStrategy stage2 =
                new CompactionStrategy() {
                    @Override
                    public boolean shouldTrigger(ContextState state) {
                        return true;
                    }

                    @Override
                    public Mono<CompactionResult> compact(
                            List<Msg> messages, CompactionConfig cfg) {
                        executionOrder.add("stage2");
                        BoundaryMarker m = new BoundaryMarker(Instant.now(), "s2", 5, 5, 20);
                        return Mono.just(new CompactionResult(messages, 20, m));
                    }

                    @Override
                    public int priority() {
                        return 200;
                    }

                    @Override
                    public String name() {
                        return "stage2";
                    }
                };

        // Pass stage2 before stage1 — pipeline should sort by priority
        CompactionPipeline pipeline = new CompactionPipeline(List.of(stage2, stage1));
        List<Msg> msgs = sampleMessages(5);

        StepVerifier.create(pipeline.execute(msgs, Set.of(), 0.95f, config))
                .assertNext(
                        result -> {
                            assertEquals(30, result.tokensSaved()); // 10 + 20 merged
                            assertEquals(List.of("stage1", "stage2"), executionOrder);
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Only stages whose threshold is met should trigger")
    void testThresholdFiltering() {
        List<String> executed = Collections.synchronizedList(new ArrayList<>());

        CompactionStrategy lowThreshold =
                new CompactionStrategy() {
                    @Override
                    public boolean shouldTrigger(ContextState state) {
                        executed.add("lowCheck");
                        return state.pressure() >= 0.80f;
                    }

                    @Override
                    public Mono<CompactionResult> compact(
                            List<Msg> messages, CompactionConfig cfg) {
                        executed.add("lowExec");
                        BoundaryMarker m = new BoundaryMarker(Instant.now(), "low", 3, 3, 5);
                        return Mono.just(new CompactionResult(messages, 5, m));
                    }

                    @Override
                    public int priority() {
                        return 100;
                    }

                    @Override
                    public String name() {
                        return "low";
                    }
                };

        CompactionStrategy highThreshold =
                new CompactionStrategy() {
                    @Override
                    public boolean shouldTrigger(ContextState state) {
                        executed.add("highCheck");
                        return state.pressure() >= 0.98f;
                    }

                    @Override
                    public Mono<CompactionResult> compact(
                            List<Msg> messages, CompactionConfig cfg) {
                        executed.add("highExec");
                        BoundaryMarker m = new BoundaryMarker(Instant.now(), "high", 3, 3, 50);
                        return Mono.just(new CompactionResult(messages, 50, m));
                    }

                    @Override
                    public int priority() {
                        return 500;
                    }

                    @Override
                    public String name() {
                        return "high";
                    }
                };

        CompactionPipeline pipeline = new CompactionPipeline(List.of(lowThreshold, highThreshold));

        // Pressure 0.85 — only the low-threshold stage should fire
        StepVerifier.create(pipeline.execute(sampleMessages(3), Set.of(), 0.85f, config))
                .assertNext(
                        result -> {
                            assertEquals(5, result.tokensSaved());
                            assertTrue(executed.contains("lowExec"));
                            assertFalse(executed.contains("highExec"));
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("When first stage is sufficient, later stages still run if they trigger")
    void testFirstStageSufficient() {
        CompactionStrategy first = stubStrategy("first", 100, 0.80f, 1000);
        CompactionStrategy second = stubStrategy("second", 200, 0.80f, 500);

        CompactionPipeline pipeline = new CompactionPipeline(List.of(first, second));

        // Both stages trigger at 0.80 — both should execute, results are merged
        StepVerifier.create(pipeline.execute(sampleMessages(5), Set.of(), 0.90f, config))
                .assertNext(
                        result -> {
                            assertEquals(1500, result.tokensSaved()); // merged
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Circuit breaker opens after 3 consecutive failures")
    void testCircuitBreakerOpensAfter3Failures() {
        CompactionStrategy failing = failingStrategy("fail", 100, 0.0f);
        CompactionPipeline pipeline = new CompactionPipeline(List.of(failing));

        assertFalse(pipeline.isCircuitBreakerOpen());

        // Trigger 3 failures
        for (int i = 0; i < 3; i++) {
            StepVerifier.create(pipeline.execute(sampleMessages(1), Set.of(), 0.90f, config))
                    .expectError(RuntimeException.class)
                    .verify();
        }

        assertTrue(pipeline.isCircuitBreakerOpen());

        // Now pipeline should return empty (skipped)
        StepVerifier.create(pipeline.execute(sampleMessages(1), Set.of(), 0.90f, config))
                .verifyComplete();
    }

    @Test
    @DisplayName("Circuit breaker resets after a success")
    void testCircuitBreakerResetsOnSuccess() {
        CompactionStrategy failing = failingStrategy("fail", 100, 0.0f);
        CompactionPipeline pipeline = new CompactionPipeline(List.of(failing));

        // Trigger 2 failures
        for (int i = 0; i < 2; i++) {
            StepVerifier.create(pipeline.execute(sampleMessages(1), Set.of(), 0.90f, config))
                    .expectError()
                    .verify();
        }
        assertFalse(pipeline.isCircuitBreakerOpen());

        // Manually reset and verify
        pipeline.resetCircuitBreaker();
        assertFalse(pipeline.isCircuitBreakerOpen());
    }

    @Test
    @DisplayName("Empty message list should produce result with no savings")
    void testEmptyMessageList() {
        CompactionStrategy stage = stubStrategy("test", 100, 0.0f, 0);
        CompactionPipeline pipeline = new CompactionPipeline(List.of(stage));

        StepVerifier.create(pipeline.execute(List.of(), Set.of(), 0.90f, config))
                .assertNext(result -> assertEquals(0, result.tokensSaved()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Verbatim messages should be excluded from compressible set")
    void testVerbatimMessagesExcluded() {
        List<Msg> captured = new ArrayList<>();

        CompactionStrategy captureStrategy =
                new CompactionStrategy() {
                    @Override
                    public boolean shouldTrigger(ContextState state) {
                        return true;
                    }

                    @Override
                    public Mono<CompactionResult> compact(
                            List<Msg> messages, CompactionConfig cfg) {
                        captured.addAll(messages);
                        BoundaryMarker m =
                                new BoundaryMarker(
                                        Instant.now(),
                                        "capture",
                                        messages.size(),
                                        messages.size(),
                                        0);
                        return Mono.just(new CompactionResult(messages, 0, m));
                    }

                    @Override
                    public int priority() {
                        return 100;
                    }

                    @Override
                    public String name() {
                        return "capture";
                    }
                };

        CompactionPipeline pipeline = new CompactionPipeline(List.of(captureStrategy));

        Msg normalMsg =
                Msg.builder()
                        .id("normal")
                        .role(MsgRole.USER)
                        .addContent(new Content.TextContent("hello"))
                        .build();
        Msg verbatimMsg =
                Msg.builder()
                        .id("verbatim")
                        .role(MsgRole.USER)
                        .addContent(new Content.TextContent("important"))
                        .verbatimPreserved(true)
                        .build();

        Set<String> verbatimIds = Set.of("verbatim");

        StepVerifier.create(
                        pipeline.execute(
                                List.of(normalMsg, verbatimMsg), verbatimIds, 0.90f, config))
                .assertNext(
                        result -> {
                            // Only the normal message should be passed to the strategy
                            assertEquals(1, captured.size());
                            assertEquals("normal", captured.get(0).id());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("No stages triggering should return empty Mono")
    void testNoStagesTriggering() {
        CompactionStrategy highThreshold = stubStrategy("high", 100, 0.99f, 100);
        CompactionPipeline pipeline = new CompactionPipeline(List.of(highThreshold));

        // Pressure 0.5 — below threshold, no stages fire
        StepVerifier.create(pipeline.execute(sampleMessages(3), Set.of(), 0.5f, config))
                .verifyComplete();
    }
}
