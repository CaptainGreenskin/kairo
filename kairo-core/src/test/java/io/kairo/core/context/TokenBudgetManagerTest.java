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

import io.kairo.api.context.TokenBudget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenBudgetManagerTest {

    @Test
    @DisplayName("forClaude200K factory creates correct budget")
    void testClaude200KFactory() {
        TokenBudgetManager mgr = TokenBudgetManager.forClaude200K();

        assertEquals(0, mgr.used());
        // effective = 200_000 - 8_096 = 191_904
        assertEquals(191_904, mgr.remaining());
        assertEquals(0.0f, mgr.pressure(), 0.001f);
    }

    @Test
    @DisplayName("forClaude1M factory creates correct budget")
    void testClaude1MFactory() {
        TokenBudgetManager mgr = TokenBudgetManager.forClaude1M();

        assertEquals(0, mgr.used());
        assertEquals(1_000_000 - 16_000, mgr.remaining());
        assertEquals(0.0f, mgr.pressure(), 0.001f);
    }

    @Test
    @DisplayName("recordUsage increments used tokens")
    void testRecordUsage() {
        TokenBudgetManager mgr = new TokenBudgetManager(10_000, 1_000);

        mgr.recordUsage(500);
        assertEquals(500, mgr.used());
        assertEquals(10_000 - 1_000 - 500, mgr.remaining());

        mgr.recordUsage(300);
        assertEquals(800, mgr.used());
    }

    @Test
    @DisplayName("releaseUsage decrements used tokens")
    void testReleaseUsage() {
        TokenBudgetManager mgr = new TokenBudgetManager(10_000, 1_000);

        mgr.recordUsage(500);
        mgr.releaseUsage(200);
        assertEquals(300, mgr.used());
    }

    @Test
    @DisplayName("pressure calculation is correct")
    void testPressureCalculation() {
        TokenBudgetManager mgr = new TokenBudgetManager(10_000, 0);

        mgr.recordUsage(5_000);
        assertEquals(0.5f, mgr.pressure(), 0.001f);

        mgr.recordUsage(5_000);
        assertEquals(1.0f, mgr.pressure(), 0.001f);
    }

    @Test
    @DisplayName("pressure returns 1.0 when effective budget is zero or negative")
    void testPressureEdgeCase() {
        // reservedForResponse >= totalBudget
        TokenBudgetManager mgr = new TokenBudgetManager(100, 200);
        assertEquals(1.0f, mgr.pressure());
    }

    @Test
    @DisplayName("remaining can go negative when over budget")
    void testRemainingNegative() {
        TokenBudgetManager mgr = new TokenBudgetManager(1_000, 100);

        mgr.recordUsage(1_500);
        assertTrue(mgr.remaining() < 0);
        assertTrue(mgr.pressure() > 1.0f);
    }

    @Test
    @DisplayName("reset clears used tokens")
    void testReset() {
        TokenBudgetManager mgr = new TokenBudgetManager(10_000, 1_000);

        mgr.recordUsage(5_000);
        assertEquals(5_000, mgr.used());

        mgr.reset();
        assertEquals(0, mgr.used());
        assertEquals(9_000, mgr.remaining());
    }

    @Test
    @DisplayName("getBudget returns correct snapshot")
    void testGetBudget() {
        TokenBudgetManager mgr = new TokenBudgetManager(10_000, 1_000);
        mgr.recordUsage(2_000);

        TokenBudget budget = mgr.getBudget();
        assertEquals(10_000, budget.total());
        assertEquals(2_000, budget.used());
        assertEquals(7_000, budget.remaining());
        assertEquals(1_000, budget.reservedForResponse());
        // pressure = 2000 / (10000 - 1000) = 2000/9000 ≈ 0.222
        assertEquals(2_000f / 9_000f, budget.pressure(), 0.001f);
    }
}
