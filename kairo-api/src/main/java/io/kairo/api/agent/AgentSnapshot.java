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
package io.kairo.api.agent;

import io.kairo.api.Stable;
import io.kairo.api.message.Msg;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of an agent's runtime state at a point in time.
 *
 * <p>Captures the state necessary to resume an agent from where it left off: conversation history,
 * iteration progress, token usage, and lifecycle state. Runtime dependencies (ModelProvider,
 * ToolExecutor, HookChain, MiddlewarePipeline) are <strong>not</strong> included — they are
 * re-injected via {@code AgentBuilder.restoreFrom()} during restoration.
 *
 * <p>Middleware state is intentionally excluded: middleware is a stateless cross-cutting concern
 * that is re-executed fresh on every {@link Agent#call(Msg)} invocation.
 *
 * @param agentId unique agent identifier
 * @param agentName human-readable agent name
 * @param state agent lifecycle state at snapshot time
 * @param iteration current ReAct loop iteration count
 * @param totalTokensUsed cumulative token usage
 * @param conversationHistory full conversation history
 * @param contextState extensible metadata (model name, config flags, etc.)
 * @param createdAt when this snapshot was taken
 */
@Stable(value = "Agent snapshot persistence record; shape frozen since v0.5", since = "1.0.0")
public record AgentSnapshot(
        String agentId,
        String agentName,
        AgentState state,
        int iteration,
        long totalTokensUsed,
        List<Msg> conversationHistory,
        Map<String, Object> contextState,
        Instant createdAt) {

    /** Compact constructor with null-safe defaults. */
    public AgentSnapshot {
        if (conversationHistory == null) {
            conversationHistory = List.of();
        }
        if (contextState == null) {
            contextState = Map.of();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
