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

    // ---- contextWindow == 0: pressure-only fallback ----

    @Test
    void zeroContextWindowBelowThresholdReturnsFalse() {
        // pressure 0.70 < threshold 0.80
        ContextState state = new ContextState(100_000, 70_000, 0.70f, 50, 0);
        assertFalse(HybridThreshold.shouldTrigger(state, 0.80f, 40_000));
    }

    @Test
    void zeroContextWindowAtThresholdReturnsTrue() {
        // pressure 0.80 >= threshold 0.80
        ContextState state = new ContextState(100_000, 80_000, 0.80f, 50, 0);
        assertTrue(HybridThreshold.shouldTrigger(state, 0.80f, 40_000));
    }

    @Test
    void zeroContextWindowAboveThresholdReturnsTrue() {
        // pressure 0.95 >= threshold 0.80
        ContextState state = new ContextState(100_000, 95_000, 0.95f, 50, 0);
        assertTrue(HybridThreshold.shouldTrigger(state, 0.80f, 40_000));
    }

    // ---- contextWindow > 0: min(percentage, absolute) ----

    @Test
    void percentageTriggerIsEffectiveWhenLower() {
        // contextWindow=100_000, threshold=0.80 → percentageTrigger=80_000
        // absoluteTrigger = 100_000 - 40_000 = 60_000
        // effectiveThreshold = min(80_000, 60_000) = 60_000
        // usedTokens=65_000 >= 60_000 → true
        ContextState state = new ContextState(100_000, 65_000, 0.65f, 50, 100_000);
        assertTrue(HybridThreshold.shouldTrigger(state, 0.80f, 40_000));
    }

    @Test
    void absoluteTriggerIsEffectiveWhenLower() {
        // percentageTrigger = 0.90 * 100_000 = 90_000
        // absoluteTrigger = 100_000 - 5_000 = 95_000
        // effectiveThreshold = min(90_000, 95_000) = 90_000
        // usedTokens=85_000 < 90_000 → false
        ContextState state = new ContextState(100_000, 85_000, 0.85f, 50, 100_000);
        assertFalse(HybridThreshold.shouldTrigger(state, 0.90f, 5_000));
    }

    @Test
    void triggerExactlyAtEffectiveThreshold() {
        // effectiveThreshold = min(0.80 * 100_000, 100_000 - 40_000) = min(80_000, 60_000) = 60_000
        // usedTokens=60_000 == 60_000 → true
        ContextState state = new ContextState(100_000, 60_000, 0.60f, 50, 100_000);
        assertTrue(HybridThreshold.shouldTrigger(state, 0.80f, 40_000));
    }

    @Test
    void belowEffectiveThresholdReturnsFalse() {
        // effectiveThreshold = min(80_000, 60_000) = 60_000
        // usedTokens=55_000 < 60_000 → false
        ContextState state = new ContextState(100_000, 55_000, 0.55f, 50, 100_000);
        assertFalse(HybridThreshold.shouldTrigger(state, 0.80f, 40_000));
    }

    // ---- usedTokens == 0: derive from pressure ----

    @Test
    void usedTokensZeroDerivedFromPressureTriggers() {
        // usedTokens=0, pressure=0.85, contextWindow=100_000
        // derived usedTokens = 0.85 * 100_000 = 85_000
        // effectiveThreshold = min(0.80 * 100_000, 100_000 - 10_000) = min(80_000, 90_000) = 80_000
        // 85_000 >= 80_000 → true
        ContextState state = new ContextState(100_000, 0, 0.85f, 50, 100_000);
        assertTrue(HybridThreshold.shouldTrigger(state, 0.80f, 10_000));
    }

    @Test
    void usedTokensZeroDerivedFromPressureDoesNotTrigger() {
        // usedTokens=0, pressure=0.50, contextWindow=100_000
        // derived usedTokens = 0.50 * 100_000 = 50_000
        // effectiveThreshold = min(80_000, 60_000) = 60_000
        // 50_000 < 60_000 → false
        ContextState state = new ContextState(100_000, 0, 0.50f, 50, 100_000);
        assertFalse(HybridThreshold.shouldTrigger(state, 0.80f, 40_000));
    }

    // ---- edge cases ----

    @Test
    void zeroPressureZeroContextWindowReturnsFalse() {
        ContextState state = new ContextState(0, 0, 0.0f, 0, 0);
        assertFalse(HybridThreshold.shouldTrigger(state, 0.80f, 40_000));
    }

    @Test
    void veryLargeAbsoluteBufferMakesAbsoluteTriggerNegative() {
        // absoluteTrigger = 100_000 - 200_000 = -100_000 (negative)
        // percentageTrigger = 0.80 * 100_000 = 80_000
        // effectiveThreshold = min(80_000, -100_000) = -100_000
        // usedTokens=1 >= -100_000 → true (always triggers when buffer > window)
        ContextState state = new ContextState(100_000, 1, 0.01f, 1, 100_000);
        assertTrue(HybridThreshold.shouldTrigger(state, 0.80f, 200_000));
    }
}
