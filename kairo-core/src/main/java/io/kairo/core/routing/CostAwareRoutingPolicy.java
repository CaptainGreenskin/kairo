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

import io.kairo.api.message.Msg;
import io.kairo.api.routing.RoutingContext;
import io.kairo.api.routing.RoutingDecision;
import io.kairo.api.routing.RoutingPolicy;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Cost-aware routing policy that selects the cheapest model tier within the configured cost budget.
 *
 * <p>Selection logic:
 *
 * <ol>
 *   <li>Estimate input token count from context messages using the {@code chars / 4} heuristic
 *       (consistent with ADR-010 precedent).
 *   <li>Estimate output tokens as {@code inputTokens / 2} (rough heuristic).
 *   <li>If a cost budget is present in the context, select the cheapest tier within that budget.
 *   <li>If no budget constraint, use the first tier in the fallback chain.
 *   <li>Return a {@link RoutingDecision} with the first model from the selected tier.
 *   <li>If all tiers are exhausted or no matching tier exists, return an error.
 * </ol>
 *
 * <p>Fallback is linear: if the primary tier has no models, the next tier in the chain is tried.
 * This is selection-time fallback, not runtime retry (which is a different concern).
 */
public class CostAwareRoutingPolicy implements RoutingPolicy {

    private static final Logger log = LoggerFactory.getLogger(CostAwareRoutingPolicy.class);

    /** Token estimation divisor: characters / 4 ≈ tokens (ADR-010 precedent). */
    private static final int CHARS_PER_TOKEN = 4;

    private final ModelTierRegistry registry;
    private final List<String> fallbackChainTierNames;

    /**
     * Create a cost-aware routing policy.
     *
     * @param registry the model tier registry
     * @param fallbackChainTierNames ordered tier names defining the fallback evaluation order
     */
    public CostAwareRoutingPolicy(ModelTierRegistry registry, List<String> fallbackChainTierNames) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.fallbackChainTierNames =
                List.copyOf(
                        Objects.requireNonNull(
                                fallbackChainTierNames, "fallbackChainTierNames must not be null"));
    }

    @Override
    public Mono<RoutingDecision> selectProvider(RoutingContext context) {
        return Mono.fromCallable(() -> doSelect(context));
    }

    private RoutingDecision doSelect(RoutingContext context) {
        long estimatedInputTokens = estimateInputTokens(context.messages());
        long estimatedOutputTokens = Math.max(1, estimatedInputTokens / 2);

        Double costBudget = context.costBudget();

        // If a cost budget is set, filter tiers within budget
        if (costBudget != null && costBudget > 0) {
            BigDecimal budget = BigDecimal.valueOf(costBudget);
            List<ModelTier> affordable =
                    registry.tiersWithinBudget(budget, estimatedInputTokens, estimatedOutputTokens);

            // Try affordable tiers in fallback chain order
            for (String tierName : fallbackChainTierNames) {
                for (ModelTier tier : affordable) {
                    if (tier.tierName().equals(tierName) && !tier.models().isEmpty()) {
                        String selectedModel = tier.models().iterator().next();
                        double estimated =
                                tier.estimateCost(estimatedInputTokens, estimatedOutputTokens)
                                        .doubleValue();
                        log.debug(
                                "Cost-aware routing selected model '{}' from tier '{}' "
                                        + "(estimatedCost={}, budget={})",
                                selectedModel,
                                tierName,
                                estimated,
                                costBudget);
                        return new RoutingDecision(
                                selectedModel,
                                "cost-aware: tier=" + tierName + ", within budget",
                                estimated);
                    }
                }
            }

            // No affordable tier in fallback chain — error
            throw new IllegalStateException(
                    "No tier within cost budget "
                            + costBudget
                            + " for estimated "
                            + estimatedInputTokens
                            + " input / "
                            + estimatedOutputTokens
                            + " output tokens");
        }

        // No budget constraint — use first available tier from fallback chain
        List<ModelTier> allTiers = registry.fallbackChain();
        for (String tierName : fallbackChainTierNames) {
            for (ModelTier tier : allTiers) {
                if (tier.tierName().equals(tierName) && !tier.models().isEmpty()) {
                    String selectedModel = tier.models().iterator().next();
                    double estimated =
                            tier.estimateCost(estimatedInputTokens, estimatedOutputTokens)
                                    .doubleValue();
                    log.debug(
                            "Cost-aware routing selected model '{}' from tier '{}' (no budget constraint)",
                            selectedModel,
                            tierName);
                    return new RoutingDecision(
                            selectedModel,
                            "cost-aware: tier=" + tierName + ", no budget constraint",
                            estimated);
                }
            }
        }

        throw new IllegalStateException(
                "All tiers exhausted — no model available in fallback chain "
                        + fallbackChainTierNames);
    }

    /**
     * Estimate the number of input tokens from the conversation messages.
     *
     * <p>Uses the {@code chars / 4} heuristic from ADR-010 (same as ToolResultBudget). Token
     * estimation uses chars/4 heuristic. Typical error margin: ±30% for English text, higher for
     * CJK or mixed content. For production accuracy, consider injecting a real tokenizer.
     */
    long estimateInputTokens(List<Msg> messages) {
        long totalChars = 0;
        for (Msg msg : messages) {
            totalChars += msg.text().length();
        }
        return Math.max(1, totalChars / CHARS_PER_TOKEN);
    }
}
