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
package io.kairo.core.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.core.shutdown.GracefulShutdownManager;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link DefaultAgentFactory}. */
class DefaultAgentFactoryTest {

    private static AgentConfig buildConfig(String name) {
        return AgentConfig.builder()
                .name(name)
                .modelProvider(mock(ModelProvider.class))
                .modelName("test-model")
                .maxIterations(5)
                .tokenBudget(100_000)
                .build();
    }

    @Test
    void create_withToolExecutorOnly_returnsNonNullAgent() {
        DefaultAgentFactory factory = new DefaultAgentFactory(mock(ToolExecutor.class));
        Agent agent = factory.create(buildConfig("agent-1"));
        assertThat(agent).isNotNull();
    }

    @Test
    void create_withShutdownManager_returnsAgent() {
        DefaultAgentFactory factory =
                new DefaultAgentFactory(mock(ToolExecutor.class), new GracefulShutdownManager());
        Agent agent = factory.create(buildConfig("agent-2"));
        assertThat(agent).isNotNull();
    }

    @Test
    void create_multipleAgents_returnsDifferentInstances() {
        DefaultAgentFactory factory = new DefaultAgentFactory(mock(ToolExecutor.class));
        Agent a1 = factory.create(buildConfig("agent-a"));
        Agent a2 = factory.create(buildConfig("agent-b"));
        assertThat(a1).isNotSameAs(a2);
    }

    @Test
    void create_returnsDefaultReActAgent() {
        DefaultAgentFactory factory = new DefaultAgentFactory(mock(ToolExecutor.class));
        Agent agent = factory.create(buildConfig("react-agent"));
        assertThat(agent).isInstanceOf(DefaultReActAgent.class);
    }

    @Test
    void createSubAgent_withParent_returnsNewAgent() {
        DefaultAgentFactory factory = new DefaultAgentFactory(mock(ToolExecutor.class));
        Agent parent = factory.create(buildConfig("parent"));
        Agent child = factory.createSubAgent(parent, buildConfig("child"));
        assertThat(child).isNotNull().isNotSameAs(parent);
    }

    @Test
    void createSubAgent_parentIsNotDefaultReActAgent_returnsAgentWithoutParentHistory() {
        DefaultAgentFactory factory = new DefaultAgentFactory(mock(ToolExecutor.class));
        Agent nonReact = mock(Agent.class);
        Agent child = factory.createSubAgent(nonReact, buildConfig("child-of-mock"));
        assertThat(child).isNotNull();
    }
}
