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
package io.kairo.spring;

import io.kairo.api.agent.AgentBuilderCustomizer;
import io.kairo.core.agent.AgentBuilder;
import io.kairo.core.agent.continuation.AgentContinuationStrategy;
import io.kairo.core.agent.continuation.CompositeContinuationStrategy;
import io.kairo.core.agent.continuation.FinishReasonRecoveryStrategy;
import io.kairo.core.agent.continuation.NoopContinuationStrategy;
import io.kairo.core.agent.continuation.PendingTodoNudgeStrategy;
import io.kairo.core.agent.continuation.RecentToolActivityStrategy;
import io.kairo.spring.config.ContinuationProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for the agent continuation strategy framework.
 *
 * <p>When {@code kairo.agent.continuation.enabled=true}, registers a {@link
 * CompositeContinuationStrategy} composed of the recommended sub-strategies with configurable
 * parameters. When disabled (the default), a {@link NoopContinuationStrategy} is used, preserving
 * pre-0.5.0 termination behavior.
 *
 * <p>The registered strategy is wired into the agent builder via an {@link AgentBuilderCustomizer}
 * bean, ensuring seamless integration with the existing agent construction pipeline.
 *
 * @since 0.5.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(AgentContinuationStrategy.class)
@EnableConfigurationProperties(ContinuationProperties.class)
class ContinuationAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ContinuationAutoConfiguration.class);

    // ---- Strategy Beans ----

    /**
     * Registers the composite continuation strategy when explicitly enabled.
     *
     * <p>Composes sub-strategies in priority order using property-driven parameters.
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "kairo.agent.continuation",
            name = "enabled",
            havingValue = "true")
    @ConditionalOnMissingBean(AgentContinuationStrategy.class)
    AgentContinuationStrategy compositeContinuationStrategy(ContinuationProperties properties) {
        CompositeContinuationStrategy strategy =
                new CompositeContinuationStrategy(
                        List.of(
                                new FinishReasonRecoveryStrategy(properties.getMaxLengthRetries()),
                                new PendingTodoNudgeStrategy(),
                                new RecentToolActivityStrategy(
                                        properties.getToolActivityLookback())));
        log.info(
                "Configured composite continuation strategy (signal-based; maxLengthRetries={}, "
                        + "toolActivityLookback={})",
                properties.getMaxLengthRetries(),
                properties.getToolActivityLookback());
        return strategy;
    }

    /**
     * Fallback: registers the noop strategy when continuation is disabled or no strategy bean
     * exists.
     */
    @Bean
    @ConditionalOnMissingBean(AgentContinuationStrategy.class)
    AgentContinuationStrategy noopContinuationStrategy() {
        log.debug("Continuation framework disabled — using NoopContinuationStrategy");
        return NoopContinuationStrategy.INSTANCE;
    }

    // ---- Builder Customizer ----

    /**
     * Wires the resolved continuation strategy into the agent builder via the customizer pipeline.
     */
    @Bean
    @ConditionalOnBean(AgentContinuationStrategy.class)
    AgentBuilderCustomizer continuationCustomizer(AgentContinuationStrategy strategy) {
        return builder -> {
            if (builder instanceof AgentBuilder agentBuilder) {
                agentBuilder.continuationStrategy(strategy);
            }
        };
    }
}
