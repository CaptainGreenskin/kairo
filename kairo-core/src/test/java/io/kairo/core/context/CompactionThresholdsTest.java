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

import io.kairo.api.context.*;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.core.context.compaction.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CompactionThresholdsTest {

    // ---- CompactionThresholds record tests ----

    @Nested
    @DisplayName("CompactionThresholds record")
    class RecordTests {

        @Test
        @DisplayName("DEFAULTS instance has correct sensible defaults")
        void defaultsHaveCorrectValues() {
            CompactionThresholds d = CompactionThresholds.DEFAULTS;
            assertEquals(0.80f, d.triggerPressure());
            assertEquals(0.80f, d.snipPressure());
            assertEquals(0.85f, d.microPressure());
            assertEquals(0.90f, d.collapsePressure());
            assertEquals(0.95f, d.autoPressure());
            assertEquals(0.98f, d.partialPressure());
            assertEquals(3, d.cbFailureLimit());
            assertEquals(30, d.cbCooldownSeconds());
            assertEquals(13_000, d.bufferTokens());
        }

        @Test
        @DisplayName("No-arg constructor produces same values as DEFAULTS")
        void noArgConstructorMatchesDefaults() {
            CompactionThresholds fresh = new CompactionThresholds();
            assertEquals(CompactionThresholds.DEFAULTS, fresh);
        }

        @Test
        @DisplayName("Builder allows partial overrides")
        void builderPartialOverride() {
            CompactionThresholds t =
                    CompactionThresholds.builder().triggerPressure(0.70f).cbFailureLimit(5).build();
            assertEquals(0.70f, t.triggerPressure());
            assertEquals(5, t.cbFailureLimit());
            // other fields should keep defaults
            assertEquals(0.85f, t.microPressure());
            assertEquals(13_000, t.bufferTokens());
        }

        @Test
        @DisplayName("Invalid values are corrected to defaults")
        void invalidValuesCorrectedToDefaults() {
            CompactionThresholds t =
                    new CompactionThresholds(0.0f, -1.0f, 1.5f, 0.90f, 0.95f, 0.98f, -1, -5, -100);
            assertEquals(CompactionThresholds.DEFAULT_TRIGGER_PRESSURE, t.triggerPressure());
            assertEquals(CompactionThresholds.DEFAULT_SNIP_PRESSURE, t.snipPressure());
            assertEquals(CompactionThresholds.DEFAULT_MICRO_PRESSURE, t.microPressure());
            assertEquals(CompactionThresholds.DEFAULT_CB_FAILURE_LIMIT, t.cbFailureLimit());
            assertEquals(CompactionThresholds.DEFAULT_CB_COOLDOWN_SECONDS, t.cbCooldownSeconds());
            assertEquals(CompactionThresholds.DEFAULT_BUFFER_TOKENS, t.bufferTokens());
        }
    }

    // ---- CompactionPolicyDefaults delegation ----

    @Test
    @DisplayName("CompactionPolicyDefaults delegates to CompactionThresholds constants")
    void policyDefaultsDelegateToThresholds() {
        assertEquals(
                CompactionThresholds.DEFAULT_TRIGGER_PRESSURE,
                CompactionPolicyDefaults.PRESSURE_THRESHOLD);
        assertEquals(
                CompactionThresholds.DEFAULT_CB_FAILURE_LIMIT,
                CompactionPolicyDefaults.PIPELINE_CIRCUIT_BREAKER_THRESHOLD);
        assertEquals(
                CompactionThresholds.DEFAULT_CB_COOLDOWN_SECONDS,
                CompactionPolicyDefaults.PIPELINE_CIRCUIT_BREAKER_COOLDOWN_SECONDS);
    }

    // ---- Stage trigger threshold configurability ----

    @Nested
    @DisplayName("Configurable stage thresholds")
    class StageThresholdTests {

        private ContextState state(float pressure) {
            return new ContextState(0, 0, pressure, 10, 0);
        }

        @Test
        @DisplayName("SnipCompaction uses configurable threshold")
        void snipUsesConfigurableThreshold() {
            SnipCompaction defaultSnip = new SnipCompaction();
            SnipCompaction lowSnip = new SnipCompaction(0.50f);

            ContextState lowPressure = state(0.60f);
            assertFalse(
                    defaultSnip.shouldTrigger(lowPressure),
                    "default 0.80 should not trigger at 0.60");
            assertTrue(lowSnip.shouldTrigger(lowPressure), "custom 0.50 should trigger at 0.60");
        }

        @Test
        @DisplayName("MicroCompaction uses configurable threshold")
        void microUsesConfigurableThreshold() {
            MicroCompaction defaultMicro = new MicroCompaction();
            MicroCompaction lowMicro = new MicroCompaction(0.50f);

            ContextState lowPressure = state(0.60f);
            assertFalse(defaultMicro.shouldTrigger(lowPressure));
            assertTrue(lowMicro.shouldTrigger(lowPressure));
        }

        @Test
        @DisplayName("CollapseCompaction uses configurable threshold")
        void collapseUsesConfigurableThreshold() {
            CollapseCompaction defaultCollapse = new CollapseCompaction();
            CollapseCompaction lowCollapse = new CollapseCompaction(0.50f);

            ContextState lowPressure = state(0.60f);
            assertFalse(defaultCollapse.shouldTrigger(lowPressure));
            assertTrue(lowCollapse.shouldTrigger(lowPressure));
        }

        @Test
        @DisplayName("PartialCompaction uses configurable threshold")
        void partialUsesConfigurableThreshold() {
            PartialCompaction defaultPartial = new PartialCompaction();
            PartialCompaction lowPartial = new PartialCompaction(0.50f);

            ContextState lowPressure = state(0.60f);
            assertFalse(defaultPartial.shouldTrigger(lowPressure));
            assertTrue(lowPartial.shouldTrigger(lowPressure));
        }
    }

    // ---- Pipeline with custom thresholds ----

    @Nested
    @DisplayName("Pipeline with custom thresholds")
    class PipelineTests {

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
        @DisplayName("Custom CB failure limit is respected by pipeline")
        void customCbLimitRespected() {
            CompactionThresholds thresholds =
                    CompactionThresholds.builder().cbFailureLimit(1).cbCooldownSeconds(60).build();

            CompactionStrategy failing =
                    new CompactionStrategy() {
                        @Override
                        public boolean shouldTrigger(ContextState state) {
                            return true;
                        }

                        @Override
                        public Mono<CompactionResult> compact(
                                List<Msg> messages, CompactionConfig cfg) {
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
            CompactionConfig config = new CompactionConfig(100_000, true, null);

            assertFalse(pipeline.isCircuitBreakerOpen());

            // One failure should open the CB (limit = 1)
            StepVerifier.create(pipeline.execute(sampleMessages(1), Set.of(), 0.90f, config))
                    .expectError(RuntimeException.class)
                    .verify();

            assertTrue(
                    pipeline.isCircuitBreakerOpen(), "CB should open after 1 failure with limit=1");
        }

        @Test
        @DisplayName("Default thresholds pipeline produces identical behavior to hardcoded values")
        void defaultThresholdsPreserveBehavior() {
            // Create pipeline with explicit defaults
            CompactionPipeline defaultPipeline =
                    new CompactionPipeline(
                            (io.kairo.api.model.ModelProvider) null,
                            null,
                            null,
                            CompactionThresholds.DEFAULTS);

            assertFalse(defaultPipeline.isCircuitBreakerOpen());

            // A stub strategy at 0.80 should trigger at pressure 0.85
            CompactionStrategy stub =
                    new CompactionStrategy() {
                        @Override
                        public boolean shouldTrigger(ContextState state) {
                            return state.pressure() >= 0.80f;
                        }

                        @Override
                        public Mono<CompactionResult> compact(
                                List<Msg> messages, CompactionConfig cfg) {
                            BoundaryMarker m =
                                    new BoundaryMarker(
                                            Instant.now(),
                                            "test",
                                            messages.size(),
                                            messages.size(),
                                            42);
                            return Mono.just(new CompactionResult(messages, 42, m));
                        }

                        @Override
                        public int priority() {
                            return 100;
                        }

                        @Override
                        public String name() {
                            return "test";
                        }
                    };

            CompactionPipeline pipeline =
                    new CompactionPipeline(
                            List.of(stub), null, null, CompactionThresholds.DEFAULTS);
            CompactionConfig config = new CompactionConfig(100_000, true, null);

            StepVerifier.create(pipeline.execute(sampleMessages(3), Set.of(), 0.85f, config))
                    .assertNext(result -> assertEquals(42, result.tokensSaved()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Lower trigger threshold causes compaction to trigger sooner")
        void lowerThresholdCompactsSooner() {
            CompactionThresholds lowThresholds =
                    CompactionThresholds.builder().snipPressure(0.50f).build();

            // At pressure 0.60, default SnipCompaction (0.80) should NOT trigger
            // but a custom one at 0.50 SHOULD trigger
            SnipCompaction defaultSnip = new SnipCompaction();
            SnipCompaction customSnip = new SnipCompaction(lowThresholds.snipPressure());

            ContextState state = new ContextState(0, 0, 0.60f, 10, 0);
            assertFalse(defaultSnip.shouldTrigger(state));
            assertTrue(customSnip.shouldTrigger(state));
        }
    }

    // ---- TokenBudgetManager buffer configurability ----

    @Nested
    @DisplayName("TokenBudgetManager buffer configurability")
    class BufferTests {

        @Test
        @DisplayName("Default buffer is 13000")
        void defaultBufferIs13000() {
            TokenBudgetManager tbm = new TokenBudgetManager(200_000, 8_096);
            // effective = 200000 - 8096 - 13000 = 178904
            assertEquals(178_904, tbm.getEffectiveBudget());
        }

        @Test
        @DisplayName("Custom buffer changes effective budget")
        void customBufferChangesEffectiveBudget() {
            TokenBudgetManager tbm =
                    new TokenBudgetManager(200_000, 8_096, new HeuristicTokenEstimator(), 5_000);
            // effective = 200000 - 8096 - 5000 = 186904
            assertEquals(186_904, tbm.getEffectiveBudget());
        }

        @Test
        @DisplayName("Negative buffer falls back to default")
        void negativeBufferFallsBackToDefault() {
            TokenBudgetManager tbm =
                    new TokenBudgetManager(200_000, 8_096, new HeuristicTokenEstimator(), -1);
            // Should use DEFAULT_BUFFER (13000)
            assertEquals(178_904, tbm.getEffectiveBudget());
        }

        @Test
        @DisplayName("Zero buffer is allowed")
        void zeroBufferIsAllowed() {
            TokenBudgetManager tbm =
                    new TokenBudgetManager(200_000, 8_096, new HeuristicTokenEstimator(), 0);
            // effective = 200000 - 8096 - 0 = 191904
            assertEquals(191_904, tbm.getEffectiveBudget());
        }
    }

    // ---- DefaultContextManager with thresholds ----

    @Nested
    @DisplayName("DefaultContextManager threshold wiring")
    class ContextManagerTests {

        @Test
        @DisplayName("Custom thresholds change compaction trigger point")
        void customThresholdsChangeTriggerPoint() {
            CompactionThresholds lowThresholds =
                    CompactionThresholds.builder().triggerPressure(0.50f).build();

            TokenBudgetManager budget = new TokenBudgetManager(1000, 100);
            DefaultContextManager cm = new DefaultContextManager(budget, null, lowThresholds);

            // Add enough messages to reach 0.60 pressure (above 0.50 threshold)
            // effective budget = 1000 - 100 = 900
            // For 0.60 pressure, need 540 tokens
            for (int i = 0; i < 6; i++) {
                cm.addMessage(
                        Msg.builder()
                                .id("m-" + i)
                                .role(MsgRole.USER)
                                .addContent(new Content.TextContent("hello"))
                                .tokenCount(90)
                                .build());
            }

            // At 0.60 pressure, needs compaction with 0.50 threshold
            assertTrue(cm.needsCompaction(cm.getMessages()));
        }

        @Test
        @DisplayName("Default thresholds don't trigger at low pressure")
        void defaultThresholdsNoTriggerAtLowPressure() {
            // Use a large budget so that small messages produce low pressure
            TokenBudgetManager budget = new TokenBudgetManager(200_000, 8_096);
            DefaultContextManager cm = new DefaultContextManager(budget, null, null);

            // Add just a few small messages — well under 0.80 pressure
            for (int i = 0; i < 3; i++) {
                cm.addMessage(
                        Msg.builder()
                                .id("m-" + i)
                                .role(MsgRole.USER)
                                .addContent(new Content.TextContent("hello"))
                                .tokenCount(100)
                                .build());
            }

            // With 300 tokens used on a 200K budget, pressure is well below 0.80
            assertFalse(cm.needsCompaction(cm.getMessages()));
        }
    }
}
