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

import io.kairo.api.context.ContextState;
import org.junit.jupiter.api.Test;

class HybridThresholdTest {

    // contextWindow=0 branch: fallback to pressure-only

    @Test
    void noContextWindowPressureAboveThresholdTriggers() {
        // pressure=0.85, threshold=0.80 → should trigger
        ContextState state = new ContextState(100_000, 0, 0.85f, 10);
        assertTrue(HybridThreshold.shouldTrigger(state, 0.80f, 40_000));
    }

    @Test
    void noContextWindowPressureBelowThresholdDoesNotTrigger() {
        // pressure=0.70, threshold=0.80 → should not trigger
        ContextState state = new ContextState(100_000, 0, 0.70f, 10);
        assertFalse(HybridThreshold.shouldTrigger(state, 0.80f, 40_000));
    }

    @Test
    void noContextWindowPressureAtThresholdTriggers() {
        // pressure=0.80, threshold=0.80 → should trigger (>=)
        ContextState state = new ContextState(100_000, 0, 0.80f, 10);
        assertTrue(HybridThreshold.shouldTrigger(state, 0.80f, 40_000));
    }

    // contextWindow > 0 branch: hybrid min(percentage, absolute) logic

    @Test
    void hybridUsedTokensAbovePercentageTriggers() {
        // contextWindow=100_000, threshold=0.80 → percentageTrigger=80_000
        // absoluteTrigger=100_000-40_000=60_000 → effectiveThreshold=60_000
        // usedTokens=70_000 >= 60_000 → true
        ContextState state = new ContextState(100_000, 70_000, 0.70f, 10, 100_000);
        assertTrue(HybridThreshold.shouldTrigger(state, 0.80f, 40_000));
    }

    @Test
    void hybridUsedTokensBelowEffectiveThresholdDoesNotTrigger() {
        // effectiveThreshold=60_000, usedTokens=50_000 → false
        ContextState state = new ContextState(100_000, 50_000, 0.50f, 10, 100_000);
        assertFalse(HybridThreshold.shouldTrigger(state, 0.80f, 40_000));
    }

    @Test
    void hybridAbsoluteBufferDominatesWhenSmaller() {
        // contextWindow=100_000, threshold=0.95 → percentageTrigger=95_000
        // absoluteTrigger=100_000-40_000=60_000 → effectiveThreshold=60_000
        // usedTokens=65_000 >= 60_000 → true
        ContextState state = new ContextState(100_000, 65_000, 0.65f, 10, 100_000);
        assertTrue(HybridThreshold.shouldTrigger(state, 0.95f, 40_000));
    }

    @Test
    void hybridPercentageDominatesWhenSmaller() {
        // contextWindow=100_000, threshold=0.50 → percentageTrigger=50_000
        // absoluteTrigger=100_000-5_000=95_000 → effectiveThreshold=50_000
        // usedTokens=55_000 >= 50_000 → true
        ContextState state = new ContextState(100_000, 55_000, 0.55f, 10, 100_000);
        assertTrue(HybridThreshold.shouldTrigger(state, 0.50f, 5_000));
    }

    @Test
    void hybridUsedTokensZeroDerivesFromPressure() {
        // usedTokens=0, pressure=0.70, contextWindow=100_000
        // derived usedTokens = 0.70 * 100_000 = 70_000
        // effectiveThreshold = min(80_000, 60_000) = 60_000
        // 70_000 >= 60_000 → true
        ContextState state = new ContextState(100_000, 0, 0.70f, 10, 100_000);
        assertTrue(HybridThreshold.shouldTrigger(state, 0.80f, 40_000));
    }

    @Test
    void hybridUsedTokensZeroPressureZeroDoesNotTrigger() {
        // usedTokens=0, pressure=0 → derived usedTokens stays 0
        // 0 < 60_000 → false
        ContextState state = new ContextState(100_000, 0, 0.0f, 0, 100_000);
        assertFalse(HybridThreshold.shouldTrigger(state, 0.80f, 40_000));
    }
}
