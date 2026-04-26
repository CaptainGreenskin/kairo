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

    @Test
    void zeroContextWindowFallsBackToPressure() {
        // contextWindow = 0 → pressure-only fallback
        ContextState state = new ContextState(0, 0, 0.85f, 5);
        assertThat(HybridThreshold.shouldTrigger(state, 0.85f, 10_000)).isTrue();
    }

    @Test
    void zeroContextWindowBelowPressureReturnsFalse() {
        ContextState state = new ContextState(0, 0, 0.84f, 5);
        assertThat(HybridThreshold.shouldTrigger(state, 0.85f, 10_000)).isFalse();
    }

    @Test
    void percentageTriggerWithContextWindow() {
        // contextWindow = 100_000, threshold = 0.85, absoluteBuffer = 50_000
        // percentageTrigger = 85_000, absoluteTrigger = 50_000 → effectiveThreshold = 50_000
        // usedTokens = 50_000 >= 50_000 → true
        ContextState state = new ContextState(0, 50_000, 0.5f, 5, 100_000);
        assertThat(HybridThreshold.shouldTrigger(state, 0.85f, 50_000)).isTrue();
    }

    @Test
    void absoluteBufferTakesPrecedenceOverPercentage() {
        // contextWindow = 100_000, threshold = 0.95, absoluteBuffer = 30_000
        // percentageTrigger = 95_000, absoluteTrigger = 70_000 → effectiveThreshold = 70_000
        // usedTokens = 71_000 >= 70_000 → true
        ContextState state = new ContextState(0, 71_000, 0.71f, 5, 100_000);
        assertThat(HybridThreshold.shouldTrigger(state, 0.95f, 30_000)).isTrue();
    }

    @Test
    void belowEffectiveThresholdReturnsFalse() {
        // effectiveThreshold = min(0.85*100_000, 100_000-30_000) = min(85_000, 70_000) = 70_000
        // usedTokens = 69_000 < 70_000 → false
        ContextState state = new ContextState(0, 69_000, 0.69f, 5, 100_000);
        assertThat(HybridThreshold.shouldTrigger(state, 0.85f, 30_000)).isFalse();
    }

    @Test
    void zeroUsedTokensDerivesFromPressure() {
        // contextWindow = 100_000, usedTokens = 0, pressure = 0.90
        // → usedTokens derived as 0.90 * 100_000 = 90_000
        // effectiveThreshold = min(0.85*100_000, 100_000-5_000) = min(85_000, 95_000) = 85_000
        // 90_000 >= 85_000 → true
        ContextState state = new ContextState(0, 0, 0.90f, 5, 100_000);
        assertThat(HybridThreshold.shouldTrigger(state, 0.85f, 5_000)).isTrue();
    }
}
