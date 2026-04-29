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

class ThinkingBudgetResolverTest {

    @Test
    void complexityOneReturnsLowBudget() {
        assertEquals(4_000, ThinkingBudgetResolver.resolve(1));
    }

    @Test
    void complexityThreeReturnsLowBudget() {
        assertEquals(4_000, ThinkingBudgetResolver.resolve(3));
    }

    @Test
    void complexityFourReturnsMedBudget() {
        assertEquals(8_000, ThinkingBudgetResolver.resolve(4));
    }

    @Test
    void complexitySixReturnsMedBudget() {
        assertEquals(8_000, ThinkingBudgetResolver.resolve(6));
    }

    @Test
    void complexitySevenReturnsHighBudget() {
        assertEquals(16_000, ThinkingBudgetResolver.resolve(7));
    }

    @Test
    void complexityTenReturnsHighBudget() {
        assertEquals(16_000, ThinkingBudgetResolver.resolve(10));
    }

    @Test
    void complexityFiveReturnsMedBudget() {
        assertEquals(8_000, ThinkingBudgetResolver.resolve(5));
    }

    @Test
    void complexityTwoReturnsLowBudget() {
        assertEquals(4_000, ThinkingBudgetResolver.resolve(2));
    }

    @Test
    void complexityEightReturnsHighBudget() {
        assertEquals(16_000, ThinkingBudgetResolver.resolve(8));
    }

    @Test
    void complexityNineReturnsHighBudget() {
        assertEquals(16_000, ThinkingBudgetResolver.resolve(9));
    }
}
