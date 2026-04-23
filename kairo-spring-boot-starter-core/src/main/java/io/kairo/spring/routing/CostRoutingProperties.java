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

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Spring Boot configuration properties for cost-aware routing.
 *
 * <p>Example configuration:
 *
 * <pre>{@code
 * kairo:
 *   routing:
 *     model-tiers:
 *       - tier-name: economy
 *         models: [gpt-4o-mini, claude-3-haiku]
 *         cost-per-input-token: 0.00000015
 *         cost-per-output-token: 0.0000006
 *       - tier-name: standard
 *         models: [gpt-4o, claude-3.5-sonnet]
 *         cost-per-input-token: 0.0000025
 *         cost-per-output-token: 0.00001
 *     fallback-chain: [economy, standard]
 * }</pre>
 */
@ConfigurationProperties(prefix = "kairo.routing")
@Validated
public class CostRoutingProperties {

    @NotEmpty(message = "At least one model tier must be configured")
    @Valid
    private List<TierProperties> modelTiers = new ArrayList<>();

    @NotEmpty(message = "Fallback chain must not be empty")
    private List<String> fallbackChain = new ArrayList<>();

    public List<TierProperties> getModelTiers() {
        return modelTiers;
    }

    public void setModelTiers(List<TierProperties> modelTiers) {
        this.modelTiers = modelTiers;
    }

    public List<String> getFallbackChain() {
        return fallbackChain;
    }

    public void setFallbackChain(List<String> fallbackChain) {
        this.fallbackChain = fallbackChain;
    }

    /** Properties for a single model tier. */
    public static class TierProperties {

        @NotBlank(message = "Tier name must not be blank")
        private String tierName;

        @NotEmpty(message = "Models list must not be empty")
        private List<String> models = new ArrayList<>();

        @NotNull
        @DecimalMin(value = "0", message = "Cost per input token must be non-negative")
        private BigDecimal costPerInputToken;

        @NotNull
        @DecimalMin(value = "0", message = "Cost per output token must be non-negative")
        private BigDecimal costPerOutputToken;

        @Positive(message = "Expected latency must be positive")
        private long expectedLatencyMs = 5000;

        public String getTierName() {
            return tierName;
        }

        public void setTierName(String tierName) {
            this.tierName = tierName;
        }

        public List<String> getModels() {
            return models;
        }

        public void setModels(List<String> models) {
            this.models = models;
        }

        public BigDecimal getCostPerInputToken() {
            return costPerInputToken;
        }

        public void setCostPerInputToken(BigDecimal costPerInputToken) {
            this.costPerInputToken = costPerInputToken;
        }

        public BigDecimal getCostPerOutputToken() {
            return costPerOutputToken;
        }

        public void setCostPerOutputToken(BigDecimal costPerOutputToken) {
            this.costPerOutputToken = costPerOutputToken;
        }

        public long getExpectedLatencyMs() {
            return expectedLatencyMs;
        }

        public void setExpectedLatencyMs(long expectedLatencyMs) {
            this.expectedLatencyMs = expectedLatencyMs;
        }
    }
}
