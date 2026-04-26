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

import io.kairo.api.event.KairoEventBus;
import io.kairo.api.team.EvaluationStrategy;
import io.kairo.api.team.TeamCoordinator;
import io.kairo.expertteam.AgentEvaluationStrategy;
import io.kairo.expertteam.ExpertTeamCoordinator;
import io.kairo.expertteam.SimpleEvaluationStrategy;
import io.kairo.expertteam.internal.DefaultPlanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the Kairo expert-team orchestration module.
 *
 * <p>Opt-in via {@code kairo.expert-team.enabled=true}. When active, the configuration wires:
 *
 * <ul>
 *   <li>A {@link SimpleEvaluationStrategy} qualified as {@code simpleEvaluationStrategy} (unless
 *       the user already provides a primary {@link EvaluationStrategy}).
 *   <li>An optional {@link AgentEvaluationStrategy} — only if the user supplies a {@link
 *       AgentEvaluationStrategy.AgentInvoker} bean; otherwise the coordinator falls back to the
 *       simple strategy at request time.
 *   <li>A default {@link TeamCoordinator} backed by {@link ExpertTeamCoordinator}.
 * </ul>
 *
 * <p>The activation condition deliberately uses {@code havingValue = "true"} with {@code
 * matchIfMissing = false} so installing the starter is not enough — users must opt in explicitly.
 * This matches ADR-015's stance that expert-team orchestration is an advanced, policy-sensitive
 * feature.
 *
 * @since v0.10
 */
@AutoConfiguration
@ConditionalOnClass(ExpertTeamCoordinator.class)
@ConditionalOnProperty(
        prefix = "kairo.expert-team",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
@EnableConfigurationProperties(ExpertTeamProperties.class)
public class ExpertTeamAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ExpertTeamAutoConfiguration.class);

    /** Deterministic rubric evaluator exposed under a stable qualifier. */
    @Bean("simpleEvaluationStrategy")
    @ConditionalOnMissingBean(name = "simpleEvaluationStrategy")
    public SimpleEvaluationStrategy simpleEvaluationStrategy() {
        return new SimpleEvaluationStrategy();
    }

    /**
     * Agent-backed evaluator. Only instantiated when a user-provided {@link
     * AgentEvaluationStrategy.AgentInvoker} bean is present; otherwise this bean is absent and the
     * coordinator falls back to the simple strategy per {@link
     * io.kairo.api.team.EvaluatorPreference#AGENT} handling.
     */
    @Bean("agentEvaluationStrategy")
    @ConditionalOnMissingBean(name = "agentEvaluationStrategy")
    public AgentEvaluationStrategy agentEvaluationStrategy(
            ObjectProvider<AgentEvaluationStrategy.AgentInvoker> invokerProvider) {
        AgentEvaluationStrategy.AgentInvoker invoker = invokerProvider.getIfAvailable();
        if (invoker == null) {
            log.debug(
                    "No AgentInvoker bean present — agentEvaluationStrategy bean will be a no-op"
                            + " stub; users are expected to override this bean when using"
                            + " EvaluatorPreference.AGENT.");
            return new AgentEvaluationStrategy(
                    ctx ->
                            reactor.core.publisher.Mono.error(
                                    new IllegalStateException(
                                            "agentEvaluationStrategy requested but no AgentInvoker"
                                                    + " bean is wired")));
        }
        return new AgentEvaluationStrategy(invoker);
    }

    /** Deterministic planner used by the default coordinator. */
    @Bean
    @ConditionalOnMissingBean
    public DefaultPlanner defaultExpertTeamPlanner() {
        return new DefaultPlanner();
    }

    /** Default coordinator — wired only when no other {@link TeamCoordinator} bean is present. */
    @Bean
    @ConditionalOnMissingBean(TeamCoordinator.class)
    public TeamCoordinator expertTeamCoordinator(
            ObjectProvider<KairoEventBus> eventBusProvider,
            SimpleEvaluationStrategy simpleEvaluationStrategy,
            ObjectProvider<AgentEvaluationStrategy> agentEvaluationStrategyProvider,
            DefaultPlanner planner) {
        KairoEventBus eventBus = eventBusProvider.getIfAvailable();
        AgentEvaluationStrategy agentStrategy = agentEvaluationStrategyProvider.getIfAvailable();
        log.info(
                "Expert-team auto-configuration active (eventBus={}, agentStrategy={})",
                eventBus == null ? "none" : eventBus.getClass().getSimpleName(),
                agentStrategy == null ? "none" : agentStrategy.getClass().getSimpleName());
        return new ExpertTeamCoordinator(
                eventBus, simpleEvaluationStrategy, agentStrategy, planner);
    }
}
