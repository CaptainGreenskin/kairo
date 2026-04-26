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
package io.kairo.api.agent.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentConfig;
import io.kairo.api.agent.AgentState;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * Contract-level tests for the Agent API surface in kairo-api.
 *
 * <p>Validates Agent interface contract, AgentState transitions, ModelConfig defaults, and
 * AgentConfig validation rules.
 */
@Tag("integration")
class AgentContractTest {

    // ================================
    // Agent interface contract
    // ================================

    @Test
    @DisplayName("Agent interface declares exactly the required abstract methods")
    void agent_interfaceMethodContract() {
        Set<String> methodNames =
                Arrays.stream(Agent.class.getDeclaredMethods())
                        .filter(m -> !m.isDefault())
                        .map(Method::getName)
                        .collect(Collectors.toSet());

        assertTrue(methodNames.contains("call"), "Agent must declare call()");
        assertTrue(methodNames.contains("id"), "Agent must declare id()");
        assertTrue(methodNames.contains("name"), "Agent must declare name()");
        assertTrue(methodNames.contains("state"), "Agent must declare state()");
        assertTrue(methodNames.contains("interrupt"), "Agent must declare interrupt()");
    }

    @Test
    @DisplayName("Agent.call() return type is Mono<Msg>")
    void agent_callReturnType() throws NoSuchMethodException {
        Method callMethod = Agent.class.getMethod("call", Msg.class);
        assertEquals(Mono.class, callMethod.getReturnType());
    }

    @Test
    @DisplayName("Agent interface is implementable with minimal stub")
    void agent_implementable() {
        Agent stub =
                new Agent() {
                    @Override
                    public Mono<Msg> call(Msg input) {
                        return Mono.just(Msg.of(MsgRole.ASSISTANT, "response"));
                    }

                    @Override
                    public String id() {
                        return "test-id";
                    }

                    @Override
                    public String name() {
                        return "test-agent";
                    }

                    @Override
                    public AgentState state() {
                        return AgentState.IDLE;
                    }

                    @Override
                    public void interrupt() {}
                };

        assertEquals("test-id", stub.id());
        assertEquals("test-agent", stub.name());
        assertEquals(AgentState.IDLE, stub.state());
        assertNotNull(stub.call(Msg.of(MsgRole.USER, "hi")).block());
    }

    // ================================
    // AgentState enum transitions
    // ================================

    @Test
    @DisplayName("AgentState contains exactly 5 lifecycle states")
    void agentState_completeness() {
        AgentState[] values = AgentState.values();
        assertEquals(5, values.length);

        Set<String> names = Arrays.stream(values).map(Enum::name).collect(Collectors.toSet());
        assertEquals(Set.of("IDLE", "RUNNING", "SUSPENDED", "COMPLETED", "FAILED"), names);
    }

    @Test
    @DisplayName("AgentState valueOf round-trips correctly for all values")
    void agentState_valueOfRoundTrip() {
        for (AgentState state : AgentState.values()) {
            assertEquals(state, AgentState.valueOf(state.name()));
        }
    }

    // ================================
    // ModelConfig defaults
    // ================================

    @Test
    @DisplayName("ModelConfig default constants have expected values")
    void modelConfig_defaultConstants() {
        assertEquals("claude-sonnet-4-20250514", ModelConfig.DEFAULT_MODEL);
        assertEquals(8096, ModelConfig.DEFAULT_MAX_TOKENS);
        assertEquals(1.0, ModelConfig.DEFAULT_TEMPERATURE, 0.001);
    }

    @Test
    @DisplayName("ModelConfig builder requires model to be set")
    void modelConfig_builderRequiresModel() {
        assertThrows(NullPointerException.class, () -> ModelConfig.builder().build());
    }

    // ================================
    // AgentConfig validation rules
    // ================================

    @Test
    @DisplayName("AgentConfig builder requires name")
    void agentConfig_requiresName() {
        ModelProvider provider = mock(ModelProvider.class);
        assertThrows(
                NullPointerException.class,
                () -> AgentConfig.builder().modelProvider(provider).build());
    }

    @Test
    @DisplayName("AgentConfig builder requires modelProvider")
    void agentConfig_requiresModelProvider() {
        assertThrows(NullPointerException.class, () -> AgentConfig.builder().name("test").build());
    }

    @Test
    @DisplayName("AgentConfig builder defaults are sensible")
    void agentConfig_builderDefaults() {
        ModelProvider provider = mock(ModelProvider.class);
        AgentConfig config = AgentConfig.builder().name("test").modelProvider(provider).build();

        assertEquals(100, config.maxIterations());
        assertEquals(Duration.ofMinutes(10), config.timeout());
        assertEquals(200_000, config.tokenBudget());
        assertNull(config.systemPrompt());
        assertNull(config.toolRegistry());
        assertNull(config.modelName());
        assertTrue(config.hooks().isEmpty());
        assertTrue(config.mcpCapability().serverConfigs().isEmpty());
    }

    @Test
    @DisplayName("AgentConfig hooks list is immutable")
    void agentConfig_listsAreImmutable() {
        ModelProvider provider = mock(ModelProvider.class);
        AgentConfig config =
                AgentConfig.builder()
                        .name("test")
                        .modelProvider(provider)
                        .addHook(new Object())
                        .build();

        assertThrows(UnsupportedOperationException.class, () -> config.hooks().add(null));
    }
}
