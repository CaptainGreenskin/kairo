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
package io.kairo.mcp;

import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolSideEffect;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Converts MCP tool schemas to Kairo {@link ToolDefinition} instances. */
public final class McpToolAdapter {

    private McpToolAdapter() {}

    /**
     * Converts an MCP tool to a Kairo {@link ToolDefinition}.
     *
     * @param mcpTool the MCP tool schema
     * @param serverName the MCP server name (used as tool name prefix)
     * @param timeout per-tool timeout
     * @return a Kairo tool definition
     */
    public static ToolDefinition toToolDefinition(
            McpSchema.Tool mcpTool, String serverName, Duration timeout) {
        String name = serverName + "_" + mcpTool.name();
        String description = mcpTool.description() != null ? mcpTool.description() : "";
        JsonSchema inputSchema = convertSchema(mcpTool.inputSchema(), null);
        return new ToolDefinition(
                name,
                description,
                ToolCategory.GENERAL,
                inputSchema,
                McpToolExecutor.class,
                timeout,
                ToolSideEffect.SYSTEM_CHANGE);
    }

    /**
     * Converts an MCP tool to a Kairo {@link ToolDefinition}, excluding preset parameter keys from
     * the schema.
     *
     * @param mcpTool the MCP tool schema
     * @param serverName the MCP server name
     * @param timeout per-tool timeout
     * @param excludeParams parameter names to exclude from the schema (preset args)
     * @return a Kairo tool definition
     */
    public static ToolDefinition toToolDefinition(
            McpSchema.Tool mcpTool,
            String serverName,
            Duration timeout,
            Set<String> excludeParams) {
        String name = serverName + "_" + mcpTool.name();
        String description = mcpTool.description() != null ? mcpTool.description() : "";
        JsonSchema inputSchema = convertSchema(mcpTool.inputSchema(), excludeParams);
        return new ToolDefinition(
                name,
                description,
                ToolCategory.GENERAL,
                inputSchema,
                McpToolExecutor.class,
                timeout,
                ToolSideEffect.SYSTEM_CHANGE);
    }

    /** Converts an MCP {@link McpSchema.JsonSchema} to a Kairo {@link JsonSchema}. */
    @SuppressWarnings("unchecked")
    static JsonSchema convertSchema(McpSchema.JsonSchema mcpSchema, Set<String> excludeParams) {
        if (mcpSchema == null) {
            return new JsonSchema("object", Collections.emptyMap(), Collections.emptyList(), null);
        }

        String type = mcpSchema.type() != null ? mcpSchema.type() : "object";
        List<String> required =
                mcpSchema.required() != null
                        ? new ArrayList<>(mcpSchema.required())
                        : new ArrayList<>();

        Map<String, JsonSchema> properties = new HashMap<>();
        if (mcpSchema.properties() != null) {
            for (Map.Entry<String, Object> entry : mcpSchema.properties().entrySet()) {
                String propName = entry.getKey();
                if (excludeParams != null && excludeParams.contains(propName)) {
                    continue;
                }
                Object propValue = entry.getValue();
                properties.put(propName, convertPropertyValue(propValue));
            }
        }

        // Remove excluded params from required list
        if (excludeParams != null) {
            required.removeAll(excludeParams);
        }

        return new JsonSchema(type, properties, required, null);
    }

    @SuppressWarnings("unchecked")
    private static JsonSchema convertPropertyValue(Object value) {
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            String propType = map.containsKey("type") ? String.valueOf(map.get("type")) : null;
            String propDesc =
                    map.containsKey("description") ? String.valueOf(map.get("description")) : null;

            // Recursively convert nested properties (for object types)
            Map<String, JsonSchema> nestedProps = Collections.emptyMap();
            List<String> nestedRequired = Collections.emptyList();
            if (map.containsKey("properties") && map.get("properties") instanceof Map) {
                Map<String, Object> rawProps = (Map<String, Object>) map.get("properties");
                nestedProps = new HashMap<>();
                for (Map.Entry<String, Object> e : rawProps.entrySet()) {
                    nestedProps.put(e.getKey(), convertPropertyValue(e.getValue()));
                }
            }
            if (map.containsKey("required") && map.get("required") instanceof List) {
                nestedRequired = (List<String>) map.get("required");
            }

            return new JsonSchema(propType, nestedProps, nestedRequired, propDesc);
        }
        return new JsonSchema("string", Collections.emptyMap(), Collections.emptyList(), null);
    }
}
