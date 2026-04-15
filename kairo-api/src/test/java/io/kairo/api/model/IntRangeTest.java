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
package io.kairo.api.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class IntRangeTest {

    @Test
    void constructorValidRange() {
        IntRange range = new IntRange(0, 100);
        assertEquals(0, range.min());
        assertEquals(100, range.max());
    }

    @Test
    void constructorEqualMinMax() {
        IntRange range = new IntRange(5, 5);
        assertEquals(5, range.min());
        assertEquals(5, range.max());
    }

    @Test
    void constructorMinGreaterThanMaxThrows() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> new IntRange(10, 5));
        assertTrue(ex.getMessage().contains("min"));
        assertTrue(ex.getMessage().contains("max"));
    }

    @Test
    void clampBelowMin() {
        IntRange range = new IntRange(10, 20);
        assertEquals(10, range.clamp(5));
    }

    @Test
    void clampAboveMax() {
        IntRange range = new IntRange(10, 20);
        assertEquals(20, range.clamp(25));
    }

    @Test
    void clampWithinRange() {
        IntRange range = new IntRange(10, 20);
        assertEquals(15, range.clamp(15));
    }

    @Test
    void clampAtBoundaries() {
        IntRange range = new IntRange(10, 20);
        assertEquals(10, range.clamp(10));
        assertEquals(20, range.clamp(20));
    }

    @Test
    void lerpAtZero() {
        IntRange range = new IntRange(0, 100);
        assertEquals(0, range.lerp(0.0));
    }

    @Test
    void lerpAtOne() {
        IntRange range = new IntRange(0, 100);
        assertEquals(100, range.lerp(1.0));
    }

    @Test
    void lerpAtHalf() {
        IntRange range = new IntRange(0, 100);
        assertEquals(50, range.lerp(0.5));
    }

    @Test
    void lerpClampsBelowZero() {
        IntRange range = new IntRange(0, 100);
        assertEquals(0, range.lerp(-0.5));
    }

    @Test
    void lerpClampsAboveOne() {
        IntRange range = new IntRange(0, 100);
        assertEquals(100, range.lerp(1.5));
    }
}
