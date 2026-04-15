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

/**
 * An inclusive integer range with clamping and interpolation utilities.
 *
 * @param min the minimum value (inclusive)
 * @param max the maximum value (inclusive)
 */
public record IntRange(int min, int max) {

    public IntRange {
        if (min > max) {
            throw new IllegalArgumentException("min (" + min + ") > max (" + max + ")");
        }
    }

    /**
     * Clamp the given value to this range.
     *
     * @param value the value to clamp
     * @return the clamped value
     */
    public int clamp(int value) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Linearly interpolate within this range.
     *
     * @param t interpolation factor, clamped to [0, 1]
     * @return the interpolated value
     */
    public int lerp(double t) {
        return min + (int) ((max - min) * Math.max(0, Math.min(1, t)));
    }
}
