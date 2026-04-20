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

/**
 * Runtime context provided to tool executions.
 *
 * <p>Contains agent and session identifiers plus user-injected runtime dependencies.
 *
 * @param agentId the ID of the agent invoking the tool
 * @param sessionId the current session ID
 * @param dependencies user-injected runtime dependencies (e.g., database connections, API clients)
 */
public record ToolContext(String agentId, String sessionId, Map<String, Object> dependencies) {

    /** Compact constructor — defensively copies dependencies. */
    public ToolContext {
        dependencies = dependencies == null ? Map.of() : Map.copyOf(dependencies);
    }
}
