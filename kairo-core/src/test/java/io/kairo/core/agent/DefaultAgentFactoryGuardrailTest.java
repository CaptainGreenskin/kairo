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

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentConfig;
import io.kairo.api.guardrail.*;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.core.shutdown.GracefulShutdownManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Regression test verifying that sub-agents created via {@link DefaultAgentFactory#createSubAgent}
 * inherit the parent's {@link GuardrailChain}.
 */
class DefaultAgentFactoryGuardrailTest {

    private ModelProvider modelProvider;
    private ToolExecutor toolExecutor;
    private GracefulShutdownManager shutdownManager;

    @BeforeEach
    void setUp() {
        modelProvider = mock(ModelProvider.class);
        toolExecutor = mock(ToolExecutor.class);
        shutdownManager = new GracefulShutdownManager();
    }

    private AgentConfig buildConfig(String name) {
        return AgentConfig.builder()
                .name(name)
                .modelProvider(modelProvider)
                .modelName("test-model")
                .maxIterations(5)
                .tokenBudget(100_000)
                .build();
    }

    private ModelResponse textResponse(String text) {
        return new ModelResponse(
                "resp-1",
                List.of(new Content.TextContent(text)),
                new ModelResponse.Usage(10, 20, 0, 0),
                ModelResponse.StopReason.END_TURN,
                "test-model");
    }

    @Test
    @DisplayName("Sub-agent created via factory inherits guardrail chain from parent")
    void subAgentInheritsGuardrailChain() {
        // Create a guardrail chain that denies all PRE_MODEL calls
        GuardrailChain chain = mock(GuardrailChain.class);
        when(chain.evaluate(any(GuardrailContext.class)))
                .thenAnswer(
                        inv -> {
                            GuardrailContext gc = inv.getArgument(0);
                            if (gc.phase() == GuardrailPhase.PRE_MODEL) {
                                return Mono.just(
                                        GuardrailDecision.deny(
                                                "Sub-agent content blocked", "test-policy"));
                            }
                            return Mono.just(GuardrailDecision.allow("test-policy"));
                        });

        // Create factory with guardrail chain
        DefaultAgentFactory factory = new DefaultAgentFactory(toolExecutor, shutdownManager, chain);

        // Create parent agent
        AgentConfig parentConfig = buildConfig("parent-agent");
        Agent parent = factory.create(parentConfig);

        // Create sub-agent — must inherit the guardrail chain
        AgentConfig subConfig = buildConfig("sub-agent");
        Agent subAgent = factory.createSubAgent(parent, subConfig);

        // Call the sub-agent — the guardrail should deny the model call
        StepVerifier.create(subAgent.call(Msg.of(MsgRole.USER, "test input")))
                .assertNext(
                        msg -> {
                            assertEquals(MsgRole.ASSISTANT, msg.role());
                            assertTrue(
                                    msg.text().contains("blocked by guardrail"),
                                    "Sub-agent should enforce guardrail, got: " + msg.text());
                        })
                .verifyComplete();

        // Model must never be called — guardrail denied it
        verify(modelProvider, never()).call(anyList(), any(ModelConfig.class));
    }

    @Test
    @DisplayName("Sub-agent without guardrail chain allows model calls normally")
    void subAgentWithoutGuardrailAllowsModelCalls() {
        // Factory without guardrail chain
        DefaultAgentFactory factory = new DefaultAgentFactory(toolExecutor, shutdownManager, null);

        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(Mono.just(textResponse("Sub-agent response")));

        AgentConfig parentConfig = buildConfig("parent-agent");
        Agent parent = factory.create(parentConfig);

        AgentConfig subConfig = buildConfig("sub-agent");
        Agent subAgent = factory.createSubAgent(parent, subConfig);

        StepVerifier.create(subAgent.call(Msg.of(MsgRole.USER, "hello")))
                .assertNext(
                        msg -> {
                            assertEquals(MsgRole.ASSISTANT, msg.role());
                            assertTrue(msg.text().contains("Sub-agent response"));
                        })
                .verifyComplete();

        verify(modelProvider, times(1)).call(anyList(), any(ModelConfig.class));
    }
}
