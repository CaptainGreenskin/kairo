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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Registry of {@link ModelTier} definitions, ordered cheapest-first.
 *
 * <p>Populated from Spring configuration properties at startup. Provides lookup and filtering
 * operations used by {@link CostAwareRoutingPolicy} to select the best tier for a given request.
 */
public class ModelTierRegistry {

    private final List<ModelTier> tiers; // ordered cheapest-first

    /**
     * Create a registry with the given tiers.
     *
     * @param tiers the tiers, ordered from cheapest to most expensive; defensively copied
     */
    public ModelTierRegistry(List<ModelTier> tiers) {
        this.tiers = List.copyOf(tiers); // immutable
    }

    /**
     * Find the tier containing the given model name.
     *
     * <p>If a model appears in multiple tiers, the first matching tier (in registration order) is
     * returned. This is by design — register cheaper tiers first for cost-optimized resolution.
     *
     * @param modelName the model identifier to look up
     * @return the tier containing the model, or empty if not found
     */
    public Optional<ModelTier> findTierForModel(String modelName) {
        return tiers.stream().filter(tier -> tier.models().contains(modelName)).findFirst();
    }

    /**
     * Return all tiers whose estimated cost for the given token counts is within the budget.
     *
     * @param maxCostPerRequest the maximum acceptable cost
     * @param estimatedInputTokens estimated input token count
     * @param estimatedOutputTokens estimated output token count
     * @return tiers within budget, in cheapest-first order
     */
    public List<ModelTier> tiersWithinBudget(
            BigDecimal maxCostPerRequest, long estimatedInputTokens, long estimatedOutputTokens) {
        return tiers.stream()
                .filter(
                        tier -> {
                            BigDecimal cost =
                                    tier.estimateCost(estimatedInputTokens, estimatedOutputTokens);
                            return cost.compareTo(maxCostPerRequest) <= 0;
                        })
                .toList();
    }

    /**
     * Return all tiers in their configured order (cheapest-first).
     *
     * @return unmodifiable list of all tiers
     */
    public List<ModelTier> fallbackChain() {
        return tiers;
    }
}
