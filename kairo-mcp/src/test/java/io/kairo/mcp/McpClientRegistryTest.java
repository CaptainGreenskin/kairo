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
import io.kairo.api.tool.ToolSideEffect;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class McpClientRegistryTest {

    private McpClientRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new McpClientRegistry();
    }

    @AfterEach
    void tearDown() {
        registry.close();
    }

    @Test
    void getToolGroupReturnsNullForUnknown() {
        assertNull(registry.getToolGroup("nonexistent"));
    }

    @Test
    void getExecutorReturnsNullForUnknown() {
        assertNull(registry.getExecutor("nonexistent_tool"));
    }

    @Test
    void serverNamesEmptyInitially() {
        assertTrue(registry.getServerNames().isEmpty());
    }

    @Test
    void closeOnEmptyRegistryDoesNotThrow() {
        assertDoesNotThrow(() -> registry.close());
    }

    @Test
    void getAllToolDefinitionsEmptyInitially() {
        var defs = registry.getAllToolDefinitions().collectList().block();
        assertNotNull(defs);
        assertTrue(defs.isEmpty());
    }

    @Test
    void toolGroupTracksToolsCorrectly() {
        McpToolGroup group = new McpToolGroup("testServer");
        assertEquals("testServer", group.getServerName());
        assertEquals(0, group.size());
        assertTrue(group.getRegisteredToolNames().isEmpty());

        ToolDefinition def =
                new ToolDefinition(
                        "testServer_tool1",
                        "desc",
                        ToolCategory.GENERAL,
                        new JsonSchema(
                                "object", Collections.emptyMap(), Collections.emptyList(), null),
                        McpToolExecutor.class,
                        Duration.ofSeconds(30),
                        ToolSideEffect.SYSTEM_CHANGE);
        McpToolExecutor executor = new McpToolExecutor(null, "tool1", "testServer_tool1", null);
        group.addTool(def, executor);

        assertEquals(1, group.size());
        assertEquals("testServer_tool1", group.getRegisteredToolNames().get(0));
        assertNotNull(group.getExecutor("testServer_tool1"));
        assertNotNull(group.getToolDefinition("testServer_tool1"));
        assertNull(group.getExecutor("unknown"));
    }

    @Test
    void toolGroupReturnsAllDefinitions() {
        McpToolGroup group = new McpToolGroup("srv");
        JsonSchema schema =
                new JsonSchema("object", Collections.emptyMap(), Collections.emptyList(), null);

        for (int i = 0; i < 3; i++) {
            String name = "srv_tool" + i;
            ToolDefinition def =
                    new ToolDefinition(
                            name,
                            "desc" + i,
                            ToolCategory.GENERAL,
                            schema,
                            McpToolExecutor.class,
                            Duration.ofSeconds(30),
                            ToolSideEffect.SYSTEM_CHANGE);
            group.addTool(def, new McpToolExecutor(null, "tool" + i, name, null));
        }

        assertEquals(3, group.size());
        assertEquals(3, group.getAllToolDefinitions().size());
    }

    @Test
    void concurrentToolGroupAccess() throws InterruptedException {
        McpToolGroup group = new McpToolGroup("concurrent");
        int threads = 10;
        CountDownLatch latch = new CountDownLatch(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        JsonSchema schema =
                new JsonSchema("object", Collections.emptyMap(), Collections.emptyList(), null);
        for (int i = 0; i < threads; i++) {
            final int idx = i;
            executor.submit(
                    () -> {
                        try {
                            String name = "concurrent_tool" + idx;
                            ToolDefinition def =
                                    new ToolDefinition(
                                            name,
                                            "desc",
                                            ToolCategory.GENERAL,
                                            schema,
                                            McpToolExecutor.class,
                                            Duration.ofSeconds(30),
                                            ToolSideEffect.SYSTEM_CHANGE);
                            group.addTool(def, new McpToolExecutor(null, "tool" + idx, name, null));
                        } finally {
                            latch.countDown();
                        }
                    });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        // ConcurrentHashMap-backed maps are thread-safe; verify all executors stored
        assertEquals(threads, group.getAllToolDefinitions().size());
    }
}
