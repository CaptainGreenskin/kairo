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
package io.kairo.core.model.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class CacheCheckResultTest {

    @Test
    void constructorDoesNotThrow() {
        CacheCheckResult result = new CacheCheckResult(100, 50, 200);
        assertThat(result).isNotNull();
    }

    @Test
    void accessorsReturnConstructorValues() {
        CacheCheckResult result = new CacheCheckResult(100, 50, 200);
        assertThat(result.cacheReadTokens()).isEqualTo(100);
        assertThat(result.cacheCreationTokens()).isEqualTo(50);
        assertThat(result.inputTokens()).isEqualTo(200);
    }

    @Test
    void hitRatioIsZeroWhenNoTokens() {
        CacheCheckResult result = new CacheCheckResult(0, 0, 0);
        assertThat(result.hitRatio()).isCloseTo(0.0, within(0.001));
    }

    @Test
    void hitRatioCalculation() {
        // 100 read, 100 creation → 50% hit ratio
        CacheCheckResult result = new CacheCheckResult(100, 100, 0);
        assertThat(result.hitRatio()).isCloseTo(0.5, within(0.001));
    }

    @Test
    void hitRatioIsOneWhenAllRead() {
        CacheCheckResult result = new CacheCheckResult(200, 0, 0);
        assertThat(result.hitRatio()).isCloseTo(1.0, within(0.001));
    }

    @Test
    void cacheBrokenDefaultsFalse() {
        CacheCheckResult result = new CacheCheckResult(10, 5, 20);
        assertThat(result.isCacheBroken()).isFalse();
    }

    @Test
    void setCacheBrokenChangesState() {
        CacheCheckResult result = new CacheCheckResult(10, 5, 20);
        result.setCacheBroken(true);
        assertThat(result.isCacheBroken()).isTrue();
    }

    @Test
    void reasonsInitiallyEmpty() {
        CacheCheckResult result = new CacheCheckResult(0, 0, 0);
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    void addReasonAppendsToList() {
        CacheCheckResult result = new CacheCheckResult(0, 0, 0);
        result.addReason("system prompt changed");
        result.addReason("tools modified");
        assertThat(result.reasons()).containsExactly("system prompt changed", "tools modified");
    }
}
