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
import io.modelcontextprotocol.client.McpAsyncClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class McpToolGroupTest {

    private static McpAsyncClient buildTestClient() {
        return McpClientBuilder.create("test").stdioTransport("echo", "hello").build();
    }

    private static ToolDefinition toolDef(String name) {
        return new ToolDefinition(
                name,
                "test tool",
                ToolCategory.GENERAL,
                new JsonSchema("object", Map.of(), List.of(), null),
                McpToolGroupTest.class);
    }

    private static McpToolExecutor executor(McpAsyncClient client, String toolName) {
        return new McpToolExecutor(client, toolName, toolName, Map.of());
    }

    @Test
    void constructorSetsServerName() {
        McpToolGroup group = new McpToolGroup("my-server");
        assertEquals("my-server", group.getServerName());
    }

    @Test
    void nullServerNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> new McpToolGroup(null));
    }

    @Test
    void blankServerNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> new McpToolGroup("   "));
    }

    @Test
    void emptyGroupHasSizeZero() {
        McpToolGroup group = new McpToolGroup("srv");
        assertEquals(0, group.size());
        assertTrue(group.getRegisteredToolNames().isEmpty());
        assertTrue(group.getAllToolDefinitions().isEmpty());
    }

    @Test
    void addToolIncrementsSize() {
        McpAsyncClient client = buildTestClient();
        McpToolGroup group = new McpToolGroup("srv");

        group.addTool(toolDef("srv_read"), executor(client, "read"));

        assertEquals(1, group.size());
    }

    @Test
    void addToolRegistersToolName() {
        McpAsyncClient client = buildTestClient();
        McpToolGroup group = new McpToolGroup("srv");

        group.addTool(toolDef("srv_read"), executor(client, "read"));

        assertTrue(group.getRegisteredToolNames().contains("srv_read"));
    }

    @Test
    void getExecutorReturnsRegisteredExecutor() {
        McpAsyncClient client = buildTestClient();
        McpToolGroup group = new McpToolGroup("srv");
        McpToolExecutor exec = executor(client, "read");

        group.addTool(toolDef("srv_read"), exec);

        assertSame(exec, group.getExecutor("srv_read"));
    }

    @Test
    void getToolDefinitionReturnsRegisteredDefinition() {
        McpAsyncClient client = buildTestClient();
        McpToolGroup group = new McpToolGroup("srv");
        ToolDefinition def = toolDef("srv_write");

        group.addTool(def, executor(client, "write"));

        assertEquals(def, group.getToolDefinition("srv_write"));
    }

    @Test
    void getAllToolDefinitionsContainsAllAdded() {
        McpAsyncClient client = buildTestClient();
        McpToolGroup group = new McpToolGroup("srv");

        group.addTool(toolDef("srv_read"), executor(client, "read"));
        group.addTool(toolDef("srv_write"), executor(client, "write"));
        group.addTool(toolDef("srv_delete"), executor(client, "delete"));

        List<ToolDefinition> all = group.getAllToolDefinitions();
        assertEquals(3, all.size());
    }

    @Test
    void registeredToolNamesIsUnmodifiable() {
        McpAsyncClient client = buildTestClient();
        McpToolGroup group = new McpToolGroup("srv");
        group.addTool(toolDef("srv_read"), executor(client, "read"));

        assertThrows(
                UnsupportedOperationException.class,
                () -> group.getRegisteredToolNames().add("sneaked_in"));
    }

    @Test
    void missingToolReturnsNull() {
        McpToolGroup group = new McpToolGroup("srv");
        assertNull(group.getExecutor("non_existent"));
        assertNull(group.getToolDefinition("non_existent"));
    }

    @Test
    void concurrentAddToolIsThreadSafe() throws Exception {
        McpAsyncClient client = buildTestClient();
        McpToolGroup group = new McpToolGroup("srv");
        int threadCount = 8;
        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        List<Exception> errors = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            pool.submit(
                    () -> {
                        try {
                            latch.await();
                            group.addTool(
                                    toolDef("srv_tool_" + idx), executor(client, "tool_" + idx));
                        } catch (Exception e) {
                            synchronized (errors) {
                                errors.add(e);
                            }
                        }
                    });
        }

        latch.countDown();
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

        assertTrue(errors.isEmpty(), "Concurrent addTool produced errors: " + errors);
        assertEquals(threadCount, group.size());
    }
}
