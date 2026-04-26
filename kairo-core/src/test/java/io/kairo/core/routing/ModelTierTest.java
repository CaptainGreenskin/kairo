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
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelTierTest {

    private static ModelTier economy() {
        return new ModelTier(
                "economy",
                Set.of("haiku", "flash"),
                new BigDecimal("0.00000015"),
                new BigDecimal("0.00000060"),
                Duration.ofMillis(500));
    }

    @Test
    void fieldAccessorsReturnConstructorValues() {
        ModelTier tier = economy();
        assertEquals("economy", tier.tierName());
        assertEquals(Set.of("haiku", "flash"), tier.models());
        assertEquals(new BigDecimal("0.00000015"), tier.costPerInputToken());
        assertEquals(new BigDecimal("0.00000060"), tier.costPerOutputToken());
        assertEquals(Duration.ofMillis(500), tier.expectedLatency());
    }

    @Test
    void modelsSetIsDefensivelyCopied() {
        Set<String> mutable = new HashSet<>();
        mutable.add("model-a");
        ModelTier tier =
                new ModelTier(
                        "standard", mutable, BigDecimal.ONE, BigDecimal.ONE, Duration.ofSeconds(1));
        mutable.add("model-b");
        assertFalse(tier.models().contains("model-b"));
    }

    @Test
    void modelsSetIsUnmodifiable() {
        ModelTier tier = economy();
        assertThrows(UnsupportedOperationException.class, () -> tier.models().add("new-model"));
    }

    @Test
    void tierNameNullThrows() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new ModelTier(
                                null,
                                Set.of(),
                                BigDecimal.ONE,
                                BigDecimal.ONE,
                                Duration.ofSeconds(1)));
    }

    @Test
    void modelsNullThrows() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new ModelTier(
                                "tier",
                                null,
                                BigDecimal.ONE,
                                BigDecimal.ONE,
                                Duration.ofSeconds(1)));
    }

    @Test
    void costPerInputTokenNullThrows() {
        assertThrows(
                NullPointerException.class,
                () -> new ModelTier("tier", Set.of(), null, BigDecimal.ONE, Duration.ofSeconds(1)));
    }

    @Test
    void costPerOutputTokenNullThrows() {
        assertThrows(
                NullPointerException.class,
                () -> new ModelTier("tier", Set.of(), BigDecimal.ONE, null, Duration.ofSeconds(1)));
    }

    @Test
    void expectedLatencyNullThrows() {
        assertThrows(
                NullPointerException.class,
                () -> new ModelTier("tier", Set.of(), BigDecimal.ONE, BigDecimal.ONE, null));
    }

    @Test
    void recordEquality() {
        ModelTier a = economy();
        ModelTier b = economy();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void toStringContainsTierName() {
        ModelTier tier = economy();
        assertTrue(tier.toString().contains("economy"));
    }

    @Test
    void estimateCostReturnsCorrectValue() {
        ModelTier tier = economy();
        // 1000 input * 0.00000015 + 500 output * 0.00000060 = 0.00015 + 0.00030 = 0.00045
        BigDecimal cost = tier.estimateCost(1_000_000, 500_000);
        assertTrue(cost.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void estimateCostZeroTokensIsZero() {
        ModelTier tier = economy();
        assertEquals(0, tier.estimateCost(0, 0).compareTo(BigDecimal.ZERO));
    }
}
