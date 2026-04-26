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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CacheCheckResultTest {

    @Test
    void fieldAccessorsReturnConstructorValues() {
        CacheCheckResult result = new CacheCheckResult(100, 50, 200);
        assertEquals(100, result.cacheReadTokens());
        assertEquals(50, result.cacheCreationTokens());
        assertEquals(200, result.inputTokens());
    }

    @Test
    void hitRatioZeroWhenTotalIsZero() {
        CacheCheckResult result = new CacheCheckResult(0, 0, 0);
        assertEquals(0.0, result.hitRatio(), 0.001);
    }

    @Test
    void hitRatioOneWhenAllCacheRead() {
        CacheCheckResult result = new CacheCheckResult(100, 0, 0);
        assertEquals(1.0, result.hitRatio(), 0.001);
    }

    @Test
    void hitRatioZeroWhenNoCacheRead() {
        CacheCheckResult result = new CacheCheckResult(0, 100, 0);
        assertEquals(0.0, result.hitRatio(), 0.001);
    }

    @Test
    void hitRatioMixedScenario() {
        // read=75, creation=25 → ratio = 75/100 = 0.75
        CacheCheckResult result = new CacheCheckResult(75, 25, 0);
        assertEquals(0.75, result.hitRatio(), 0.001);
    }

    @Test
    void initialCacheBrokenIsFalse() {
        CacheCheckResult result = new CacheCheckResult(0, 0, 0);
        assertFalse(result.isCacheBroken());
    }

    @Test
    void setCacheBrokenToTrue() {
        CacheCheckResult result = new CacheCheckResult(0, 0, 0);
        result.setCacheBroken(true);
        assertTrue(result.isCacheBroken());
    }

    @Test
    void initialReasonsIsEmpty() {
        CacheCheckResult result = new CacheCheckResult(0, 0, 0);
        assertTrue(result.reasons().isEmpty());
    }

    @Test
    void addReasonAppendsToList() {
        CacheCheckResult result = new CacheCheckResult(0, 0, 0);
        result.addReason("static prefix changed");
        assertEquals(1, result.reasons().size());
        assertEquals("static prefix changed", result.reasons().get(0));
    }

    @Test
    void addMultipleReasonsPreservesOrder() {
        CacheCheckResult result = new CacheCheckResult(0, 0, 0);
        result.addReason("reason-1");
        result.addReason("reason-2");
        assertEquals(2, result.reasons().size());
        assertEquals("reason-1", result.reasons().get(0));
        assertEquals("reason-2", result.reasons().get(1));
    }
}
