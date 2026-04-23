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
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * A pricing tier that groups one or more models with the same cost and latency characteristics.
 *
 * <p>Tiers are the building blocks of cost-aware routing: the {@link CostAwareRoutingPolicy}
 * selects the cheapest tier whose estimated cost fits within the configured budget.
 *
 * @param tierName human-readable tier identifier (e.g. "economy", "standard")
 * @param models set of model identifiers belonging to this tier
 * @param costPerInputToken cost per input token (e.g. 0.00000015)
 * @param costPerOutputToken cost per output token (e.g. 0.0000006)
 * @param expectedLatency expected response latency for models in this tier
 */
public record ModelTier(
        String tierName,
        Set<String> models,
        BigDecimal costPerInputToken,
        BigDecimal costPerOutputToken,
        Duration expectedLatency) {

    public ModelTier {
        Objects.requireNonNull(tierName, "tierName must not be null");
        Objects.requireNonNull(models, "models must not be null");
        models = Set.copyOf(models); // defensive copy
        Objects.requireNonNull(costPerInputToken, "costPerInputToken must not be null");
        Objects.requireNonNull(costPerOutputToken, "costPerOutputToken must not be null");
        Objects.requireNonNull(expectedLatency, "expectedLatency must not be null");
    }

    /**
     * Estimate the total cost for the given token counts.
     *
     * @param inputTokens estimated number of input tokens
     * @param outputTokens estimated number of output tokens
     * @return the estimated cost as {@code costPerInputToken * inputTokens + costPerOutputToken *
     *     outputTokens}
     */
    public BigDecimal estimateCost(long inputTokens, long outputTokens) {
        return costPerInputToken
                .multiply(BigDecimal.valueOf(inputTokens))
                .add(costPerOutputToken.multiply(BigDecimal.valueOf(outputTokens)));
    }
}
