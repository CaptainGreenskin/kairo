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
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolRegistry;
import java.util.Map;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the Kairo Actuator endpoint. Only active when Spring Boot Actuator is on
 * the classpath <strong>and</strong> a Kairo {@link Agent} bean is present. Gating on the bean
 * avoids a {@code NoSuchBeanDefinitionException} when the starter is on the classpath but the user
 * has opted out of the default Agent auto-configuration.
 */
@AutoConfiguration(after = AgentRuntimeAutoConfiguration.class)
@ConditionalOnClass(Endpoint.class)
@ConditionalOnBean(Agent.class)
public class AgentActuatorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AgentEndpoint agentEndpoint(Agent agent, ToolRegistry toolRegistry) {
        return new AgentEndpoint(agent, toolRegistry);
    }

    /** Actuator endpoint exposing agent runtime information at {@code /actuator/agent}. */
    @Endpoint(id = "agent")
    public static class AgentEndpoint {

        private final Agent agent;
        private final ToolRegistry toolRegistry;

        public AgentEndpoint(Agent agent, ToolRegistry toolRegistry) {
            this.agent = agent;
            this.toolRegistry = toolRegistry;
        }

        @ReadOperation
        public Map<String, Object> info() {
            return Map.of(
                    "name", agent.name(),
                    "state", agent.state().name(),
                    "tools", toolRegistry.getAll().stream().map(ToolDefinition::name).toList(),
                    "toolCount", toolRegistry.getAll().size());
        }
    }
}
