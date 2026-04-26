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
import static org.mockito.Mockito.mock;

import io.kairo.api.agent.AgentConfig;
import io.kairo.api.hook.HookResult;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.core.context.TokenBudgetManager;
import io.kairo.core.hook.DefaultHookChain;
import io.kairo.core.model.ModelFallbackManager;
import io.kairo.core.shutdown.GracefulShutdownManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link HookDecisionApplier}. */
class HookDecisionApplierTest {

    private HookDecisionApplier applier;

    @BeforeEach
    void setUp() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("test-agent")
                        .modelProvider(mock(ModelProvider.class))
                        .modelName("test-model")
                        .maxIterations(10)
                        .tokenBudget(200_000)
                        .build();

        ErrorRecoveryStrategy errorRecovery =
                new ErrorRecoveryStrategy(
                        mock(ModelProvider.class), null, new ModelFallbackManager(List.of()));

        ReActLoopContext ctx =
                new ReActLoopContext(
                        "agent-id",
                        "test-agent",
                        config,
                        new DefaultHookChain(),
                        null,
                        mock(ToolExecutor.class),
                        errorRecovery,
                        new TokenBudgetManager(200_000, 8_096),
                        new GracefulShutdownManager(),
                        null,
                        null);

        applier = new HookDecisionApplier(ctx);
    }

    // ===== applyInjections: no injections =====

    @Test
    void applyInjections_proceed_doesNotModifyHistory() {
        HookResult<String> result = HookResult.proceed("event");
        List<Msg> history = new ArrayList<>();

        applier.applyInjections(result, history);

        assertThat(history).isEmpty();
    }

    // ===== applyInjections: injected message =====

    @Test
    void applyInjections_inject_addsMessageToHistory() {
        Msg injected = Msg.of(MsgRole.USER, "hook injected message");
        HookResult<String> result = HookResult.inject("event", injected, "my-hook");
        List<Msg> history = new ArrayList<>();

        applier.applyInjections(result, history);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).text()).isEqualTo("hook injected message");
        assertThat(history.get(0).metadata()).containsEntry("hook_decision", "INJECT");
        assertThat(history.get(0).metadata()).containsEntry("hook_source", "my-hook");
    }

    // ===== applyInjections: injected context =====

    @Test
    void applyInjections_withContext_addsSystemMessageToHistory() {
        HookResult<String> result = HookResult.withContext("event", "important context");
        List<Msg> history = new ArrayList<>();

        applier.applyInjections(result, history);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).role()).isEqualTo(MsgRole.SYSTEM);
        assertThat(history.get(0).text()).contains("[Hook Context]");
        assertThat(history.get(0).text()).contains("important context");
    }

    // ===== systemHookContextMessage =====

    @Test
    void systemHookContextMessage_hasCorrectRoleAndText() {
        Msg msg = applier.systemHookContextMessage("rule: always be helpful");

        assertThat(msg.role()).isEqualTo(MsgRole.SYSTEM);
        assertThat(msg.text()).contains("[Hook Context]");
        assertThat(msg.text()).contains("rule: always be helpful");
    }

    // ===== applyReasoningConfigOverrides =====

    @Test
    void applyReasoningConfigOverrides_noOverrides_preservesConfig() {
        ModelConfig base =
                ModelConfig.builder()
                        .model("claude-sonnet")
                        .maxTokens(4096)
                        .temperature(0.7)
                        .tools(List.of())
                        .build();

        ModelConfig result = applier.applyReasoningConfigOverrides(base, Map.of());

        assertThat(result.model()).isEqualTo("claude-sonnet");
        assertThat(result.maxTokens()).isEqualTo(4096);
        assertThat(result.temperature()).isEqualTo(0.7);
    }

    @Test
    void applyReasoningConfigOverrides_modelOverride_replacesModel() {
        ModelConfig base =
                ModelConfig.builder()
                        .model("claude-sonnet")
                        .maxTokens(4096)
                        .temperature(0.7)
                        .tools(List.of())
                        .build();

        ModelConfig result =
                applier.applyReasoningConfigOverrides(base, Map.of("model", "claude-opus"));

        assertThat(result.model()).isEqualTo("claude-opus");
        assertThat(result.maxTokens()).isEqualTo(4096);
    }

    @Test
    void applyReasoningConfigOverrides_maxTokensAndTemperatureOverride() {
        ModelConfig base =
                ModelConfig.builder()
                        .model("claude-sonnet")
                        .maxTokens(4096)
                        .temperature(0.7)
                        .tools(List.of())
                        .build();

        ModelConfig result =
                applier.applyReasoningConfigOverrides(
                        base, Map.of("maxTokens", 2048, "temperature", 0.5));

        assertThat(result.maxTokens()).isEqualTo(2048);
        assertThat(result.temperature()).isEqualTo(0.5);
    }
}
