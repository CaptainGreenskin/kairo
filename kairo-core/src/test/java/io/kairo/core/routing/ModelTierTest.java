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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelTierTest {

    private static ModelTier economy() {
        return new ModelTier(
                "economy",
                Set.of("gpt-4o-mini", "claude-haiku"),
                new BigDecimal("0.00000015"),
                new BigDecimal("0.0000006"),
                Duration.ofSeconds(2));
    }

    @Test
    void constructorDoesNotThrow() {
        assertThat(economy()).isNotNull();
    }

    @Test
    void tierNamePreserved() {
        assertThat(economy().tierName()).isEqualTo("economy");
    }

    @Test
    void modelsPreserved() {
        assertThat(economy().models()).containsExactlyInAnyOrder("gpt-4o-mini", "claude-haiku");
    }

    @Test
    void costPerInputTokenPreserved() {
        assertThat(economy().costPerInputToken()).isEqualByComparingTo("0.00000015");
    }

    @Test
    void costPerOutputTokenPreserved() {
        assertThat(economy().costPerOutputToken()).isEqualByComparingTo("0.0000006");
    }

    @Test
    void expectedLatencyPreserved() {
        assertThat(economy().expectedLatency()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void modelsIsUnmodifiable() {
        assertThatThrownBy(() -> economy().models().add("injected"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void estimateCostIsCorrect() {
        // 1000 input * 0.00000015 + 500 output * 0.0000006 = 0.00015 + 0.0003 = 0.00045
        BigDecimal cost = economy().estimateCost(1_000, 500);
        assertThat(cost).isEqualByComparingTo("0.00045");
    }

    @Test
    void estimateCostZeroTokens() {
        assertThat(economy().estimateCost(0, 0)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void equalityViaRecord() {
        ModelTier a = economy();
        ModelTier b = economy();
        assertThat(a).isEqualTo(b);
    }

    @Test
    void nullTierNameThrows() {
        assertThatThrownBy(
                        () ->
                                new ModelTier(
                                        null,
                                        Set.of(),
                                        BigDecimal.ONE,
                                        BigDecimal.ONE,
                                        Duration.ZERO))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullModelsThrows() {
        assertThatThrownBy(
                        () ->
                                new ModelTier(
                                        "tier",
                                        null,
                                        BigDecimal.ONE,
                                        BigDecimal.ONE,
                                        Duration.ZERO))
                .isInstanceOf(NullPointerException.class);
    }
}
