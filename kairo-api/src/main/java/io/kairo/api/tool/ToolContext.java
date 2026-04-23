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
package io.kairo.api.tool;

import java.util.Map;
import javax.annotation.Nullable;

/**
 * Runtime context provided to tool executions.
 *
 * <p>Contains agent and session identifiers plus user-injected runtime dependencies. During crash
 * recovery, the {@code idempotencyKey} is populated to enable tools to detect duplicate
 * invocations.
 *
 * @param agentId the ID of the agent invoking the tool
 * @param sessionId the current session ID
 * @param dependencies user-injected runtime dependencies (e.g., database connections, API clients)
 * @param idempotencyKey optional key for at-least-once idempotency during crash recovery (null in
 *     normal execution)
 */
public record ToolContext(
        String agentId,
        String sessionId,
        Map<String, Object> dependencies,
        @Nullable String idempotencyKey) {

    /** Compact constructor — defensively copies dependencies. */
    public ToolContext {
        dependencies = dependencies == null ? Map.of() : Map.copyOf(dependencies);
    }

    /**
     * Backward-compatible constructor without idempotency key.
     *
     * @param agentId the ID of the agent invoking the tool
     * @param sessionId the current session ID
     * @param dependencies user-injected runtime dependencies
     */
    public ToolContext(String agentId, String sessionId, Map<String, Object> dependencies) {
        this(agentId, sessionId, dependencies, null);
    }
}
