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
package io.kairo.tools.agent;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentConfig;
import io.kairo.api.agent.AgentFactory;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.tool.ToolResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentSpawnToolTest {

    private AgentSpawnTool tool;
    private StubAgentFactory agentFactory;
    private AgentConfig baseConfig;

    @BeforeEach
    void setUp() {
        baseConfig =
                AgentConfig.builder()
                        .name("parent")
                        .systemPrompt("parent prompt")
                        .modelProvider(new StubModelProvider())
                        .maxIterations(10)
                        .timeout(java.time.Duration.ofMinutes(5))
                        .tokenBudget(100_000)
                        .build();
        agentFactory = new StubAgentFactory();
        tool = new AgentSpawnTool(agentFactory, baseConfig);
    }

    @Test
    void missingNameParameter() {
        ToolResult result = tool.execute(Map.of("task", "do something"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("'name' is required"));
    }

    @Test
    void blankNameParameter() {
        ToolResult result = tool.execute(Map.of("name", "   ", "task", "do something"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("'name' is required"));
    }

    @Test
    void missingTaskParameter() {
        ToolResult result = tool.execute(Map.of("name", "sub-agent"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("'task' is required"));
    }

    @Test
    void blankTaskParameter() {
        ToolResult result = tool.execute(Map.of("name", "sub-agent", "task", "   "));
        assertTrue(result.isError());
        assertTrue(result.content().contains("'task' is required"));
    }

    @Test
    void spawnWithTextResult() {
        agentFactory.response = Msg.of(MsgRole.ASSISTANT, "Task completed successfully");

        ToolResult result =
                tool.execute(Map.of("name", "worker", "task", "process data"));

        assertFalse(result.isError());
        assertTrue(result.content().contains("worker"));
        assertTrue(result.content().contains("Task completed successfully"));
        assertEquals("worker", result.metadata().get("subAgentName"));
    }

    @Test
    void spawnWithCustomSystemPrompt() {
        agentFactory.response = Msg.of(MsgRole.ASSISTANT, "done");

        tool.execute(
                Map.of(
                        "name",
                        "worker",
                        "task",
                        "do work",
                        "systemPrompt",
                        "You are a specialized agent."));

        assertEquals("You are a specialized agent.", agentFactory.lastConfig.systemPrompt());
    }

    @Test
    void spawnWithDefaultSystemPrompt() {
        agentFactory.response = Msg.of(MsgRole.ASSISTANT, "done");

        tool.execute(Map.of("name", "worker", "task", "do work"));

        assertEquals("You are a helpful sub-agent.", agentFactory.lastConfig.systemPrompt());
    }

    @Test
    void spawnWithNullResult() {
        agentFactory.response = null;

        ToolResult result = tool.execute(Map.of("name", "worker", "task", "do work"));

        assertFalse(result.isError());
        assertTrue(result.content().contains("completed with no output"));
    }

    @Test
    void spawnWithMultipleContentBlocks() {
        agentFactory.response =
                Msg.builder()
                        .role(MsgRole.ASSISTANT)
                        .addContent(new Content.TextContent("part 1"))
                        .addContent(new Content.TextContent("part 2"))
                        .build();

        ToolResult result =
                tool.execute(Map.of("name", "worker", "task", "multi-part task"));

        assertFalse(result.isError());
        assertTrue(result.content().contains("part 1"));
        assertTrue(result.content().contains("part 2"));
    }

    @Test
    void spawnWithNonTextContentFallsBackToMsgToString() {
        agentFactory.response =
                Msg.builder()
                        .role(MsgRole.ASSISTANT)
                        .addContent(new Content.ToolResultContent("id1", "result text", false))
                        .build();

        ToolResult result =
                tool.execute(Map.of("name", "worker", "task", "tool task"));

        assertFalse(result.isError());
        assertNotNull(result.content());
    }

    @Test
    void spawnExceptionReturnsError() {
        agentFactory.shouldThrow = true;

        ToolResult result =
                tool.execute(Map.of("name", "broken", "task", "will fail"));

        assertTrue(result.isError());
        assertTrue(result.content().contains("broken"));
        assertTrue(result.content().contains("Factory failed"));
    }

    @Test
    void spawnAgentCallExceptionReturnsError() {
        agentFactory.shouldFailCall = true;

        ToolResult result =
                tool.execute(Map.of("name", "failing", "task", "crash"));

        assertTrue(result.isError());
        assertTrue(result.content().contains("failing"));
        assertTrue(result.content().contains("Agent call failed"));
    }

    @Test
    void subAgentConfigInheritsFromBase() {
        agentFactory.response = Msg.of(MsgRole.ASSISTANT, "done");

        tool.execute(Map.of("name", "worker", "task", "do work"));

        assertNotNull(agentFactory.lastConfig);
        assertEquals("worker", agentFactory.lastConfig.name());
        assertEquals(10, agentFactory.lastConfig.maxIterations());
        assertEquals(100_000, agentFactory.lastConfig.tokenBudget());
    }

    @Test
    void taskMessageSentToSubAgent() {
        agentFactory.response = Msg.of(MsgRole.ASSISTANT, "done");

        tool.execute(Map.of("name", "worker", "task", "specific task description"));

        assertNotNull(agentFactory.lastStubAgent);
        assertEquals("specific task description", agentFactory.lastStubAgent.lastInputText);
    }

    /** Stub implementation of AgentFactory for testing without Mockito. */
    private static class StubAgentFactory implements AgentFactory {
        Msg response;
        AgentConfig lastConfig;
        StubAgent lastStubAgent;
        boolean shouldThrow;
        boolean shouldFailCall;

        @Override
        public Agent create(AgentConfig config) {
            if (shouldThrow) {
                throw new RuntimeException("Factory failed");
            }
            this.lastConfig = config;
            this.lastStubAgent = new StubAgent(response, shouldFailCall);
            return lastStubAgent;
        }

        @Override
        public Agent createSubAgent(Agent parent, AgentConfig config) {
            return create(config);
        }
    }

    /** Stub implementation of Agent for testing without Mockito. */
    private static class StubAgent implements Agent {
        private final Msg response;
        private final boolean shouldFail;
        String lastInputText;

        StubAgent(Msg response, boolean shouldFail) {
            this.response = response;
            this.shouldFail = shouldFail;
        }

        @Override
        public reactor.core.publisher.Mono<Msg> call(Msg input) {
            this.lastInputText = input.text();
            if (shouldFail) {
                return reactor.core.publisher.Mono.error(
                        new RuntimeException("Agent call failed"));
            }
            return reactor.core.publisher.Mono.justOrEmpty(response);
        }

        @Override
        public String id() {
            return "stub-agent";
        }

        @Override
        public String name() {
            return "stub";
        }

        @Override
        public io.kairo.api.agent.AgentState state() {
            return io.kairo.api.agent.AgentState.IDLE;
        }

        @Override
        public void interrupt() {}
    }

    /** Minimal stub ModelProvider for constructing AgentConfig. */
    private static class StubModelProvider implements ModelProvider {
        @Override
        public reactor.core.publisher.Mono<ModelResponse> call(
                java.util.List<Msg> messages, ModelConfig config) {
            return reactor.core.publisher.Mono.empty();
        }

        @Override
        public reactor.core.publisher.Flux<ModelResponse> stream(
                java.util.List<Msg> messages, ModelConfig config) {
            return reactor.core.publisher.Flux.empty();
        }

        @Override
        public String name() {
            return "stub";
        }
    }
}
