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
package io.kairo.core.routing;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelTierRegistryTest {

    private static final ModelTier ECONOMY =
            new ModelTier(
                    "economy",
                    Set.of("gpt-4o-mini", "claude-3-haiku"),
                    new BigDecimal("0.00000015"),
                    new BigDecimal("0.0000006"),
                    Duration.ofSeconds(3));

    private static final ModelTier STANDARD =
            new ModelTier(
                    "standard",
                    Set.of("gpt-4o", "claude-3.5-sonnet"),
                    new BigDecimal("0.0000025"),
                    new BigDecimal("0.00001"),
                    Duration.ofSeconds(5));

    private final ModelTierRegistry registry = new ModelTierRegistry(List.of(ECONOMY, STANDARD));

    @Test
    void findTierForModel_returnsCorrectTier() {
        var tier = registry.findTierForModel("gpt-4o-mini");
        assertTrue(tier.isPresent());
        assertEquals("economy", tier.get().tierName());
    }

    @Test
    void findTierForModel_unknownModelReturnsEmpty() {
        var tier = registry.findTierForModel("unknown-model");
        assertTrue(tier.isEmpty());
    }

    @Test
    void tiersWithinBudget_filtersCorrectly() {
        // Budget that only economy can satisfy: 1000 input, 500 output tokens
        // Economy cost: 0.00000015 * 1000 + 0.0000006 * 500 = 0.00015 + 0.0003 = 0.00045
        // Standard cost: 0.0000025 * 1000 + 0.00001 * 500 = 0.0025 + 0.005 = 0.0075
        BigDecimal tightBudget = new BigDecimal("0.001"); // only economy fits
        List<ModelTier> affordable = registry.tiersWithinBudget(tightBudget, 1000, 500);

        assertEquals(1, affordable.size());
        assertEquals("economy", affordable.get(0).tierName());
    }

    @Test
    void tiersWithinBudget_returnsBothWhenBudgetIsGenerous() {
        BigDecimal generousBudget = new BigDecimal("1.0");
        List<ModelTier> affordable = registry.tiersWithinBudget(generousBudget, 1000, 500);

        assertEquals(2, affordable.size());
        assertEquals("economy", affordable.get(0).tierName());
        assertEquals("standard", affordable.get(1).tierName());
    }

    @Test
    void fallbackChain_returnsAllTiersInOrder() {
        List<ModelTier> chain = registry.fallbackChain();
        assertEquals(2, chain.size());
        assertEquals("economy", chain.get(0).tierName());
        assertEquals("standard", chain.get(1).tierName());
    }

    @Test
    void defensiveCopy_modifyingInputListDoesNotAffectRegistry() {
        List<ModelTier> mutableList = new ArrayList<>(List.of(ECONOMY));
        ModelTierRegistry reg = new ModelTierRegistry(mutableList);

        mutableList.add(STANDARD);

        assertEquals(
                1, reg.fallbackChain().size(), "Registry should not be affected by input mutation");
    }
}
