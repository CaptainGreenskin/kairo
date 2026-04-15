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
package io.kairo.core.tool;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultToolRegistryTest {

    private DefaultToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DefaultToolRegistry();
    }

    private ToolDefinition tool(String name, ToolCategory category) {
        return new ToolDefinition(
                name,
                "Description of " + name,
                category,
                new JsonSchema("object", null, null, null),
                Object.class);
    }

    @Test
    void registerAndLookup() {
        ToolDefinition def = tool("bash", ToolCategory.EXECUTION);
        registry.register(def);

        Optional<ToolDefinition> found = registry.get("bash");
        assertTrue(found.isPresent());
        assertEquals("bash", found.get().name());
        assertEquals(ToolCategory.EXECUTION, found.get().category());
    }

    @Test
    void lookupNonExistentReturnsEmpty() {
        assertTrue(registry.get("nonexistent").isEmpty());
    }

    @Test
    void getAllReturnsAllRegistered() {
        registry.register(tool("a", ToolCategory.GENERAL));
        registry.register(tool("b", ToolCategory.EXECUTION));
        registry.register(tool("c", ToolCategory.FILE_AND_CODE));

        List<ToolDefinition> all = registry.getAll();
        assertEquals(3, all.size());
    }

    @Test
    void getByCategoryFilters() {
        registry.register(tool("bash", ToolCategory.EXECUTION));
        registry.register(tool("read_file", ToolCategory.FILE_AND_CODE));
        registry.register(tool("write_file", ToolCategory.FILE_AND_CODE));

        List<ToolDefinition> fileTools = registry.getByCategory(ToolCategory.FILE_AND_CODE);
        assertEquals(2, fileTools.size());
        assertTrue(fileTools.stream().allMatch(t -> t.category() == ToolCategory.FILE_AND_CODE));

        List<ToolDefinition> execTools = registry.getByCategory(ToolCategory.EXECUTION);
        assertEquals(1, execTools.size());
    }

    @Test
    void duplicateRegistrationOverwrites() {
        ToolDefinition v1 =
                new ToolDefinition(
                        "bash",
                        "version 1",
                        ToolCategory.EXECUTION,
                        new JsonSchema("object", null, null, null),
                        Object.class);
        ToolDefinition v2 =
                new ToolDefinition(
                        "bash",
                        "version 2",
                        ToolCategory.EXECUTION,
                        new JsonSchema("object", null, null, null),
                        Object.class);
        registry.register(v1);
        registry.register(v2);

        assertEquals("version 2", registry.get("bash").get().description());
        assertEquals(1, registry.getAll().size());
    }

    @Test
    void unregisterRemovesTool() {
        registry.register(tool("bash", ToolCategory.EXECUTION));
        assertTrue(registry.get("bash").isPresent());

        registry.unregister("bash");
        assertTrue(registry.get("bash").isEmpty());
    }

    @Test
    void unregisterNonExistentIsNoOp() {
        assertDoesNotThrow(() -> registry.unregister("nonexistent"));
    }

    @Test
    void registerAndRetrieveToolInstance() {
        ToolDefinition def = tool("bash", ToolCategory.EXECUTION);
        registry.register(def);

        Object instance = new Object();
        registry.registerInstance("bash", instance);

        assertSame(instance, registry.getToolInstance("bash"));
    }

    @Test
    void getToolInstanceReturnsNullWhenNotRegistered() {
        assertNull(registry.getToolInstance("nonexistent"));
    }

    @Test
    void unregisterAlsoRemovesInstance() {
        registry.register(tool("bash", ToolCategory.EXECUTION));
        registry.registerInstance("bash", new Object());

        registry.unregister("bash");
        assertNull(registry.getToolInstance("bash"));
    }
}
