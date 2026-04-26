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
package io.kairo.spring.routing;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests that {@link CostRoutingProperties} JSR-303 validation annotations reject invalid
 * configuration values.
 */
class CostRoutingPropertiesValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        if (factory != null) {
            factory.close();
        }
    }

    @Test
    void validConfiguration_shouldSucceed() {
        CostRoutingProperties props = new CostRoutingProperties();
        CostRoutingProperties.TierProperties tier = validTier();
        props.setModelTiers(List.of(tier));
        props.setFallbackChain(List.of("economy"));

        Set<ConstraintViolation<CostRoutingProperties>> violations = validator.validate(props);
        assertThat(violations).isEmpty();
    }

    @Test
    void blankTierName_shouldFailValidation() {
        CostRoutingProperties.TierProperties tier = validTier();
        tier.setTierName("");

        CostRoutingProperties props = propsWithTier(tier);
        Set<ConstraintViolation<CostRoutingProperties>> violations = validator.validate(props);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .anyMatch(v -> v.getMessage().contains("Tier name must not be blank"));
    }

    @Test
    void emptyModels_shouldFailValidation() {
        CostRoutingProperties.TierProperties tier = validTier();
        tier.setModels(List.of());

        CostRoutingProperties props = propsWithTier(tier);
        Set<ConstraintViolation<CostRoutingProperties>> violations = validator.validate(props);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .anyMatch(v -> v.getMessage().contains("Models list must not be empty"));
    }

    @Test
    void negativeCostPerInputToken_shouldFailValidation() {
        CostRoutingProperties.TierProperties tier = validTier();
        tier.setCostPerInputToken(new BigDecimal("-0.001"));

        CostRoutingProperties props = propsWithTier(tier);
        Set<ConstraintViolation<CostRoutingProperties>> violations = validator.validate(props);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .anyMatch(
                        v -> v.getMessage().contains("Cost per input token must be non-negative"));
    }

    @Test
    void negativeCostPerOutputToken_shouldFailValidation() {
        CostRoutingProperties.TierProperties tier = validTier();
        tier.setCostPerOutputToken(new BigDecimal("-0.001"));

        CostRoutingProperties props = propsWithTier(tier);
        Set<ConstraintViolation<CostRoutingProperties>> violations = validator.validate(props);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .anyMatch(
                        v -> v.getMessage().contains("Cost per output token must be non-negative"));
    }

    @Test
    void nullCostPerInputToken_shouldFailValidation() {
        CostRoutingProperties.TierProperties tier = validTier();
        tier.setCostPerInputToken(null);

        CostRoutingProperties props = propsWithTier(tier);
        Set<ConstraintViolation<CostRoutingProperties>> violations = validator.validate(props);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void emptyModelTiers_shouldFailValidation() {
        CostRoutingProperties props = new CostRoutingProperties();
        props.setModelTiers(List.of());
        props.setFallbackChain(List.of("economy"));

        Set<ConstraintViolation<CostRoutingProperties>> violations = validator.validate(props);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .anyMatch(
                        v -> v.getMessage().contains("At least one model tier must be configured"));
    }

    @Test
    void emptyFallbackChain_shouldFailValidation() {
        CostRoutingProperties props = new CostRoutingProperties();
        CostRoutingProperties.TierProperties tier = validTier();
        props.setModelTiers(List.of(tier));
        props.setFallbackChain(List.of());

        Set<ConstraintViolation<CostRoutingProperties>> violations = validator.validate(props);

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .anyMatch(v -> v.getMessage().contains("Fallback chain must not be empty"));
    }

    private static CostRoutingProperties propsWithTier(CostRoutingProperties.TierProperties tier) {
        CostRoutingProperties props = new CostRoutingProperties();
        props.setModelTiers(List.of(tier));
        props.setFallbackChain(List.of("economy"));
        return props;
    }

    private static CostRoutingProperties.TierProperties validTier() {
        CostRoutingProperties.TierProperties tier = new CostRoutingProperties.TierProperties();
        tier.setTierName("economy");
        tier.setModels(List.of("gpt-4o-mini"));
        tier.setCostPerInputToken(new BigDecimal("0.00000015"));
        tier.setCostPerOutputToken(new BigDecimal("0.0000006"));
        return tier;
    }
}
