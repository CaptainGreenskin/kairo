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
import io.kairo.core.context.CompactionThresholds;
import io.kairo.core.context.DefaultContextManager;
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
        Agent agent =
                AgentBuilder.create()
                        .name("minimal")
                        .model(provider)
                        .modelName("test-model")
                        .build();
        assertNotNull(agent);
        assertEquals("minimal", agent.name());
    }

    @Test
    void missingNameThrows() {
        ModelProvider provider = mock(ModelProvider.class);
        assertThrows(
                NullPointerException.class,
                () -> AgentBuilder.create().model(provider).modelName("test-model").build());
    }

    @Test
    void missingModelProviderThrows() {
        assertThrows(
                NullPointerException.class,
                () -> AgentBuilder.create().name("test").modelName("test-model").build());
    }

    @Test
    void invalidMaxIterationsThrows() {
        ModelProvider provider = mock(ModelProvider.class);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        AgentBuilder.create()
                                .name("test")
                                .model(provider)
                                .modelName("test-model")
                                .maxIterations(0)
                                .build());
    }

    @Test
    void invalidTokenBudgetThrows() {
        ModelProvider provider = mock(ModelProvider.class);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        AgentBuilder.create()
                                .name("test")
                                .model(provider)
                                .modelName("test-model")
                                .tokenBudget(-1)
                                .build());
    }

    @Test
    void nullHookIgnored() {
        ModelProvider provider = mock(ModelProvider.class);
        Agent agent =
                AgentBuilder.create()
                        .name("test")
                        .model(provider)
                        .modelName("test-model")
                        .hook(null)
                        .build();
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

    @Test
    void buildWithoutModelNameThrows() {
        ModelProvider provider = mock(ModelProvider.class);
        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () -> AgentBuilder.create().name("test").model(provider).build());
        assertTrue(ex.getMessage().contains("modelName is required"));
    }

    // ==================== COMPACTION THRESHOLDS WIRING ====================

    @Test
    void compactionThresholdsWiredToDefaultContextManager() {
        ModelProvider provider = mock(ModelProvider.class);
        CompactionThresholds customThresholds =
                CompactionThresholds.builder().triggerPressure(0.50f).build();

        Agent agent =
                AgentBuilder.create()
                        .name("threshold-agent")
                        .model(provider)
                        .modelName("test-model")
                        .compactionThresholds(customThresholds)
                        .build();

        assertNotNull(agent);
        assertInstanceOf(DefaultReActAgent.class, agent);
        // The agent should have a DefaultContextManager with the custom threshold.
        // Verify via the agent's context manager (which is accessible through config).
        DefaultReActAgent reactAgent = (DefaultReActAgent) agent;
        assertNotNull(reactAgent.getContextManager());
        assertInstanceOf(DefaultContextManager.class, reactAgent.getContextManager());
    }

    @Test
    void noCompactionThresholds_noAutoContextManager() {
        ModelProvider provider = mock(ModelProvider.class);

        Agent agent =
                AgentBuilder.create()
                        .name("no-threshold-agent")
                        .model(provider)
                        .modelName("test-model")
                        .build();

        assertNotNull(agent);
        // Without compactionThresholds and no explicit contextManager, it should be null
        DefaultReActAgent reactAgent = (DefaultReActAgent) agent;
        assertNull(reactAgent.getContextManager());
    }

    @Test
    void explicitContextManagerNotOverridden() {
        ModelProvider provider = mock(ModelProvider.class);
        var explicitCm = mock(io.kairo.api.context.ContextManager.class);
        CompactionThresholds customThresholds =
                CompactionThresholds.builder().triggerPressure(0.50f).build();

        Agent agent =
                AgentBuilder.create()
                        .name("explicit-cm-agent")
                        .model(provider)
                        .modelName("test-model")
                        .contextManager(explicitCm)
                        .compactionThresholds(customThresholds)
                        .build();

        assertNotNull(agent);
        DefaultReActAgent reactAgent = (DefaultReActAgent) agent;
        // Should keep the explicit context manager, not replace with DefaultContextManager
        assertSame(explicitCm, reactAgent.getContextManager());
    }
}
