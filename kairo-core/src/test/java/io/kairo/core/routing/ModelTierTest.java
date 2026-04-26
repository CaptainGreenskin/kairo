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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelTierTest {

    private static final ModelTier ECONOMY =
            new ModelTier(
                    "economy",
                    Set.of("gpt-3.5-turbo"),
                    new BigDecimal("0.000001"),
                    new BigDecimal("0.000002"),
                    Duration.ofSeconds(2));

    @Test
    void storesTierName() {
        assertThat(ECONOMY.tierName()).isEqualTo("economy");
    }

    @Test
    void storesModels() {
        assertThat(ECONOMY.models()).contains("gpt-3.5-turbo");
    }

    @Test
    void storesExpectedLatency() {
        assertThat(ECONOMY.expectedLatency()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void estimateCost_calculatesCorrectly() {
        // 1_000_000 * 0.000001 + 500_000 * 0.000002 = 1.0 + 1.0 = 2.0
        var cost = ECONOMY.estimateCost(1_000_000L, 500_000L);
        assertThat(cost).isEqualByComparingTo(new BigDecimal("2.0"));
    }

    @Test
    void estimateCost_zeroTokens_returnsZero() {
        assertThat(ECONOMY.estimateCost(0, 0)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void nullTierName_throwsNpe() {
        assertThatNullPointerException()
                .isThrownBy(
                        () ->
                                new ModelTier(
                                        null,
                                        Set.of(),
                                        BigDecimal.ONE,
                                        BigDecimal.ONE,
                                        Duration.ZERO));
    }

    @Test
    void modelsAreImmutableCopy() {
        var mutableSet = new HashSet<>(Set.of("model-a"));
        var tier = new ModelTier("t", mutableSet, BigDecimal.ONE, BigDecimal.ONE, Duration.ZERO);
        mutableSet.add("model-b");
        assertThat(tier.models()).doesNotContain("model-b");
    }
}
