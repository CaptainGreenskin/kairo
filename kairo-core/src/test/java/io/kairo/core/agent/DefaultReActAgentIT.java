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

import io.kairo.api.agent.AgentConfig;
import io.kairo.api.agent.AgentState;
import io.kairo.api.hook.*;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.tool.*;
import io.kairo.core.hook.DefaultHookChain;
import io.kairo.core.middleware.DefaultMiddlewarePipeline;
import io.kairo.core.shutdown.GracefulShutdownManager;
import io.kairo.core.tool.DefaultPermissionGuard;
import io.kairo.core.tool.DefaultToolExecutor;
import io.kairo.core.tool.DefaultToolRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * End-to-end integration tests for {@link DefaultReActAgent} using a stub {@link ModelProvider}.
 *
 * <p>Verifies the full ReAct loop: tool calls, multi-turn cycles, loop termination, hook lifecycle
 * ordering, and session closure — without any real model API calls.
 */
@Tag("integration")
class DefaultReActAgentIT {

    private DefaultToolRegistry toolRegistry;
    private DefaultToolExecutor toolExecutor;
    private DefaultHookChain hookChain;

    @BeforeEach
    void setUp() {
        toolRegistry = new DefaultToolRegistry();
        toolExecutor = new DefaultToolExecutor(toolRegistry, new DefaultPermissionGuard());
        hookChain = new DefaultHookChain();
    }

    // ---- Stubs ----

    private static ModelProvider stubProvider(ModelResponse... responses) {
        var idx = new AtomicInteger();
        return new ModelProvider() {
            @Override
            public String name() {
                return "stub";
            }

            @Override
            public Mono<ModelResponse> call(List<Msg> messages, ModelConfig config) {
                int i = idx.getAndIncrement();
                return Mono.just(
                        i < responses.length ? responses[i] : responses[responses.length - 1]);
            }

            @Override
            public Flux<ModelResponse> stream(List<Msg> messages, ModelConfig config) {
                return call(messages, config).flux();
            }
        };
    }

    private static ModelResponse textResponse(String text) {
        return new ModelResponse(
                "resp-text",
                List.of(new Content.TextContent(text)),
                new ModelResponse.Usage(10, 20, 0, 0),
                ModelResponse.StopReason.END_TURN,
                "stub-model");
    }

    private static ModelResponse toolCallResponse(String toolName, Map<String, Object> input) {
        return new ModelResponse(
                "resp-tool",
                List.of(new Content.ToolUseContent("tc-1", toolName, input)),
                new ModelResponse.Usage(15, 25, 0, 0),
                ModelResponse.StopReason.TOOL_USE,
                "stub-model");
    }

    private AgentConfig.Builder baseConfig(ModelProvider provider) {
        return AgentConfig.builder()
                .name("it-agent")
                .modelProvider(provider)
                .toolRegistry(toolRegistry)
                .modelName("stub-model")
                .maxIterations(10)
                .timeout(Duration.ofSeconds(10))
                .tokenBudget(100_000);
    }

    private DefaultReActAgent createAgent(AgentConfig config) {
        return new DefaultReActAgent(
                config,
                toolExecutor,
                hookChain,
                new DefaultMiddlewarePipeline(List.of()),
                new GracefulShutdownManager());
    }

    private void registerEchoTool() {
        ToolDefinition echo =
                new ToolDefinition(
                        "echo",
                        "echoes input",
                        ToolCategory.GENERAL,
                        new JsonSchema("object", null, null, null),
                        Object.class);
        toolRegistry.register(echo);
        toolRegistry.registerInstance(
                "echo",
                (ToolHandler)
                        input ->
                                new ToolResult(
                                        "echo", "echoed:" + input.get("text"), false, Map.of()));
    }

    // ---- Test scenarios ----

    @Test
    void singleTurnNoToolCall() {
        var provider = stubProvider(textResponse("Direct answer."));
        var agent = createAgent(baseConfig(provider).build());

        StepVerifier.create(agent.call(Msg.of(MsgRole.USER, "hello")))
                .assertNext(
                        msg -> {
                            assertThat(msg.role()).isEqualTo(MsgRole.ASSISTANT);
                            assertThat(msg.text()).contains("Direct answer.");
                        })
                .verifyComplete();

        assertThat(agent.state()).isEqualTo(AgentState.COMPLETED);
    }

    @Test
    void singleTurnToolCallThenFinalResponse() {
        registerEchoTool();
        var provider =
                stubProvider(
                        toolCallResponse("echo", Map.of("text", "hello")),
                        textResponse("Echo done."));
        var agent = createAgent(baseConfig(provider).build());

        StepVerifier.create(agent.call(Msg.of(MsgRole.USER, "echo hello")))
                .assertNext(
                        msg -> {
                            assertThat(msg.role()).isEqualTo(MsgRole.ASSISTANT);
                            assertThat(msg.text()).contains("Echo done.");
                        })
                .verifyComplete();

        assertThat(agent.state()).isEqualTo(AgentState.COMPLETED);
    }

    @Test
    void multiTurnLoopTwoToolCallsThenFinalResponse() {
        registerEchoTool();
        var provider =
                stubProvider(
                        toolCallResponse("echo", Map.of("text", "first")),
                        toolCallResponse("echo", Map.of("text", "second")),
                        textResponse("All done."));
        var agent = createAgent(baseConfig(provider).build());

        StepVerifier.create(agent.call(Msg.of(MsgRole.USER, "multi-step")))
                .assertNext(
                        msg -> {
                            assertThat(msg.role()).isEqualTo(MsgRole.ASSISTANT);
                            assertThat(msg.text()).contains("All done.");
                        })
                .verifyComplete();

        assertThat(agent.state()).isEqualTo(AgentState.COMPLETED);
    }

    @Test
    void loopTerminatesWhenMaxIterationsReached() {
        registerEchoTool();
        // Model always requests a tool — never terminates on its own
        var provider = stubProvider(toolCallResponse("echo", Map.of("text", "loop")));
        var agent = createAgent(baseConfig(provider).maxIterations(2).build());

        StepVerifier.create(agent.call(Msg.of(MsgRole.USER, "loop")))
                .assertNext(
                        msg -> {
                            assertThat(msg.role()).isEqualTo(MsgRole.ASSISTANT);
                            assertThat(msg.text())
                                    .containsAnyOf(
                                            "max iterations", "maximum iteration limit", "limit");
                        })
                .verifyComplete();
    }

    @Test
    void loopDetectorAbortsOnRepeatedToolCallWithSameArgs() {
        registerEchoTool();
        // Same tool + same args on every call triggers LoopDetectionException after 3 repeats
        var provider = stubProvider(toolCallResponse("echo", Map.of("text", "same")));
        var agent = createAgent(baseConfig(provider).maxIterations(20).build());

        StepVerifier.create(agent.call(Msg.of(MsgRole.USER, "repeat")))
                .expectErrorMatches(
                        e ->
                                e instanceof LoopDetectionException
                                        || (e.getMessage() != null
                                                && e.getMessage().contains("Loop detected")))
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void hooksFireAtCorrectLifecyclePoints() {
        var preReasoningCount = new AtomicInteger();
        var postReasoningCount = new AtomicInteger();
        var preActingCount = new AtomicInteger();
        var postActingCount = new AtomicInteger();

        hookChain.register(
                new Object() {
                    @PreReasoning
                    public void onPre(PreReasoningEvent e) {
                        preReasoningCount.incrementAndGet();
                    }

                    @PostReasoning
                    public void onPost(PostReasoningEvent e) {
                        postReasoningCount.incrementAndGet();
                    }

                    @PreActing
                    public HookResult<PreActingEvent> onPreAct(PreActingEvent e) {
                        preActingCount.incrementAndGet();
                        return HookResult.proceed(e);
                    }

                    @PostActing
                    public void onPostAct(PostActingEvent e) {
                        postActingCount.incrementAndGet();
                    }
                });

        registerEchoTool();
        var provider =
                stubProvider(toolCallResponse("echo", Map.of("text", "x")), textResponse("Done."));
        var agent = createAgent(baseConfig(provider).build());

        agent.call(Msg.of(MsgRole.USER, "go")).block();

        assertThat(preReasoningCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(postReasoningCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(preActingCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(postActingCount.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void sessionEndHookFiresAfterCompletion() {
        var sessionEndCount = new AtomicInteger();

        hookChain.register(
                new Object() {
                    @OnSessionEnd
                    public void onEnd(SessionEndEvent e) {
                        sessionEndCount.incrementAndGet();
                    }
                });

        var provider = stubProvider(textResponse("Finished."));
        var agent = createAgent(baseConfig(provider).build());

        agent.call(Msg.of(MsgRole.USER, "ping")).block();

        assertThat(sessionEndCount.get()).isEqualTo(1);
        assertThat(agent.state()).isEqualTo(AgentState.COMPLETED);
    }
}
