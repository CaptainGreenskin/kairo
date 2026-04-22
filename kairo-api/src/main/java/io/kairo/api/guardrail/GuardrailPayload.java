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
package io.kairo.api.guardrail;

import io.kairo.api.Experimental;
import io.kairo.api.message.Msg;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.tool.ToolResult;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Typed payload carried through the guardrail evaluation pipeline.
 *
 * <p>This is a sealed interface with exactly four permitted variants, one per {@link
 * GuardrailPhase}.
 *
 * @since v0.7 (Experimental)
 */
@Experimental("Guardrail SPI — contract may change in v0.8")
public sealed interface GuardrailPayload {

    /**
     * Payload for the {@link GuardrailPhase#PRE_MODEL} phase.
     *
     * @param messages the messages about to be sent to the model
     * @param config the model configuration for this call
     */
    record ModelInput(List<Msg> messages, ModelConfig config) implements GuardrailPayload {}

    /**
     * Payload for the {@link GuardrailPhase#POST_MODEL} phase.
     *
     * @param response the model response returned from the provider (may be null if model fails
     *     before producing a response)
     */
    record ModelOutput(@Nullable ModelResponse response) implements GuardrailPayload {}

    /**
     * Payload for the {@link GuardrailPhase#PRE_TOOL} phase.
     *
     * @param toolName the name of the tool to be executed
     * @param args the arguments to be passed to the tool
     */
    record ToolInput(String toolName, Map<String, Object> args) implements GuardrailPayload {}

    /**
     * Payload for the {@link GuardrailPhase#POST_TOOL} phase.
     *
     * @param toolName the name of the tool that was executed
     * @param result the result returned by the tool (may be null if tool fails before producing a
     *     result)
     */
    record ToolOutput(String toolName, @Nullable ToolResult result) implements GuardrailPayload {}
}
