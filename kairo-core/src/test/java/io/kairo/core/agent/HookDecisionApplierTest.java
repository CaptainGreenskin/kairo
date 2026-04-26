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

import io.kairo.api.hook.HookResult;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HookDecisionApplierTest {

    // ctx is not used by any method under test — safe to pass null
    private final HookDecisionApplier applier = new HookDecisionApplier(null);

    @Test
    void systemHookContextMessage_returnsSystemRole() {
        Msg msg = applier.systemHookContextMessage("extra info");
        assertThat(msg.role()).isEqualTo(MsgRole.SYSTEM);
    }

    @Test
    void systemHookContextMessage_wrapsTextWithPrefix() {
        Msg msg = applier.systemHookContextMessage("my context");
        assertThat(msg.text()).contains("[Hook Context] my context");
    }

    @Test
    void applyInjections_noInjections_doesNotModifyHistory() {
        var history = new ArrayList<Msg>();
        applier.applyInjections(HookResult.proceed("event"), history);
        assertThat(history).isEmpty();
    }

    @Test
    void applyInjections_withInjectedContext_addsSystemMessage() {
        var history = new ArrayList<Msg>();
        applier.applyInjections(HookResult.withContext("event", "extra context"), history);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).role()).isEqualTo(MsgRole.SYSTEM);
        assertThat(history.get(0).text()).contains("extra context");
    }

    @Test
    void applyInjections_withInjectedMessage_addsMessageToHistory() {
        var history = new ArrayList<Msg>();
        Msg injected =
                Msg.builder()
                        .role(MsgRole.USER)
                        .addContent(new Content.TextContent("injected"))
                        .build();
        applier.applyInjections(HookResult.inject("event", injected, "test-hook"), history);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).role()).isEqualTo(MsgRole.USER);
    }

    @Test
    void applyReasoningConfigOverrides_modelOverride_replacesModel() {
        var base = ModelConfig.builder().model("claude-3").maxTokens(1000).build();
        var result = applier.applyReasoningConfigOverrides(base, Map.of("model", "claude-4"));
        assertThat(result.model()).isEqualTo("claude-4");
    }

    @Test
    void applyReasoningConfigOverrides_emptyOverrides_preservesBase() {
        var base = ModelConfig.builder().model("claude-3").maxTokens(500).build();
        var result = applier.applyReasoningConfigOverrides(base, Map.of());
        assertThat(result.model()).isEqualTo("claude-3");
        assertThat(result.maxTokens()).isEqualTo(500);
    }

    @Test
    void applyReasoningConfigOverrides_maxTokensOverride_appliesNewLimit() {
        var base = ModelConfig.builder().model("m").maxTokens(1000).build();
        var result = applier.applyReasoningConfigOverrides(base, Map.of("maxTokens", 2000));
        assertThat(result.maxTokens()).isEqualTo(2000);
    }
}
