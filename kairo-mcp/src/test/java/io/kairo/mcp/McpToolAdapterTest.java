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

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.tool.ToolDefinition;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class McpToolAdapterTest {

    private McpSchema.Tool makeTool(String name, String description, Map<String, Object> props, List<String> required) {
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema("object", props, required, null, null, null);
        return new McpSchema.Tool(name, null, description, schema, null, null, null);
    }

    @Test
    void convertsSimpleToolToDefinition() {
        Map<String, Object> props = Map.of("city", Map.of("type", "string", "description", "City name"));
        McpSchema.Tool tool = makeTool("get_weather", "Get weather info", props, List.of("city"));

        ToolDefinition def = McpToolAdapter.toToolDefinition(tool, "weather", Duration.ofSeconds(30));

        assertEquals("weather_get_weather", def.name());
        assertEquals("Get weather info", def.description());
        assertNotNull(def.inputSchema());
        assertEquals("object", def.inputSchema().type());
        assertTrue(def.inputSchema().properties().containsKey("city"));
        assertTrue(def.inputSchema().required().contains("city"));
    }

    @Test
    void prefixesToolNameWithServerName() {
        McpSchema.Tool tool = makeTool("read_file", "Read a file", Collections.emptyMap(), Collections.emptyList());
        ToolDefinition def = McpToolAdapter.toToolDefinition(tool, "filesystem", Duration.ofSeconds(10));
        assertEquals("filesystem_read_file", def.name());
    }

    @Test
    void handlesRequiredParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("path", Map.of("type", "string"));
        props.put("encoding", Map.of("type", "string"));
        McpSchema.Tool tool = makeTool("read", "Read", props, List.of("path"));

        ToolDefinition def = McpToolAdapter.toToolDefinition(tool, "fs", Duration.ofSeconds(30));
        assertEquals(List.of("path"), def.inputSchema().required());
    }

    @Test
    void handlesOptionalParameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("query", Map.of("type", "string"));
        props.put("limit", Map.of("type", "number"));
        McpSchema.Tool tool = makeTool("search", "Search", props, List.of("query"));

        ToolDefinition def = McpToolAdapter.toToolDefinition(tool, "db", Duration.ofSeconds(30));
        assertTrue(def.inputSchema().properties().containsKey("limit"));
        assertFalse(def.inputSchema().required().contains("limit"));
    }

    @Test
    void handlesNullInputSchema() {
        McpSchema.Tool tool = new McpSchema.Tool("ping", null, "Ping server", null, null, null, null);
        ToolDefinition def = McpToolAdapter.toToolDefinition(tool, "srv", Duration.ofSeconds(5));
        assertEquals("object", def.inputSchema().type());
        assertTrue(def.inputSchema().properties().isEmpty());
    }

    @Test
    void handlesNullDescription() {
        McpSchema.Tool tool = makeTool("noop", null, Collections.emptyMap(), Collections.emptyList());
        ToolDefinition def = McpToolAdapter.toToolDefinition(tool, "srv", Duration.ofSeconds(5));
        assertEquals("", def.description());
    }

    @Test
    void excludesPresetArgsFromParams() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("path", Map.of("type", "string"));
        props.put("encoding", Map.of("type", "string"));
        McpSchema.Tool tool = makeTool("read", "Read file", props, List.of("path", "encoding"));

        Set<String> exclude = Set.of("encoding");
        ToolDefinition def = McpToolAdapter.toToolDefinition(tool, "fs", Duration.ofSeconds(30), exclude);

        assertTrue(def.inputSchema().properties().containsKey("path"));
        assertFalse(def.inputSchema().properties().containsKey("encoding"));
        assertTrue(def.inputSchema().required().contains("path"));
        assertFalse(def.inputSchema().required().contains("encoding"));
    }
}
