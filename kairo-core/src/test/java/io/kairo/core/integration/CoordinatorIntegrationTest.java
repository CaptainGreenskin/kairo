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
package io.kairo.core.integration;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.agent.AgentConfig;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.core.agent.CoordinatorAgent;
import io.kairo.core.agent.CoordinatorConfig;
import io.kairo.core.tool.DefaultToolRegistry;
import io.kairo.core.tool.ToolHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Integration test verifying that CoordinatorAgent only has access to orchestration tools and
 * rejects non-orchestration tool calls.
 */
class CoordinatorIntegrationTest {

    /**
     * A scripted model provider that returns pre-configured responses in sequence. Based on the
     * pattern from AgentIntegrationTest.
     */
    static class ScriptedModelProvider implements ModelProvider {
        private final List<Object> scriptedResponses = new ArrayList<>();
        private final AtomicInteger callCount = new AtomicInteger(0);

        @Override
        public String name() {
            return "scripted-mock";
        }

        ScriptedModelProvider thenReturn(ModelResponse response) {
            scriptedResponses.add(response);
            return this;
        }

        int getCallCount() {
            return callCount.get();
        }

        @Override
        public Mono<ModelResponse> call(List<Msg> messages, ModelConfig config) {
            int idx = callCount.getAndIncrement();
            if (idx >= scriptedResponses.size()) {
                return Mono.just(textResponse("Default response (no more scripted responses)"));
            }
            Object response = scriptedResponses.get(idx);
            if (response instanceof RuntimeException ex) {
                return Mono.error(ex);
            }
            return Mono.just((ModelResponse) response);
        }

        @Override
        public Flux<ModelResponse> stream(List<Msg> messages, ModelConfig config) {
            return call(messages, config).flux();
        }
    }

    static ModelResponse textResponse(String text) {
        return new ModelResponse(
                "msg_mock",
                List.of(new Content.TextContent(text)),
                new ModelResponse.Usage(100, 50, 0, 0),
                ModelResponse.StopReason.END_TURN,
                "mock-model");
    }

    static ModelResponse toolCallResponse(String toolName, Map<String, Object> input) {
        String toolId = "toolu_" + UUID.randomUUID().toString().substring(0, 12);
        return new ModelResponse(
                "msg_mock",
                List.of(new Content.ToolUseContent(toolId, toolName, input)),
                new ModelResponse.Usage(100, 50, 0, 0),
                ModelResponse.StopReason.TOOL_USE,
                "mock-model");
    }

    /** A no-op tool handler for testing. */
    public static class NoOpToolHandler implements ToolHandler {
        @Override
        public io.kairo.api.tool.ToolResult execute(Map<String, Object> input) {
            return new io.kairo.api.tool.ToolResult("noop", "executed", false, Map.of());
        }
    }

    @Test
    void coordinator_withMockProvider_onlyUsesOrchestrationTools() {
        // Setup: scripted provider that first tries to call "read_file" (should not exist),
        // then calls "agent_spawn" (should exist), then returns final text response.
        ScriptedModelProvider provider = new ScriptedModelProvider();
        // First call: model tries to use "read_file" tool
        provider.thenReturn(toolCallResponse("read_file", Map.of("path", "/tmp/test.txt")));
        // After tool error, model tries "agent_spawn" instead
        provider.thenReturn(
                toolCallResponse(
                        "agent_spawn", Map.of("name", "worker-1", "task", "Do the actual work")));
        // Final response
        provider.thenReturn(textResponse("Task dispatched to worker."));

        // Create tool registry with both FILE_AND_CODE and AGENT_AND_TASK tools
        DefaultToolRegistry registry = new DefaultToolRegistry();
        JsonSchema emptySchema = new JsonSchema("object", null, List.of(), "");

        ToolDefinition readFileTool =
                new ToolDefinition(
                        "read_file",
                        "Read file contents",
                        ToolCategory.FILE_AND_CODE,
                        emptySchema,
                        NoOpToolHandler.class);
        ToolDefinition agentSpawnTool =
                new ToolDefinition(
                        "agent_spawn",
                        "Spawn a worker agent",
                        ToolCategory.AGENT_AND_TASK,
                        emptySchema,
                        NoOpToolHandler.class);

        registry.register(readFileTool);
        registry.registerInstance("read_file", new NoOpToolHandler());
        registry.register(agentSpawnTool);
        registry.registerInstance("agent_spawn", new NoOpToolHandler());

        AgentConfig baseConfig =
                AgentConfig.builder()
                        .name("coordinator")
                        .modelProvider(provider)
                        .toolRegistry(registry)
                        .modelName("test-model")
                        .maxIterations(5)
                        .build();

        CoordinatorConfig coordConfig = CoordinatorConfig.of(baseConfig);
        CoordinatorAgent coordinator = new CoordinatorAgent(coordConfig);

        // Verify: filtered registry only has AGENT_AND_TASK tools
        AgentConfig filteredConfig = getConfig(coordinator);
        List<ToolDefinition> tools = filteredConfig.toolRegistry().getAll();

        assertEquals(1, tools.size(), "Only agent_spawn should be in filtered registry");
        assertEquals("agent_spawn", tools.get(0).name());
        assertFalse(
                tools.stream().anyMatch(t -> t.name().equals("read_file")),
                "read_file should NOT be in filtered registry");

        // Run the coordinator — it will try read_file (which won't be found in filtered executor),
        // then try agent_spawn (which should work), then finish.
        Msg response =
                coordinator
                        .call(Msg.of(MsgRole.USER, "Please review the code"))
                        .block(java.time.Duration.ofSeconds(10));

        assertNotNull(response, "Coordinator should produce a response");
        // The provider should have been called (we verify it ran through the loop)
        assertTrue(provider.getCallCount() >= 2, "Provider should have been called at least twice");
    }

    /** Use reflection to get the private AgentConfig from DefaultReActAgent. */
    private static AgentConfig getConfig(CoordinatorAgent agent) {
        try {
            java.lang.reflect.Field configField =
                    io.kairo.core.agent.DefaultReActAgent.class.getDeclaredField("config");
            configField.setAccessible(true);
            return (AgentConfig) configField.get(agent);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to access config field", e);
        }
    }
}
