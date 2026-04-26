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

import io.kairo.api.Experimental;
import java.util.List;
import javax.annotation.Nullable;

/**
 * MCP integration config grouped as a single capability record.
 *
 * <p>Replaces the 4 individual {@code mcp*} fields previously embedded in {@code AgentConfig}.
 * Future versions may relocate this record to the {@code kairo-mcp} module entirely; it remains in
 * {@code kairo-api} for v0.10 to keep the config record package-stable.
 *
 * @param serverConfigs MCP server configuration objects (type-erased to {@link Object} because the
 *     concrete type lives in {@code kairo-mcp})
 * @param maxToolsPerServer safety bound so one server cannot flood the agent
 * @param strictSchemaAlignment enforce strict JSON schema alignment when importing tools
 * @param toolSearchQuery optional case-insensitive query to filter exposed MCP tools
 * @since v0.10 (Experimental)
 */
@Experimental("AgentConfig capability — contract may change in v0.11")
public record McpCapabilityConfig(
        List<Object> serverConfigs,
        int maxToolsPerServer,
        boolean strictSchemaAlignment,
        @Nullable String toolSearchQuery) {

    /** Empty MCP capability (no servers) with defaults matching historical behaviour. */
    public static final McpCapabilityConfig EMPTY =
            new McpCapabilityConfig(List.of(), 128, true, null);

    public McpCapabilityConfig {
        serverConfigs = serverConfigs == null ? List.of() : List.copyOf(serverConfigs);
        if (maxToolsPerServer < 1) {
            throw new IllegalArgumentException("maxToolsPerServer must be >= 1");
        }
    }
}
