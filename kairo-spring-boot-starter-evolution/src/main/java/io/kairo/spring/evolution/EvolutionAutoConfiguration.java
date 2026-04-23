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
package io.kairo.spring.evolution;

import io.kairo.api.agent.AgentBuilderCustomizer;
import io.kairo.api.evolution.EvolutionPolicy;
import io.kairo.api.evolution.EvolutionTrigger;
import io.kairo.api.evolution.EvolvedSkillStore;
import io.kairo.api.model.ModelProvider;
import io.kairo.core.agent.AgentBuilder;
import io.kairo.evolution.DefaultEvolutionPolicy;
import io.kairo.evolution.DefaultEvolutionTrigger;
import io.kairo.evolution.EvolutionHook;
import io.kairo.evolution.EvolutionPipelineOrchestrator;
import io.kairo.evolution.EvolutionStateMachine;
import io.kairo.evolution.InMemoryEvolutionRuntimeStateStore;
import io.kairo.evolution.InMemoryEvolvedSkillStore;
import io.kairo.evolution.SkillContentInjector;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for the Kairo self-evolution subsystem.
 *
 * <p>Activated only when {@code kairo.evolution.enabled=true}. Registers all evolution beans
 * (policy, trigger, stores, orchestrator, hook, skill injector) and an {@link
 * AgentBuilderCustomizer} that wires them into agents built via {@link AgentBuilder}.
 *
 * <p>Each bean uses {@code @ConditionalOnMissingBean} so applications can override any component
 * with a custom implementation.
 *
 * @since v0.9 (Experimental)
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "kairo.evolution.enabled", havingValue = "true")
@EnableConfigurationProperties(EvolutionProperties.class)
public class EvolutionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    InMemoryEvolutionRuntimeStateStore evolutionRuntimeStateStore() {
        return new InMemoryEvolutionRuntimeStateStore();
    }

    @Bean
    @ConditionalOnMissingBean
    EvolutionStateMachine evolutionStateMachine(EvolutionProperties properties) {
        return new EvolutionStateMachine(properties.getMaxConsecutiveFailures());
    }

    @Bean
    @ConditionalOnMissingBean(EvolvedSkillStore.class)
    InMemoryEvolvedSkillStore evolvedSkillStore() {
        return new InMemoryEvolvedSkillStore();
    }

    @Bean
    @ConditionalOnMissingBean(EvolutionTrigger.class)
    DefaultEvolutionTrigger evolutionTrigger() {
        return new DefaultEvolutionTrigger();
    }

    @Bean
    @ConditionalOnMissingBean(EvolutionPolicy.class)
    DefaultEvolutionPolicy evolutionPolicy(
            ModelProvider modelProvider,
            EvolvedSkillStore skillStore,
            EvolutionProperties properties) {
        return new DefaultEvolutionPolicy(
                modelProvider,
                properties.getReviewModelName(),
                properties.getIterationThreshold(),
                skillStore,
                Duration.ofSeconds(properties.getReviewTimeoutSeconds()));
    }

    @Bean
    @ConditionalOnMissingBean
    EvolutionPipelineOrchestrator evolutionPipelineOrchestrator(
            EvolutionPolicy policy,
            EvolvedSkillStore skillStore,
            EvolutionStateMachine stateMachine,
            InMemoryEvolutionRuntimeStateStore stateStore) {
        return new EvolutionPipelineOrchestrator(policy, skillStore, stateMachine, stateStore);
    }

    @Bean
    @ConditionalOnMissingBean
    EvolutionHook evolutionHook(
            EvolutionTrigger trigger,
            EvolvedSkillStore skillStore,
            EvolutionPipelineOrchestrator orchestrator) {
        return new EvolutionHook(trigger, skillStore, orchestrator);
    }

    @Bean
    @ConditionalOnMissingBean
    SkillContentInjector skillContentInjector(EvolvedSkillStore skillStore) {
        return new SkillContentInjector(skillStore);
    }

    /**
     * Registers an {@link AgentBuilderCustomizer} that wires the evolution hook and skill content
     * injector into any agent built via {@link AgentBuilder}.
     *
     * <p>This is the explicit wiring path from "bean exists" to "bean is used by agent". The
     * customizer is applied during {@link AgentBuilder#build()} via the customizer chain.
     */
    @Bean
    AgentBuilderCustomizer evolutionCustomizer(EvolutionHook hook, SkillContentInjector injector) {
        return builder -> {
            if (builder instanceof AgentBuilder agentBuilder) {
                agentBuilder.hook(hook).systemPromptContributor(injector);
            }
        };
    }
}
