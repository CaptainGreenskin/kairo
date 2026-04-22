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
import java.util.Map;

/**
 * Context passed to a {@link GuardrailPolicy} for evaluation.
 *
 * @param phase the pipeline boundary point (PRE_MODEL, POST_MODEL, PRE_TOOL, POST_TOOL)
 * @param agentName the name of the agent that owns this pipeline
 * @param targetName the model name or tool name being guarded
 * @param payload the typed payload to evaluate
 * @param metadata arbitrary key-value metadata for extensibility
 * @since v0.7 (Experimental)
 */
@Experimental("Guardrail SPI — contract may change in v0.8")
public record GuardrailContext(
        GuardrailPhase phase,
        String agentName,
        String targetName,
        GuardrailPayload payload,
        Map<String, Object> metadata) {}
