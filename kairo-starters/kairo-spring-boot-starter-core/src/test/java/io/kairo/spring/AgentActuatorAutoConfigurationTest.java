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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentState;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolRegistry;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.spring.AgentActuatorAutoConfiguration.AgentEndpoint;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Tests for {@link AgentActuatorAutoConfiguration}. */
class AgentActuatorAutoConfigurationTest {

    private static Agent mockAgent(String name, AgentState state) {
        Agent agent = mock(Agent.class);
        when(agent.name()).thenReturn(name);
        when(agent.state()).thenReturn(state);
        return agent;
    }

    private static ToolRegistry mockRegistry(List<ToolDefinition> tools) {
        ToolRegistry registry = mock(ToolRegistry.class);
        when(registry.getAll()).thenReturn(tools);
        return registry;
    }

    private static ToolDefinition toolDef(String name) {
        return new ToolDefinition(
                name,
                "desc",
                ToolCategory.FILE_AND_CODE,
                null,
                Object.class,
                Duration.ofSeconds(10),
                ToolSideEffect.READ_ONLY,
                null);
    }

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(AgentActuatorAutoConfiguration.class));

    @Test
    void withAgentBean_registersAgentEndpoint() {
        runner.withBean(Agent.class, () -> mockAgent("my-agent", AgentState.IDLE))
                .withBean(ToolRegistry.class, () -> mockRegistry(List.of()))
                .run(context -> assertThat(context).hasSingleBean(AgentEndpoint.class));
    }

    @Test
    void withoutAgentBean_doesNotRegisterEndpoint() {
        runner.withBean(ToolRegistry.class, () -> mockRegistry(List.of()))
                .run(context -> assertThat(context).doesNotHaveBean(AgentEndpoint.class));
    }

    @Test
    void conditionalOnMissingBean_userCanProvideCustomEndpoint() {
        AgentEndpoint custom =
                new AgentEndpoint(mockAgent("custom", AgentState.IDLE), mockRegistry(List.of()));
        runner.withBean(Agent.class, () -> mockAgent("default", AgentState.IDLE))
                .withBean(ToolRegistry.class, () -> mockRegistry(List.of()))
                .withBean("customAgentEndpoint", AgentEndpoint.class, () -> custom)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(AgentEndpoint.class);
                            assertThat(context.getBean(AgentEndpoint.class)).isSameAs(custom);
                        });
    }

    @Test
    void info_returnsAgentName() {
        Agent agent = mockAgent("kairo-agent", AgentState.IDLE);
        ToolRegistry registry = mockRegistry(List.of());
        AgentEndpoint endpoint = new AgentEndpoint(agent, registry);

        Map<String, Object> info = endpoint.info();

        assertThat(info).containsEntry("name", "kairo-agent");
    }

    @Test
    void info_returnsStateAsString() {
        Agent agent = mockAgent("x", AgentState.RUNNING);
        ToolRegistry registry = mockRegistry(List.of());
        AgentEndpoint endpoint = new AgentEndpoint(agent, registry);

        Map<String, Object> info = endpoint.info();

        assertThat(info.get("state")).isEqualTo("RUNNING");
    }

    @Test
    void info_stateIsNotNull() {
        Agent agent = mockAgent("x", AgentState.IDLE);
        ToolRegistry registry = mockRegistry(List.of());
        AgentEndpoint endpoint = new AgentEndpoint(agent, registry);

        assertThat(endpoint.info().get("state")).isNotNull();
    }

    @Test
    void info_toolsIsListType() {
        Agent agent = mockAgent("x", AgentState.IDLE);
        ToolRegistry registry = mockRegistry(List.of(toolDef("read"), toolDef("write")));
        AgentEndpoint endpoint = new AgentEndpoint(agent, registry);

        Object tools = endpoint.info().get("tools");

        assertThat(tools).isInstanceOf(List.class);
    }

    @Test
    void info_toolCountMatchesToolsListLength() {
        Agent agent = mockAgent("x", AgentState.IDLE);
        ToolRegistry registry = mockRegistry(List.of(toolDef("read"), toolDef("write")));
        AgentEndpoint endpoint = new AgentEndpoint(agent, registry);

        Map<String, Object> info = endpoint.info();
        List<?> tools = (List<?>) info.get("tools");
        int toolCount = (int) info.get("toolCount");

        assertThat(toolCount).isEqualTo(tools.size());
    }

    @Test
    void info_toolsContainsToolNames() {
        Agent agent = mockAgent("x", AgentState.IDLE);
        ToolRegistry registry = mockRegistry(List.of(toolDef("read"), toolDef("bash")));
        AgentEndpoint endpoint = new AgentEndpoint(agent, registry);

        @SuppressWarnings("unchecked")
        List<String> tools = (List<String>) endpoint.info().get("tools");

        assertThat(tools).containsExactlyInAnyOrder("read", "bash");
    }

    @Test
    void endpointAnnotation_hasIdAgent() {
        Endpoint annotation = AgentEndpoint.class.getAnnotation(Endpoint.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.id()).isEqualTo("agent");
    }

    @Test
    void info_emptyToolRegistry_toolCountIsZero() {
        Agent agent = mockAgent("x", AgentState.IDLE);
        ToolRegistry registry = mockRegistry(List.of());
        AgentEndpoint endpoint = new AgentEndpoint(agent, registry);

        Map<String, Object> info = endpoint.info();

        assertThat(info.get("toolCount")).isEqualTo(0);
        assertThat((List<?>) info.get("tools")).isEmpty();
    }
}
