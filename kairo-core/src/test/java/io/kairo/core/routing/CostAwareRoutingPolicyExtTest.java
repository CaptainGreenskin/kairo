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
import io.kairo.api.routing.RoutingDecision;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * Extended boundary tests for {@link CostAwareRoutingPolicy} covering budget degradation, tier
 * fallback chains, edge-case inputs, and concurrency.
 */
class CostAwareRoutingPolicyExtTest {

    // PREMIUM: expensive ($0.01/input, $0.03/output)
    private static final ModelTier PREMIUM =
            new ModelTier(
                    "premium",
                    Set.of("gpt-4-turbo", "claude-opus"),
                    new BigDecimal("0.01"),
                    new BigDecimal("0.03"),
                    Duration.ofSeconds(10));

    // STANDARD: mid-tier ($0.001/input, $0.002/output)
    private static final ModelTier STANDARD =
            new ModelTier(
                    "standard",
                    Set.of("gpt-4o", "claude-3.5-sonnet"),
                    new BigDecimal("0.001"),
                    new BigDecimal("0.002"),
                    Duration.ofSeconds(5));

    // ECONOMY: cheapest ($0.0001/input, $0.0002/output)
    private static final ModelTier ECONOMY =
            new ModelTier(
                    "economy",
                    Set.of("gpt-4o-mini", "claude-haiku"),
                    new BigDecimal("0.0001"),
                    new BigDecimal("0.0002"),
                    Duration.ofSeconds(3));

    private final ModelTierRegistry registry =
            new ModelTierRegistry(List.of(ECONOMY, STANDARD, PREMIUM));

    // Full chain: PREMIUM → STANDARD → ECONOMY (preferred order, most capable first)
    private final CostAwareRoutingPolicy fullChainPolicy =
            new CostAwareRoutingPolicy(registry, List.of("premium", "standard", "economy"));

    // Economy-only chain
    private final CostAwareRoutingPolicy economyOnlyPolicy =
            new CostAwareRoutingPolicy(registry, List.of("economy"));

    private RoutingContext ctx(double budget) {
        Msg msg = Msg.of(MsgRole.USER, "a".repeat(40)); // 10 tokens — cheap
        ModelConfig config = ModelConfig.builder().model("any").costBudget(budget).build();
        return new RoutingContext("agent", List.of(msg), config, budget);
    }

    private RoutingContext ctxNoBudget() {
        Msg msg = Msg.of(MsgRole.USER, "hello");
        ModelConfig config = ModelConfig.builder().model("any").build();
        return new RoutingContext("agent", List.of(msg), config, null);
    }

    // ── Budget sufficient: selects first (cheapest) in chain ──────────────────

    @Test
    void sufficientBudget_selectsFirstTierInFallbackChain() {
        RoutingContext ctx = ctx(99.0); // enormous budget
        StepVerifier.create(economyOnlyPolicy.selectProvider(ctx))
                .assertNext(
                        d -> {
                            assertTrue(ECONOMY.models().contains(d.providerName()));
                            assertTrue(d.rationale().contains("economy"));
                        })
                .verifyComplete();
    }

    // ── Budget exceeded → degrades to cheaper tier ─────────────────────────

    @Test
    void budgetExceeded_degradesToCheaperTier() {
        // 10 input + 5 output tokens
        // ECONOMY cost = 0.0001*10 + 0.0002*5 = 0.002
        // STANDARD cost = 0.001*10 + 0.002*5 = 0.02
        // Budget 0.005: STANDARD (0.02) > budget, ECONOMY (0.002) ≤ budget
        double tightBudget = 0.005;
        RoutingContext ctx = ctx(tightBudget);
        StepVerifier.create(fullChainPolicy.selectProvider(ctx))
                .assertNext(
                        d -> {
                            assertTrue(
                                    ECONOMY.models().contains(d.providerName()),
                                    "Should degrade to ECONOMY, got: " + d.providerName());
                        })
                .verifyComplete();
    }

    // ── All tiers over budget → error ───────────────────────────────────────

    @Test
    void allTiersOverBudget_returnsError() {
        double impossiblyLowBudget = 0.0000000001;
        Msg bigMsg = Msg.of(MsgRole.USER, "x".repeat(4000)); // ~1000 tokens
        ModelConfig config =
                ModelConfig.builder().model("any").costBudget(impossiblyLowBudget).build();
        RoutingContext ctx =
                new RoutingContext("agent", List.of(bigMsg), config, impossiblyLowBudget);

        StepVerifier.create(fullChainPolicy.selectProvider(ctx))
                .expectErrorSatisfies(
                        e -> {
                            assertInstanceOf(IllegalStateException.class, e);
                            assertTrue(e.getMessage().contains("No tier within cost budget"));
                        })
                .verify();
    }

    // ── Zero tokens does not trigger degradation ─────────────────────────────

    @Test
    void zeroTokens_noArtificialDegradation() {
        // Even with tiny tokens, should pick the first tier in chain when budget is generous
        RoutingContext ctx = ctx(1.0);
        StepVerifier.create(fullChainPolicy.selectProvider(ctx))
                .assertNext(
                        d -> {
                            // PREMIUM is first in full chain
                            assertTrue(PREMIUM.models().contains(d.providerName()));
                        })
                .verifyComplete();
    }

    // ── Empty registry → error ────────────────────────────────────────────────

    @Test
    void emptyRegistry_returnsError() {
        ModelTierRegistry emptyRegistry = new ModelTierRegistry(List.of());
        CostAwareRoutingPolicy policy =
                new CostAwareRoutingPolicy(emptyRegistry, List.of("premium"));
        RoutingContext ctx = ctxNoBudget();
        StepVerifier.create(policy.selectProvider(ctx))
                .expectErrorSatisfies(
                        e -> {
                            assertInstanceOf(IllegalStateException.class, e);
                            assertTrue(e.getMessage().contains("exhausted"));
                        })
                .verify();
    }

    // ── Single-tier system routes correctly ──────────────────────────────────

    @Test
    void singleTier_routesCorrectly() {
        ModelTierRegistry single = new ModelTierRegistry(List.of(STANDARD));
        CostAwareRoutingPolicy policy = new CostAwareRoutingPolicy(single, List.of("standard"));
        RoutingContext ctx = ctxNoBudget();
        StepVerifier.create(policy.selectProvider(ctx))
                .assertNext(d -> assertTrue(STANDARD.models().contains(d.providerName())))
                .verifyComplete();
    }

    // ── PREMIUM → STANDARD → ECONOMY degradation chain ──────────────────────

    @Test
    void premiumStandardEconomy_fullDegradationChain() {
        // 10 tokens: PREMIUM cost=0.25, STANDARD cost=0.02, ECONOMY cost=0.002
        // budget=0.005: only ECONOMY fits
        double budget = 0.005;
        RoutingContext ctx = ctx(budget);
        StepVerifier.create(fullChainPolicy.selectProvider(ctx))
                .assertNext(d -> assertTrue(ECONOMY.models().contains(d.providerName())))
                .verifyComplete();
    }

    // ── Custom budget threshold: mid-tier selected ───────────────────────────

    @Test
    void customBudgetThreshold_selectsMidTier() {
        // Give budget that STANDARD fits but PREMIUM doesn't
        // 10 input + 5 output tokens → PREMIUM cost = 0.01*10 + 0.03*5 = 0.25
        // STANDARD cost = 0.001*10 + 0.002*5 = 0.02
        // Give budget = 0.05: PREMIUM too expensive, STANDARD OK, ECONOMY OK
        // fullChainPolicy picks PREMIUM first in chain order — but PREMIUM exceeds budget
        // Then tries STANDARD — STANDARD cost (0.02) < 0.05 → selects STANDARD
        double budget = 0.05;
        RoutingContext ctx = ctx(budget);
        StepVerifier.create(fullChainPolicy.selectProvider(ctx))
                .assertNext(
                        d -> {
                            assertTrue(
                                    STANDARD.models().contains(d.providerName()),
                                    "Expected STANDARD tier. Got: "
                                            + d.providerName()
                                            + " — this tier: "
                                            + registry.findTierForModel(d.providerName())
                                                    .map(ModelTier::tierName)
                                                    .orElse("unknown"));
                        })
                .verifyComplete();
    }

    // ── Same token count, different tiers → correct tier wins ────────────────

    @Test
    void sameTokenCount_cheaperTierSelected_withBudget() {
        // With 10 tokens and budget 0.000005, ECONOMY ($0.0001/tok) = 0.001 — wait,
        // 10 input * 0.0001 + 5 output * 0.0002 = 0.001 + 0.001 = 0.002 > 0.000005
        // So we need a very small message for ECONOMY to fit
        Msg tiny = Msg.of(MsgRole.USER, "hi"); // 2 chars → 1 token (max(1, 2/4)=1)
        ModelConfig config = ModelConfig.builder().model("any").costBudget(0.001).build();
        RoutingContext ctx = new RoutingContext("agent", List.of(tiny), config, 0.001);
        // 1 input + 1 output: ECONOMY = 0.0001 + 0.0002 = 0.0003 < 0.001 ✓
        // fullChainPolicy tries PREMIUM first → PREMIUM cost = 0.01+0.03 = 0.04 > 0.001
        // Then STANDARD → 0.001+0.002 = 0.003 > 0.001
        // Then ECONOMY → 0.0003 < 0.001 ✓
        StepVerifier.create(fullChainPolicy.selectProvider(ctx))
                .assertNext(d -> assertTrue(ECONOMY.models().contains(d.providerName())))
                .verifyComplete();
    }

    // ── No budget: picks first in fallback chain ─────────────────────────────

    @Test
    void noBudget_picksFirstInFallbackChain() {
        RoutingContext ctx = ctxNoBudget();
        StepVerifier.create(fullChainPolicy.selectProvider(ctx))
                .assertNext(d -> assertTrue(PREMIUM.models().contains(d.providerName())))
                .verifyComplete();
    }

    // ── Concurrent routing requests are race-condition-free ──────────────────

    @Test
    void concurrentRouting_noRaceCondition() throws InterruptedException {
        int concurrency = 20;
        CountDownLatch latch = new CountDownLatch(concurrency);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger errorCount = new AtomicInteger();

        var executor = Executors.newFixedThreadPool(concurrency);
        for (int i = 0; i < concurrency; i++) {
            executor.submit(
                    () -> {
                        try {
                            RoutingDecision d =
                                    fullChainPolicy.selectProvider(ctxNoBudget()).block();
                            if (d != null && PREMIUM.models().contains(d.providerName())) {
                                successCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                        } finally {
                            latch.countDown();
                        }
                    });
        }
        latch.await();
        executor.shutdown();

        assertEquals(0, errorCount.get(), "No errors expected in concurrent routing");
        assertEquals(concurrency, successCount.get(), "All requests should select PREMIUM tier");
    }

    // ── Decision includes non-null rationale and non-negative cost ───────────

    @Test
    void decision_hasRationaleAndNonNegativeCost() {
        RoutingContext ctx = ctxNoBudget();
        StepVerifier.create(fullChainPolicy.selectProvider(ctx))
                .assertNext(
                        d -> {
                            assertNotNull(d.rationale());
                            assertFalse(d.rationale().isBlank());
                            assertTrue(d.estimatedCost() >= 0.0);
                        })
                .verifyComplete();
    }

    // ── Budget exactly matching estimated cost is accepted ───────────────────

    @Test
    void budgetExactlyMatchingCost_accepted() {
        // 1 input + 1 output token → ECONOMY cost = 0.0001 + 0.0002 = 0.0003
        Msg tiny = Msg.of(MsgRole.USER, "hi");
        double exactBudget = 0.0003;
        ModelConfig config = ModelConfig.builder().model("any").costBudget(exactBudget).build();
        RoutingContext ctx = new RoutingContext("agent", List.of(tiny), config, exactBudget);

        StepVerifier.create(economyOnlyPolicy.selectProvider(ctx))
                .assertNext(d -> assertTrue(ECONOMY.models().contains(d.providerName())))
                .verifyComplete();
    }
}
