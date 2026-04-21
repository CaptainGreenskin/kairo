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

import io.kairo.api.hook.HookResult;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.message.MsgBuilder;
import java.util.List;
import java.util.Map;

/**
 * Hook result interpretation and shared message-building utilities.
 *
 * <p>Handles the common patterns of applying hook injections (messages, context) into conversation
 * history, applying model config overrides from hooks, and building tool result / system context
 * messages.
 *
 * <p>Package-private: not part of the public API.
 */
class HookDecisionApplier {

    private final ReActLoopContext ctx;

    HookDecisionApplier(ReActLoopContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Apply hook injections (injected message and/or injected context) into the conversation
     * history. This is the common pattern shared by pre-reasoning and pre-acting hooks.
     */
    void applyInjections(HookResult<?> hookResult, List<Msg> history) {
        if (hookResult.hasInjectedMessage()) {
            Msg injected =
                    Msg.builder()
                            .role(hookResult.injectedMessage().role())
                            .contents(hookResult.injectedMessage().contents())
                            .metadata("hook_source", hookResult.hookSource())
                            .metadata("hook_decision", "INJECT")
                            .verbatimPreserved(true)
                            .build();
            history.add(injected);
        }
        if (hookResult.hasInjectedContext()) {
            history.add(systemHookContextMessage(hookResult.injectedContext()));
        }
    }

    /** Build a SYSTEM message wrapping hook-injected context. */
    Msg systemHookContextMessage(String injectedContext) {
        return Msg.builder()
                .role(MsgRole.SYSTEM)
                .addContent(new Content.TextContent("[Hook Context] " + injectedContext))
                .build();
    }

    /** Apply hook-provided override fields onto the base model config. */
    ModelConfig applyReasoningConfigOverrides(
            ModelConfig modelConfig, Map<String, Object> overrides) {
        ModelConfig.Builder cfgBuilder =
                ModelConfig.builder()
                        .model(modelConfig.model())
                        .maxTokens(modelConfig.maxTokens())
                        .temperature(modelConfig.temperature())
                        .systemPrompt(modelConfig.systemPrompt())
                        .tools(modelConfig.tools());
        if (modelConfig.systemPromptParts() != null) {
            cfgBuilder.systemPromptParts(modelConfig.systemPromptParts());
        }
        if (modelConfig.systemPromptSegments() != null) {
            cfgBuilder.segments(modelConfig.systemPromptSegments());
        }

        Object modelOverride = overrides.get("model");
        if (modelOverride instanceof String m) {
            cfgBuilder.model(m);
        }

        Object maxTokensOverride = overrides.get("maxTokens");
        if (maxTokensOverride instanceof Number n) {
            cfgBuilder.maxTokens(n.intValue());
        }

        Object tempOverride = overrides.get("temperature");
        if (tempOverride instanceof Number n) {
            cfgBuilder.temperature(n.doubleValue());
        }
        return cfgBuilder.build();
    }

    /**
     * Build a tool result {@link Msg} from a list of tool results. Each tool result is added as a
     * {@link Content.ToolResultContent} block.
     */
    Msg buildToolResultMsg(List<ToolResult> results) {
        MsgBuilder builder = MsgBuilder.create().role(MsgRole.TOOL).sourceAgentId(ctx.agentId());

        for (ToolResult result : results) {
            builder.addToolResult(result.toolUseId(), result.content(), result.isError());
        }

        return builder.build();
    }
}
