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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.kairo.api.agent.AgentConfig;
import io.kairo.api.agent.AgentSnapshot;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.tool.*;
import io.kairo.core.hook.DefaultHookChain;
import io.kairo.core.tool.DefaultPermissionGuard;
import io.kairo.core.tool.DefaultToolExecutor;
import io.kairo.core.tool.DefaultToolRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Tests that totalToolCalls is tracked in AgentSnapshot.contextState. */
class AgentToolCallCountTest {

    private ModelProvider modelProvider;
    private DefaultToolRegistry toolRegistry;
    private DefaultToolExecutor toolExecutor;

    @BeforeEach
    void setUp() {
        modelProvider = mock(ModelProvider.class);
        toolRegistry = new DefaultToolRegistry();
        toolExecutor = new DefaultToolExecutor(toolRegistry, new DefaultPermissionGuard());
    }

    private AgentConfig.Builder baseConfig() {
        return AgentConfig.builder()
                .name("test-agent")
                .modelProvider(modelProvider)
                .toolRegistry(toolRegistry)
                .modelName("test-model")
                .maxIterations(10)
                .timeout(Duration.ofSeconds(30))
                .tokenBudget(100_000);
    }

    private DefaultReActAgent createAgent(AgentConfig config) {
        return new DefaultReActAgent(config, toolExecutor, new DefaultHookChain(), null, null);
    }

    @Test
    @DisplayName("snapshot contains totalToolCalls = 0 before any call")
    void snapshotContainsTotalToolCallsInitially() {
        DefaultReActAgent agent = createAgent(baseConfig().build());

        AgentSnapshot snapshot = agent.snapshot();

        assertThat(snapshot.contextState()).containsKey("totalToolCalls");
        assertThat(snapshot.contextState().get("totalToolCalls")).isEqualTo(0);
    }

    @Test
    @DisplayName("snapshot totalToolCalls increments after tool execution")
    void totalToolCallsIncrementsAfterToolExecution() {
        ToolDefinition echoTool =
                new ToolDefinition(
                        "echo",
                        "echoes input",
                        ToolCategory.GENERAL,
                        new JsonSchema("object", null, null, null),
                        Object.class);
        toolRegistry.register(echoTool);
        toolRegistry.registerInstance(
                "echo", (ToolHandler) input -> new ToolResult("echo", "result", false, Map.of()));

        String toolCallId = "toolu_abc123";
        ModelResponse toolUseResponse =
                new ModelResponse(
                        "resp-tool",
                        List.of(new Content.ToolUseContent(toolCallId, "echo", Map.of())),
                        new ModelResponse.Usage(10, 10, 0, 0),
                        ModelResponse.StopReason.TOOL_USE,
                        "test-model");
        ModelResponse finalResponse =
                new ModelResponse(
                        "resp-final",
                        List.of(new Content.TextContent("done")),
                        new ModelResponse.Usage(10, 10, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "test-model");

        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(Mono.just(toolUseResponse))
                .thenReturn(Mono.just(finalResponse));

        DefaultReActAgent agent = createAgent(baseConfig().build());

        StepVerifier.create(agent.call(Msg.of(MsgRole.USER, "call echo")))
                .expectNextCount(1)
                .verifyComplete();

        AgentSnapshot snapshot = agent.snapshot();
        assertThat(snapshot.contextState()).containsKey("totalToolCalls");
        assertThat((int) snapshot.contextState().get("totalToolCalls")).isGreaterThan(0);
    }

    @Test
    @DisplayName("snapshot contains modelName alongside totalToolCalls")
    void snapshotContainsBothModelNameAndTotalToolCalls() {
        String modelName = "test-model";
        DefaultReActAgent agent = createAgent(baseConfig().modelName(modelName).build());

        AgentSnapshot snapshot = agent.snapshot();

        assertThat(snapshot.contextState())
                .containsEntry("modelName", modelName)
                .containsKey("totalToolCalls");
    }
}
