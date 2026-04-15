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

import io.kairo.api.agent.AgentConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.core.tool.DefaultToolRegistry;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoordinatorAgentTest {

    private final ModelProvider mockProvider = mock(ModelProvider.class);

    /** Helper to create a simple ToolDefinition with a given name and category. */
    private static ToolDefinition toolDef(String name, String description, ToolCategory category) {
        JsonSchema schema = new JsonSchema("object", null, List.of(), description);
        return new ToolDefinition(name, description, category, schema, Object.class);
    }

    /** Use reflection to get the private AgentConfig from DefaultReActAgent. */
    private static AgentConfig getConfig(CoordinatorAgent agent) {
        try {
            Field configField = DefaultReActAgent.class.getDeclaredField("config");
            configField.setAccessible(true);
            return (AgentConfig) configField.get(agent);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to access config field", e);
        }
    }

    @Test
    void toolFiltering_onlyKeepsAgentAndTaskTools() {
        DefaultToolRegistry registry = new DefaultToolRegistry();
        registry.register(toolDef("read_file", "Read file", ToolCategory.FILE_AND_CODE));
        registry.register(toolDef("exec_cmd", "Execute command", ToolCategory.EXECUTION));
        registry.register(toolDef("agent_spawn", "Spawn agent", ToolCategory.AGENT_AND_TASK));
        registry.register(toolDef("task_list", "List tasks", ToolCategory.AGENT_AND_TASK));
        registry.register(toolDef("web_search", "Web search", ToolCategory.INFORMATION));

        AgentConfig baseConfig =
                AgentConfig.builder()
                        .name("coord")
                        .modelProvider(mockProvider)
                        .toolRegistry(registry)
                        .build();

        CoordinatorConfig coordConfig = CoordinatorConfig.of(baseConfig);
        CoordinatorAgent agent = new CoordinatorAgent(coordConfig);

        // The filtered registry should only contain AGENT_AND_TASK tools
        AgentConfig filteredConfig = getConfig(agent);
        List<ToolDefinition> filteredTools = filteredConfig.toolRegistry().getAll();

        assertEquals(2, filteredTools.size(), "Should only keep AGENT_AND_TASK tools");
        assertTrue(
                filteredTools.stream().allMatch(t -> t.category() == ToolCategory.AGENT_AND_TASK),
                "All remaining tools should be AGENT_AND_TASK category");
        assertTrue(
                filteredTools.stream().anyMatch(t -> t.name().equals("agent_spawn")),
                "agent_spawn should be retained");
        assertTrue(
                filteredTools.stream().anyMatch(t -> t.name().equals("task_list")),
                "task_list should be retained");
    }

    @Test
    void toolFiltering_emptyRegistry_noError() {
        // With null tool registry
        AgentConfig baseConfig =
                AgentConfig.builder().name("coord-null").modelProvider(mockProvider).build();

        CoordinatorConfig coordConfig = CoordinatorConfig.of(baseConfig);
        CoordinatorAgent agent = new CoordinatorAgent(coordConfig);
        assertNotNull(agent);

        // With empty DefaultToolRegistry
        AgentConfig baseConfig2 =
                AgentConfig.builder()
                        .name("coord-empty")
                        .modelProvider(mockProvider)
                        .toolRegistry(new DefaultToolRegistry())
                        .build();

        CoordinatorConfig coordConfig2 = CoordinatorConfig.of(baseConfig2);
        CoordinatorAgent agent2 = new CoordinatorAgent(coordConfig2);
        assertNotNull(agent2);

        AgentConfig filteredConfig = getConfig(agent2);
        assertTrue(filteredConfig.toolRegistry().getAll().isEmpty());
    }

    @Test
    void systemPrompt_containsCoordinatorInstructions() {
        AgentConfig baseConfig =
                AgentConfig.builder().name("coord").modelProvider(mockProvider).build();

        CoordinatorConfig coordConfig = CoordinatorConfig.of(baseConfig);
        CoordinatorAgent agent = new CoordinatorAgent(coordConfig);

        AgentConfig config = getConfig(agent);
        String systemPrompt = config.systemPrompt();

        assertNotNull(systemPrompt);
        assertTrue(
                systemPrompt.contains("Coordinator Agent"), "Should contain 'Coordinator Agent'");
        assertTrue(
                systemPrompt.contains("MUST NOT directly read files"),
                "Should contain 'MUST NOT directly read files'");
    }

    @Test
    void systemPrompt_preservesUserPrompt() {
        String userPrompt = "You are a project manager for Java projects.";
        AgentConfig baseConfig =
                AgentConfig.builder()
                        .name("coord")
                        .modelProvider(mockProvider)
                        .systemPrompt(userPrompt)
                        .build();

        CoordinatorConfig coordConfig = CoordinatorConfig.of(baseConfig);
        CoordinatorAgent agent = new CoordinatorAgent(coordConfig);

        AgentConfig config = getConfig(agent);
        String systemPrompt = config.systemPrompt();

        assertTrue(systemPrompt.contains("Coordinator Agent"), "Should contain coordinator prefix");
        assertTrue(systemPrompt.contains(userPrompt), "Should contain user's original prompt");
        // Coordinator prefix should come before user prompt
        int coordIdx = systemPrompt.indexOf("Coordinator Agent");
        int userIdx = systemPrompt.indexOf(userPrompt);
        assertTrue(coordIdx < userIdx, "Coordinator prefix should appear before user prompt");
    }

    @Test
    void requirePlanBeforeDispatch_addsInstruction() {
        AgentConfig baseConfig =
                AgentConfig.builder().name("coord").modelProvider(mockProvider).build();

        // With requirePlanBeforeDispatch=true (default via CoordinatorConfig.of)
        CoordinatorConfig coordConfig = CoordinatorConfig.of(baseConfig);
        CoordinatorAgent agent = new CoordinatorAgent(coordConfig);

        AgentConfig config = getConfig(agent);
        assertTrue(
                config.systemPrompt().contains("MUST use enter_plan_mode"),
                "Should contain plan mode instruction when requirePlanBeforeDispatch=true");

        // With requirePlanBeforeDispatch=false
        CoordinatorConfig coordConfigNoPlan =
                CoordinatorConfig.builder(baseConfig).requirePlanBeforeDispatch(false).build();
        CoordinatorAgent agentNoPlan = new CoordinatorAgent(coordConfigNoPlan);

        AgentConfig configNoPlan = getConfig(agentNoPlan);
        assertFalse(
                configNoPlan.systemPrompt().contains("MUST use enter_plan_mode"),
                "Should NOT contain plan mode instruction when requirePlanBeforeDispatch=false");
    }

    @Test
    void config_maxConcurrentWorkers_validation() {
        AgentConfig baseConfig =
                AgentConfig.builder().name("coord").modelProvider(mockProvider).build();

        assertThrows(
                IllegalArgumentException.class,
                () -> new CoordinatorConfig(baseConfig, List.of(), 0, true),
                "maxConcurrentWorkers=0 should throw");

        assertThrows(
                IllegalArgumentException.class,
                () -> new CoordinatorConfig(baseConfig, List.of(), -1, true),
                "maxConcurrentWorkers=-1 should throw");
    }

    @Test
    void config_factoryMethods() {
        AgentConfig baseConfig =
                AgentConfig.builder().name("coord").modelProvider(mockProvider).build();

        // CoordinatorConfig.of(baseConfig) — defaults
        CoordinatorConfig defaultConfig = CoordinatorConfig.of(baseConfig);
        assertEquals(5, defaultConfig.maxConcurrentWorkers());
        assertTrue(defaultConfig.requirePlanBeforeDispatch());
        assertTrue(defaultConfig.workerTemplates().isEmpty());
        assertSame(baseConfig, defaultConfig.baseConfig());

        // Builder with custom maxConcurrentWorkers
        CoordinatorConfig customConfig =
                CoordinatorConfig.builder(baseConfig)
                        .maxConcurrentWorkers(3)
                        .requirePlanBeforeDispatch(false)
                        .build();
        assertEquals(3, customConfig.maxConcurrentWorkers());
        assertFalse(customConfig.requirePlanBeforeDispatch());

        // of() with worker templates
        AgentConfig workerConfig =
                AgentConfig.builder().name("worker").modelProvider(mockProvider).build();
        CoordinatorConfig withWorkers = CoordinatorConfig.of(baseConfig, List.of(workerConfig));
        assertEquals(1, withWorkers.workerTemplates().size());
    }

    @Test
    void buildCoordinator_fromAgentBuilder() {
        CoordinatorAgent agent =
                AgentBuilder.create().name("coord").model(mockProvider).buildCoordinator();

        assertNotNull(agent);
        assertInstanceOf(CoordinatorAgent.class, agent);
        assertEquals("coord", agent.name());
        assertNotNull(agent.coordinatorConfig());
        assertEquals(5, agent.coordinatorConfig().maxConcurrentWorkers());
        assertTrue(agent.coordinatorConfig().requirePlanBeforeDispatch());
    }

    @Test
    void buildCoordinator_withCustomSettings() {
        CoordinatorAgent agent =
                AgentBuilder.create()
                        .name("coord-custom")
                        .model(mockProvider)
                        .buildCoordinator(3, false);

        assertNotNull(agent);
        assertEquals("coord-custom", agent.name());
        assertEquals(3, agent.coordinatorConfig().maxConcurrentWorkers());
        assertFalse(agent.coordinatorConfig().requirePlanBeforeDispatch());
    }
}
