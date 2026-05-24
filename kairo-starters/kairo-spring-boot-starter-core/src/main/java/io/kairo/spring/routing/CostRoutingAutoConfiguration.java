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

import io.kairo.core.routing.CostAwareRoutingPolicy;
import io.kairo.core.routing.ModelTier;
import io.kairo.core.routing.ModelTierRegistry;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for cost-aware routing.
 *
 * <p>Activated only when {@code kairo.routing.model-tiers} is configured. Creates a {@link
 * ModelTierRegistry} and {@link CostAwareRoutingPolicy} bean from Spring properties.
 *
 * <p>This does <b>not</b> replace the {@code DefaultRoutingPolicy} — it creates a separate bean
 * that users can wire into their agent configuration.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "kairo.routing", name = "model-tiers")
@EnableConfigurationProperties(CostRoutingProperties.class)
public class CostRoutingAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CostRoutingAutoConfiguration.class);

    @Bean
    ModelTierRegistry modelTierRegistry(CostRoutingProperties properties) {
        List<ModelTier> tiers =
                properties.getModelTiers().stream()
                        .map(
                                tp ->
                                        new ModelTier(
                                                tp.getTierName(),
                                                new HashSet<>(tp.getModels()),
                                                tp.getCostPerInputToken(),
                                                tp.getCostPerOutputToken(),
                                                Duration.ofMillis(tp.getExpectedLatencyMs())))
                        .toList();

        log.info("Configured ModelTierRegistry with {} tiers: {}", tiers.size(), tierNames(tiers));
        return new ModelTierRegistry(tiers);
    }

    @Bean
    CostAwareRoutingPolicy costAwareRoutingPolicy(
            ModelTierRegistry registry, CostRoutingProperties properties) {
        List<String> fallbackChain = properties.getFallbackChain();
        log.info("Configured CostAwareRoutingPolicy with fallback chain: {}", fallbackChain);
        return new CostAwareRoutingPolicy(registry, fallbackChain);
    }

    private static List<String> tierNames(List<ModelTier> tiers) {
        return tiers.stream().map(ModelTier::tierName).toList();
    }
}
