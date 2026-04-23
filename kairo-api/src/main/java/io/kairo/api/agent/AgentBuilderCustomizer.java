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
package io.kairo.api.agent;

import io.kairo.api.Experimental;

/**
 * Callback interface for customizing agent construction. Implementations are applied by the starter
 * during agent creation.
 *
 * <p>Example (non-Spring):
 *
 * <pre>{@code
 * AgentBuilder builder = new AgentBuilder()
 *     .name("my-agent")
 *     .hook(new EvolutionHook(...));
 * }</pre>
 *
 * <p>Example (Spring):
 *
 * <pre>{@code
 * @Bean
 * AgentBuilderCustomizer evolutionCustomizer(EvolutionHook hook) {
 *     return builder -> builder.hook(hook);
 * }
 * }</pre>
 *
 * <p>Note: The builder parameter is typed as {@code Object} to avoid kairo-api depending on
 * kairo-core's AgentBuilder. The starter casts to the concrete builder type. A typed version can be
 * introduced when AgentBuilder is promoted to kairo-api.
 *
 * @since v0.9 (Experimental)
 */
@FunctionalInterface
@Experimental("Self-Evolution SPI — contract may change in v0.10")
public interface AgentBuilderCustomizer {

    /**
     * Customize the agent builder during construction.
     *
     * @param builder the agent builder instance (typically io.kairo.core.agent.AgentBuilder)
     */
    void customize(Object builder);
}
