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
import org.junit.jupiter.api.Test;

class McpToolGroupTest {

    private static McpToolExecutor executor() {
        return new McpToolExecutor(null, "mcp-tool", "kairo-tool", null);
    }

    private static ToolDefinition def(String name) {
        return new ToolDefinition(
                name,
                "desc",
                ToolCategory.GENERAL,
                new JsonSchema("object", Map.of(), List.of(), null),
                Object.class);
    }

    @Test
    void constructorDoesNotThrow() {
        assertDoesNotThrow(() -> new McpToolGroup("my-server"));
    }

    @Test
    void serverNameIsPreserved() {
        McpToolGroup group = new McpToolGroup("my-server");
        assertEquals("my-server", group.getServerName());
    }

    @Test
    void constructorRejectsNullServerName() {
        assertThrows(IllegalArgumentException.class, () -> new McpToolGroup(null));
    }

    @Test
    void constructorRejectsBlankServerName() {
        assertThrows(IllegalArgumentException.class, () -> new McpToolGroup("  "));
    }

    @Test
    void initiallyEmpty() {
        McpToolGroup group = new McpToolGroup("s");
        assertEquals(0, group.size());
        assertTrue(group.getRegisteredToolNames().isEmpty());
    }

    @Test
    void addToolIncrementsSize() {
        McpToolGroup group = new McpToolGroup("s");
        McpToolExecutor executor = executor();
        group.addTool(def("tool-a"), executor);
        assertEquals(1, group.size());
    }

    @Test
    void addedToolNameIsRegistered() {
        McpToolGroup group = new McpToolGroup("s");
        McpToolExecutor executor = executor();
        group.addTool(def("tool-a"), executor);
        assertTrue(group.getRegisteredToolNames().contains("tool-a"));
    }

    @Test
    void getExecutorReturnsAddedExecutor() {
        McpToolGroup group = new McpToolGroup("s");
        McpToolExecutor executor = executor();
        group.addTool(def("tool-a"), executor);
        assertSame(executor, group.getExecutor("tool-a"));
    }

    @Test
    void getToolDefinitionReturnsAddedDefinition() {
        McpToolGroup group = new McpToolGroup("s");
        ToolDefinition d = def("tool-a");
        group.addTool(d, executor());
        assertEquals(d, group.getToolDefinition("tool-a"));
    }

    @Test
    void getAllToolDefinitionsContainsAdded() {
        McpToolGroup group = new McpToolGroup("s");
        ToolDefinition d = def("tool-a");
        group.addTool(d, executor());
        assertTrue(group.getAllToolDefinitions().contains(d));
    }

    @Test
    void registeredToolNamesIsUnmodifiable() {
        McpToolGroup group = new McpToolGroup("s");
        assertThrows(
                UnsupportedOperationException.class,
                () -> group.getRegisteredToolNames().add("injected"));
    }

    @Test
    void multipleTool() {
        McpToolGroup group = new McpToolGroup("s");
        group.addTool(def("tool-a"), executor());
        group.addTool(def("tool-b"), executor());
        assertEquals(2, group.size());
        assertEquals(2, group.getRegisteredToolNames().size());
    }

    @Test
    void getExecutorReturnsNullForUnknown() {
        McpToolGroup group = new McpToolGroup("s");
        assertNull(group.getExecutor("unknown"));
    }
}
