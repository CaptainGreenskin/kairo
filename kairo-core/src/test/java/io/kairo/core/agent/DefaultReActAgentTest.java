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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import io.kairo.api.agent.AgentConfig;
import io.kairo.api.agent.AgentState;
import io.kairo.api.hook.HookChain;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.tool.*;
import io.kairo.api.tool.ToolHandler;
import io.kairo.core.hook.DefaultHookChain;
import io.kairo.core.tool.DefaultPermissionGuard;
import io.kairo.core.tool.DefaultToolExecutor;
import io.kairo.core.tool.DefaultToolRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DefaultReActAgentTest {

    private ModelProvider modelProvider;
    private DefaultToolRegistry toolRegistry;
    private DefaultToolExecutor toolExecutor;
    private HookChain hookChain;

    @BeforeEach
    void setUp() {
        modelProvider = mock(ModelProvider.class);
        toolRegistry = new DefaultToolRegistry();
        toolExecutor = new DefaultToolExecutor(toolRegistry, new DefaultPermissionGuard());
        hookChain = new DefaultHookChain();
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
        return new DefaultReActAgent(config, toolExecutor, hookChain, null, null);
    }

    private ModelResponse textResponse(String text) {
        return new ModelResponse(
                "resp-1",
                List.of(new Content.TextContent(text)),
                new ModelResponse.Usage(10, 20, 0, 0),
                ModelResponse.StopReason.END_TURN,
                "test-model");
    }

    private ModelResponse toolCallResponse(
            String toolId, String toolName, Map<String, Object> input) {
        return new ModelResponse(
                "resp-tool",
                List.of(new Content.ToolUseContent(toolId, toolName, input)),
                new ModelResponse.Usage(15, 25, 0, 0),
                ModelResponse.StopReason.TOOL_USE,
                "test-model");
    }

    @Test
    void simpleTextResponseNoToolCalls() {
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(Mono.just(textResponse("Hello, I'm your assistant.")));

        DefaultReActAgent agent = createAgent(baseConfig().build());
        Msg input = Msg.of(MsgRole.USER, "Hi");

        StepVerifier.create(agent.call(input))
                .assertNext(
                        msg -> {
                            assertEquals(MsgRole.ASSISTANT, msg.role());
                            assertTrue(msg.text().contains("Hello, I'm your assistant."));
                        })
                .verifyComplete();

        assertEquals(AgentState.COMPLETED, agent.state());
    }

    @Test
    void fullReActLoopWithToolCall() {
        // Register a tool handler
        ToolDefinition echoTool =
                new ToolDefinition(
                        "echo",
                        "echoes input",
                        ToolCategory.GENERAL,
                        new JsonSchema("object", null, null, null),
                        Object.class);
        toolRegistry.register(echoTool);
        toolRegistry.registerInstance(
                "echo",
                (ToolHandler)
                        input ->
                                new ToolResult(
                                        "echo", "echoed: " + input.get("text"), false, Map.of()));

        AtomicInteger callCount = new AtomicInteger(0);
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenAnswer(
                        invocation -> {
                            int n = callCount.incrementAndGet();
                            if (n == 1) {
                                // First call: model wants to use echo tool
                                return Mono.just(
                                        toolCallResponse("tc-1", "echo", Map.of("text", "world")));
                            } else {
                                // Second call: model gives final answer
                                return Mono.just(textResponse("The echo result was received."));
                            }
                        });

        DefaultReActAgent agent = createAgent(baseConfig().build());

        StepVerifier.create(agent.call(Msg.of(MsgRole.USER, "echo world")))
                .assertNext(
                        msg -> {
                            assertEquals(MsgRole.ASSISTANT, msg.role());
                            assertTrue(msg.text().contains("echo result"));
                        })
                .verifyComplete();

        // Should have called model twice: once for tool call, once for final answer
        assertEquals(2, callCount.get());
        assertEquals(AgentState.COMPLETED, agent.state());
    }

    @Test
    void maxIterationGuard() {
        // Model always requests a tool call, never terminates
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(Mono.just(toolCallResponse("tc-loop", "echo", Map.of("text", "loop"))));

        ToolDefinition echoTool =
                new ToolDefinition(
                        "echo",
                        "echoes",
                        ToolCategory.GENERAL,
                        new JsonSchema("object", null, null, null),
                        Object.class);
        toolRegistry.register(echoTool);
        toolRegistry.registerInstance(
                "echo", (ToolHandler) input -> new ToolResult("echo", "result", false, Map.of()));

        DefaultReActAgent agent = createAgent(baseConfig().maxIterations(2).build());

        StepVerifier.create(agent.call(Msg.of(MsgRole.USER, "loop forever")))
                .assertNext(
                        msg -> {
                            assertEquals(MsgRole.ASSISTANT, msg.role());
                            assertTrue(
                                    msg.text().contains("max iterations")
                                            || msg.text().contains("maximum iteration limit"));
                        })
                .verifyComplete();
    }

    @Test
    void tokenBudgetGuard() {
        // First call uses all the budget
        ModelResponse bigResponse =
                new ModelResponse(
                        "resp-1",
                        List.of(new Content.ToolUseContent("tc-1", "echo", Map.of("x", "y"))),
                        new ModelResponse.Usage(50000, 60000, 0, 0),
                        ModelResponse.StopReason.TOOL_USE,
                        "m");
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(Mono.just(bigResponse));

        ToolDefinition echoTool =
                new ToolDefinition(
                        "echo",
                        "echoes",
                        ToolCategory.GENERAL,
                        new JsonSchema("object", null, null, null),
                        Object.class);
        toolRegistry.register(echoTool);
        toolRegistry.registerInstance(
                "echo", (ToolHandler) input -> new ToolResult("echo", "r", false, Map.of()));

        DefaultReActAgent agent = createAgent(baseConfig().tokenBudget(100_000).build());

        StepVerifier.create(agent.call(Msg.of(MsgRole.USER, "use budget")))
                .assertNext(msg -> assertTrue(msg.text().contains("token budget")))
                .verifyComplete();
    }

    @Test
    void interruptDuringExecution() {
        // Model call that takes a while
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(
                        Mono.delay(Duration.ofSeconds(5)).then(Mono.just(textResponse("late"))));

        DefaultReActAgent agent = createAgent(baseConfig().build());

        // Start the call and immediately interrupt
        Mono<Msg> result = agent.call(Msg.of(MsgRole.USER, "slow task"));

        // Interrupt after a short delay
        agent.interrupt();
        assertEquals(AgentState.SUSPENDED, agent.state());
    }

    @Test
    void agentIdAndNameSet() {
        DefaultReActAgent agent = createAgent(baseConfig().build());
        assertNotNull(agent.id());
        assertEquals("test-agent", agent.name());
        assertEquals(AgentState.IDLE, agent.state());
    }

    @Test
    void conversationHistoryTracked() {
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(Mono.just(textResponse("reply")));

        DefaultReActAgent agent = createAgent(baseConfig().build());
        agent.call(Msg.of(MsgRole.USER, "Hello")).block();

        // Should contain user message + assistant reply
        assertEquals(2, agent.conversationHistory().size());
        assertEquals(MsgRole.USER, agent.conversationHistory().get(0).role());
        assertEquals(MsgRole.ASSISTANT, agent.conversationHistory().get(1).role());
    }

    @Test
    void conversationHistoryIsUnmodifiable() {
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(Mono.just(textResponse("reply")));

        DefaultReActAgent agent = createAgent(baseConfig().build());
        agent.call(Msg.of(MsgRole.USER, "Hello")).block();

        assertThrows(
                UnsupportedOperationException.class, () -> agent.conversationHistory().add(null));
    }

    @Test
    void noToolExecutorReturnsErrorResults() {
        // Model wants to call a tool but no executor
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenAnswer(
                        inv -> {
                            List<Msg> msgs = inv.getArgument(0);
                            // Check if we already have tool results in history
                            boolean hasToolResult =
                                    msgs.stream().anyMatch(m -> m.role() == MsgRole.TOOL);
                            if (hasToolResult) {
                                return Mono.just(textResponse("Got the error"));
                            }
                            return Mono.just(
                                    toolCallResponse("tc-1", "echo", Map.of("text", "hi")));
                        });

        AgentConfig config = baseConfig().build();
        DefaultReActAgent agent = new DefaultReActAgent(config, null, hookChain, null, null);

        StepVerifier.create(agent.call(Msg.of(MsgRole.USER, "call tool")))
                .assertNext(msg -> assertNotNull(msg.text()))
                .verifyComplete();
    }

    @Test
    void modelProviderErrorPropagates() {
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(Mono.error(new RuntimeException("API error")));

        DefaultReActAgent agent = createAgent(baseConfig().build());

        StepVerifier.create(agent.call(Msg.of(MsgRole.USER, "fail")))
                .expectErrorMessage("API error")
                .verify();

        assertEquals(AgentState.FAILED, agent.state());
    }

    @Test
    void totalTokensUsedTracked() {
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(Mono.just(textResponse("reply")));

        DefaultReActAgent agent = createAgent(baseConfig().build());
        agent.call(Msg.of(MsgRole.USER, "Hello")).block();

        // Our textResponse() returns Usage(10, 20, 0, 0), total = 30
        assertEquals(30, agent.totalTokensUsed());
    }
}
