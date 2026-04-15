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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import io.kairo.api.agent.Agent;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.api.tool.ToolRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AgentBuilderTest {

    @Test
    void buildWithAllOptions() {
        ModelProvider provider = mock(ModelProvider.class);
        ToolRegistry registry = mock(ToolRegistry.class);
        ToolExecutor executor = mock(ToolExecutor.class);
        Object hook = new Object();

        Agent agent =
                AgentBuilder.create()
                        .name("builder-agent")
                        .model(provider)
                        .tools(registry)
                        .toolExecutor(executor)
                        .systemPrompt("System prompt here")
                        .maxIterations(25)
                        .timeout(Duration.ofMinutes(5))
                        .tokenBudget(50_000)
                        .modelName("gpt-4o")
                        .hook(hook)
                        .build();

        assertNotNull(agent);
        assertEquals("builder-agent", agent.name());
        assertNotNull(agent.id());
        assertInstanceOf(DefaultReActAgent.class, agent);
    }

    @Test
    void buildWithMinimalRequired() {
        ModelProvider provider = mock(ModelProvider.class);
        Agent agent = AgentBuilder.create().name("minimal").model(provider).build();
        assertNotNull(agent);
        assertEquals("minimal", agent.name());
    }

    @Test
    void missingNameThrows() {
        ModelProvider provider = mock(ModelProvider.class);
        assertThrows(
                NullPointerException.class, () -> AgentBuilder.create().model(provider).build());
    }

    @Test
    void missingModelProviderThrows() {
        assertThrows(NullPointerException.class, () -> AgentBuilder.create().name("test").build());
    }

    @Test
    void invalidMaxIterationsThrows() {
        ModelProvider provider = mock(ModelProvider.class);
        assertThrows(
                IllegalArgumentException.class,
                () -> AgentBuilder.create().name("test").model(provider).maxIterations(0).build());
    }

    @Test
    void invalidTokenBudgetThrows() {
        ModelProvider provider = mock(ModelProvider.class);
        assertThrows(
                IllegalArgumentException.class,
                () -> AgentBuilder.create().name("test").model(provider).tokenBudget(-1).build());
    }

    @Test
    void nullHookIgnored() {
        ModelProvider provider = mock(ModelProvider.class);
        Agent agent = AgentBuilder.create().name("test").model(provider).hook(null).build();
        assertNotNull(agent);
    }

    @Test
    void fluentApiChainingWorks() {
        ModelProvider provider = mock(ModelProvider.class);
        // Verify that all methods return the builder for chaining
        AgentBuilder builder =
                AgentBuilder.create()
                        .name("chained")
                        .model(provider)
                        .systemPrompt("prompt")
                        .maxIterations(10)
                        .timeout(Duration.ofMinutes(1))
                        .tokenBudget(10_000)
                        .modelName("model");
        assertNotNull(builder);
        assertNotNull(builder.build());
    }
}
