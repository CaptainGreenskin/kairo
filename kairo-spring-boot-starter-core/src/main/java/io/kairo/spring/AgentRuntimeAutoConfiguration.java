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

import io.kairo.api.agent.Agent;
import io.kairo.spring.config.AgentProperties;
import io.kairo.spring.config.CheckpointProperties;
import io.kairo.spring.config.EmbeddingProperties;
import io.kairo.spring.config.MemoryProperties;
import io.kairo.spring.config.ModelProperties;
import io.kairo.spring.config.SkillProperties;
import io.kairo.spring.config.ToolProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot auto-configuration for Kairo.
 *
 * <p>Entry point that imports per-capability configuration classes. Provides sensible defaults for
 * all runtime components while allowing full customization through {@link AgentRuntimeProperties}
 * or by defining your own beans (all beans are {@code @ConditionalOnMissingBean}).
 */
@AutoConfiguration
@EnableConfigurationProperties({
    AgentRuntimeProperties.class,
    ModelProperties.class,
    AgentProperties.class,
    ToolProperties.class,
    SkillProperties.class,
    MemoryProperties.class,
    EmbeddingProperties.class,
    CheckpointProperties.class
})
@ConditionalOnClass(Agent.class)
@Import({
    CoreAutoConfiguration.class,
    MemoryAutoConfiguration.class,
    SkillAutoConfiguration.class,
    io.kairo.spring.routing.CostRoutingAutoConfiguration.class,
    io.kairo.spring.execution.DurableExecutionAutoConfiguration.class
})
public class AgentRuntimeAutoConfiguration {}
