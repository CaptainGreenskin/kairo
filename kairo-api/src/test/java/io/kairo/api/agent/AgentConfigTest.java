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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import io.kairo.api.model.ModelProvider;
import io.kairo.api.tool.ToolRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentConfigTest {

    @Test
    void builderWithAllFields() {
        ModelProvider provider = mock(ModelProvider.class);
        ToolRegistry registry = mock(ToolRegistry.class);
        Object hook = new Object();

        AgentConfig config =
                AgentConfig.builder()
                        .name("test-agent")
                        .systemPrompt("You are helpful")
                        .modelProvider(provider)
                        .toolRegistry(registry)
                        .maxIterations(25)
                        .timeout(Duration.ofMinutes(5))
                        .tokenBudget(100_000)
                        .modelName("gpt-4o")
                        .mcpCapability(new McpCapabilityConfig(List.of(), 128, true, "calendar"))
                        .addHook(hook)
                        .build();

        assertEquals("test-agent", config.name());
        assertEquals("You are helpful", config.systemPrompt());
        assertSame(provider, config.modelProvider());
        assertSame(registry, config.toolRegistry());
        assertEquals(25, config.maxIterations());
        assertEquals(Duration.ofMinutes(5), config.timeout());
        assertEquals(100_000, config.tokenBudget());
        assertEquals("gpt-4o", config.modelName());
        assertEquals("calendar", config.mcpCapability().toolSearchQuery());
        assertEquals(1, config.hooks().size());
        assertSame(hook, config.hooks().get(0));
    }

    @Test
    void builderDefaults() {
        ModelProvider provider = mock(ModelProvider.class);
        AgentConfig config = AgentConfig.builder().name("test").modelProvider(provider).build();

        assertEquals(100, config.maxIterations());
        assertEquals(Duration.ofMinutes(10), config.timeout());
        assertEquals(200_000, config.tokenBudget());
        assertNull(config.systemPrompt());
        assertNull(config.toolRegistry());
        assertNull(config.modelName());
        assertNull(config.mcpCapability().toolSearchQuery());
        assertTrue(config.hooks().isEmpty());
    }

    @Test
    void nameIsRequired() {
        ModelProvider provider = mock(ModelProvider.class);
        assertThrows(
                NullPointerException.class,
                () -> AgentConfig.builder().modelProvider(provider).build());
    }

    @Test
    void modelProviderIsRequired() {
        assertThrows(NullPointerException.class, () -> AgentConfig.builder().name("test").build());
    }

    @Test
    void hooksListIsImmutable() {
        ModelProvider provider = mock(ModelProvider.class);
        AgentConfig config = AgentConfig.builder().name("test").modelProvider(provider).build();
        assertThrows(UnsupportedOperationException.class, () -> config.hooks().add(null));
    }
}
