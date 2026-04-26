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

import io.kairo.api.context.ContextState;
import org.junit.jupiter.api.Test;

class HybridThresholdTest {

    private static ContextState state(int usedTokens, float pressure, int contextWindow) {
        return new ContextState(contextWindow, usedTokens, pressure, 10, contextWindow);
    }

    @Test
    void triggersWhenUsedTokensExceedsPercentageThreshold() {
        // 80% of 100_000 = 80_000; absoluteTrigger = 100_000 - 40_000 = 60_000; effective = 60_000
        ContextState s = state(65_000, 0.65f, 100_000);
        assertThat(HybridThreshold.shouldTrigger(s, 0.80f, 40_000)).isTrue();
    }

    @Test
    void doesNotTriggerWhenUsedTokensBelowEffectiveThreshold() {
        // effective = min(80_000, 60_000) = 60_000; used = 50_000 < 60_000
        ContextState s = state(50_000, 0.50f, 100_000);
        assertThat(HybridThreshold.shouldTrigger(s, 0.80f, 40_000)).isFalse();
    }

    @Test
    void absoluteBufferConstraintDominatesWhenTight() {
        // percentage trigger = 0.95 * 100_000 = 95_000
        // absolute trigger = 100_000 - 60_000 = 40_000
        // effective = min(95_000, 40_000) = 40_000; used = 45_000 >= 40_000
        ContextState s = state(45_000, 0.45f, 100_000);
        assertThat(HybridThreshold.shouldTrigger(s, 0.95f, 60_000)).isTrue();
    }

    @Test
    void percentageConstraintDominatesWhenBufferLarge() {
        // percentage trigger = 0.50 * 100_000 = 50_000
        // absolute trigger = 100_000 - 10_000 = 90_000
        // effective = min(50_000, 90_000) = 50_000; used = 45_000 < 50_000
        ContextState s = state(45_000, 0.45f, 100_000);
        assertThat(HybridThreshold.shouldTrigger(s, 0.50f, 10_000)).isFalse();
    }

    @Test
    void atEffectiveBoundaryTriggers() {
        // effective = min(80_000, 60_000) = 60_000; used = 60_000 => triggers
        ContextState s = state(60_000, 0.60f, 100_000);
        assertThat(HybridThreshold.shouldTrigger(s, 0.80f, 40_000)).isTrue();
    }

    @Test
    void zeroContextWindowFallsBackToPercentage_triggersWhenPressureAbove() {
        // contextWindow == 0 → fallback: pressure >= percentageThreshold
        ContextState s = new ContextState(0, 0, 0.90f, 5);
        assertThat(HybridThreshold.shouldTrigger(s, 0.80f, 40_000)).isTrue();
    }

    @Test
    void zeroContextWindowFallsBackToPercentage_doesNotTriggerWhenPressureBelow() {
        ContextState s = new ContextState(0, 0, 0.70f, 5);
        assertThat(HybridThreshold.shouldTrigger(s, 0.80f, 40_000)).isFalse();
    }

    @Test
    void derivesUsedTokensFromPressureWhenZero() {
        // usedTokens=0, pressure=0.70 → derived used = 0.70 * 100_000 = 70_000
        // effective = min(80_000, 60_000) = 60_000; 70_000 >= 60_000 → true
        ContextState s = state(0, 0.70f, 100_000);
        assertThat(HybridThreshold.shouldTrigger(s, 0.80f, 40_000)).isTrue();
    }

    @Test
    void derivedUsedTokensBelowThresholdDoesNotTrigger() {
        // usedTokens=0, pressure=0.50 → derived used = 0.50 * 100_000 = 50_000
        // effective = min(80_000, 60_000) = 60_000; 50_000 < 60_000 → false
        ContextState s = state(0, 0.50f, 100_000);
        assertThat(HybridThreshold.shouldTrigger(s, 0.80f, 40_000)).isFalse();
    }
}
