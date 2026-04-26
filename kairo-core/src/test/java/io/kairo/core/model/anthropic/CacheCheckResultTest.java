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
    void constructor_storesFields() {
        var result = new CacheCheckResult(100, 50, 200);

        assertThat(result.cacheReadTokens()).isEqualTo(100);
        assertThat(result.cacheCreationTokens()).isEqualTo(50);
        assertThat(result.inputTokens()).isEqualTo(200);
    }

    @Test
    void hitRatio_withCacheHits_returnsCorrectRatio() {
        var result = new CacheCheckResult(75, 25, 100);

        assertThat(result.hitRatio()).isCloseTo(0.75, within(1e-9));
    }

    @Test
    void hitRatio_allCreation_returnsZero() {
        var result = new CacheCheckResult(0, 100, 100);

        assertThat(result.hitRatio()).isEqualTo(0.0);
    }

    @Test
    void hitRatio_noTokens_returnsZero() {
        var result = new CacheCheckResult(0, 0, 0);

        assertThat(result.hitRatio()).isEqualTo(0.0);
    }

    @Test
    void isCacheBroken_defaultsFalse() {
        var result = new CacheCheckResult(10, 10, 20);

        assertThat(result.isCacheBroken()).isFalse();
    }

    @Test
    void setCacheBroken_setsTrue() {
        var result = new CacheCheckResult(10, 10, 20);
        result.setCacheBroken(true);

        assertThat(result.isCacheBroken()).isTrue();
    }

    @Test
    void addReason_accumulatesReasons() {
        var result = new CacheCheckResult(10, 10, 20);
        result.addReason("reason1");
        result.addReason("reason2");

        assertThat(result.reasons()).containsExactly("reason1", "reason2");
    }

    @Test
    void reasons_defaultsEmpty() {
        var result = new CacheCheckResult(0, 0, 0);

        assertThat(result.reasons()).isEmpty();
    }
}
