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

import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.routing.RoutingContext;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class CostAwareRoutingPolicyTest {

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
    private final CostAwareRoutingPolicy policy =
            new CostAwareRoutingPolicy(registry, List.of("economy", "standard"));

    @Test
    void selectProvider_returnsCheapestTierModel() {
        ModelConfig config = ModelConfig.builder().model("any-model").build();
        RoutingContext ctx =
                new RoutingContext("agent", List.of(Msg.of(MsgRole.USER, "Hello")), config, null);

        StepVerifier.create(policy.selectProvider(ctx))
                .assertNext(
                        decision -> {
                            // Should select from economy tier (cheapest, first in fallback)
                            assertTrue(
                                    ECONOMY.models().contains(decision.providerName()),
                                    "Expected model from economy tier but got: "
                                            + decision.providerName());
                            assertNotNull(decision.estimatedCost());
                            assertTrue(decision.rationale().contains("economy"));
                        })
                .verifyComplete();
    }

    @Test
    void selectProvider_respectsCostBudget_skipsExpensiveTiers() {
        // With a very tight budget, only economy tier should be selected
        ModelConfig config = ModelConfig.builder().model("any-model").costBudget(0.001).build();
        Msg longMsg = Msg.of(MsgRole.USER, "a".repeat(4000)); // ~1000 tokens
        RoutingContext ctx = new RoutingContext("agent", List.of(longMsg), config, 0.001);

        StepVerifier.create(policy.selectProvider(ctx))
                .assertNext(
                        decision -> {
                            assertTrue(
                                    ECONOMY.models().contains(decision.providerName()),
                                    "Budget should restrict to economy tier");
                            assertTrue(decision.rationale().contains("within budget"));
                        })
                .verifyComplete();
    }

    @Test
    void fallback_whenFirstTierNotInChain_fallsToNextTier() {
        // Create a policy where only "standard" is in the fallback chain
        CostAwareRoutingPolicy standardOnly =
                new CostAwareRoutingPolicy(registry, List.of("standard"));

        ModelConfig config = ModelConfig.builder().model("any-model").build();
        RoutingContext ctx =
                new RoutingContext("agent", List.of(Msg.of(MsgRole.USER, "Hi")), config, null);

        StepVerifier.create(standardOnly.selectProvider(ctx))
                .assertNext(
                        decision -> {
                            assertTrue(
                                    STANDARD.models().contains(decision.providerName()),
                                    "Should select from standard tier");
                            assertTrue(decision.rationale().contains("standard"));
                        })
                .verifyComplete();
    }

    @Test
    void allTiersExhausted_returnsError() {
        // Create a policy with a non-existent tier in fallback chain
        CostAwareRoutingPolicy noTiers =
                new CostAwareRoutingPolicy(registry, List.of("nonexistent"));

        ModelConfig config = ModelConfig.builder().model("any-model").build();
        RoutingContext ctx =
                new RoutingContext("agent", List.of(Msg.of(MsgRole.USER, "Hi")), config, null);

        StepVerifier.create(noTiers.selectProvider(ctx))
                .expectErrorMatches(
                        e ->
                                e instanceof IllegalStateException
                                        && e.getMessage().contains("All tiers exhausted"))
                .verify();
    }

    @Test
    void budgetTooLow_returnsError() {
        // Budget so low no tier can satisfy it
        ModelConfig config =
                ModelConfig.builder().model("any-model").costBudget(0.0000000001).build();
        Msg longMsg = Msg.of(MsgRole.USER, "a".repeat(40000)); // ~10000 tokens
        RoutingContext ctx = new RoutingContext("agent", List.of(longMsg), config, 0.0000000001);

        StepVerifier.create(policy.selectProvider(ctx))
                .expectErrorMatches(
                        e ->
                                e instanceof IllegalStateException
                                        && e.getMessage().contains("No tier within cost budget"))
                .verify();
    }

    @Test
    void tokenEstimation_usesCharDiv4Heuristic() {
        // "a".repeat(400) = 400 chars → 100 tokens
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "a".repeat(400)));
        long tokens = policy.estimateInputTokens(messages);
        assertEquals(100, tokens);
    }

    @Test
    void tokenEstimation_emptyMessages_returnsMinimumOne() {
        long tokens = policy.estimateInputTokens(List.of());
        assertEquals(1, tokens, "Empty messages should return minimum of 1 token");
    }

    @Test
    void estimateCost_calculationIsPrecise() {
        // economy: input=0.00000015, output=0.0000006
        // 1000 input + 500 output = 0.00000015*1000 + 0.0000006*500 = 0.00015 + 0.0003 = 0.00045
        BigDecimal cost = ECONOMY.estimateCost(1000, 500);
        assertEquals(new BigDecimal("0.00045000"), cost);
    }
}
