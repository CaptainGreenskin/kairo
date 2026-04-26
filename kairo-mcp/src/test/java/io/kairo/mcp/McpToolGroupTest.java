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

import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class McpToolGroupTest {

    private McpToolGroup group;

    @BeforeEach
    void setUp() {
        group = new McpToolGroup("my-server");
    }

    @Test
    void blankServerNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> new McpToolGroup(""));
    }

    @Test
    void nullServerNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> new McpToolGroup(null));
    }

    @Test
    void getServerName() {
        assertEquals("my-server", group.getServerName());
    }

    @Test
    void emptyGroupHasZeroSize() {
        assertEquals(0, group.size());
    }

    @Test
    void addToolIncreasesSize() {
        ToolDefinition def =
                new ToolDefinition(
                        "my-tool",
                        "desc",
                        ToolCategory.GENERAL,
                        new JsonSchema("object", Map.of(), List.of(), ""),
                        null);
        McpToolExecutor executor = new McpToolExecutor(null, "mcp-tool", "my-tool", null);
        group.addTool(def, executor);

        assertEquals(1, group.size());
    }

    @Test
    void getRegisteredToolNamesContainsAddedTool() {
        ToolDefinition def =
                new ToolDefinition(
                        "alpha-tool",
                        "desc",
                        ToolCategory.GENERAL,
                        new JsonSchema("object", Map.of(), List.of(), ""),
                        null);
        McpToolExecutor executor = new McpToolExecutor(null, "alpha-tool", "alpha-tool", null);
        group.addTool(def, executor);

        assertTrue(group.getRegisteredToolNames().contains("alpha-tool"));
    }

    @Test
    void getExecutorReturnsAddedExecutor() {
        ToolDefinition def =
                new ToolDefinition(
                        "beta-tool",
                        "desc",
                        ToolCategory.GENERAL,
                        new JsonSchema("object", Map.of(), List.of(), ""),
                        null);
        McpToolExecutor executor = new McpToolExecutor(null, "beta-tool", "beta-tool", null);
        group.addTool(def, executor);

        assertSame(executor, group.getExecutor("beta-tool"));
    }

    @Test
    void getToolDefinitionReturnsAddedDefinition() {
        ToolDefinition def =
                new ToolDefinition(
                        "gamma-tool",
                        "desc",
                        ToolCategory.GENERAL,
                        new JsonSchema("object", Map.of(), List.of(), ""),
                        null);
        McpToolExecutor executor = new McpToolExecutor(null, "gamma-tool", "gamma-tool", null);
        group.addTool(def, executor);

        assertSame(def, group.getToolDefinition("gamma-tool"));
    }

    @Test
    void getExecutorForUnknownToolReturnsNull() {
        assertNull(group.getExecutor("nonexistent"));
    }

    @Test
    void getAllToolDefinitionsReturnsAllAdded() {
        ToolDefinition def1 =
                new ToolDefinition(
                        "tool-1",
                        "d1",
                        ToolCategory.GENERAL,
                        new JsonSchema("object", Map.of(), List.of(), ""),
                        null);
        ToolDefinition def2 =
                new ToolDefinition(
                        "tool-2",
                        "d2",
                        ToolCategory.GENERAL,
                        new JsonSchema("object", Map.of(), List.of(), ""),
                        null);
        group.addTool(def1, new McpToolExecutor(null, "tool-1", "tool-1", null));
        group.addTool(def2, new McpToolExecutor(null, "tool-2", "tool-2", null));

        assertEquals(2, group.getAllToolDefinitions().size());
    }

    @Test
    void registeredToolNamesIsUnmodifiable() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> group.getRegisteredToolNames().add("injected"));
    }
}
