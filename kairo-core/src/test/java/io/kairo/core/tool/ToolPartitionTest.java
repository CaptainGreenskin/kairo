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

import io.kairo.api.tool.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ToolPartitionTest {

    private DefaultToolRegistry registry;
    private DefaultToolExecutor executor;

    /** A PermissionGuard that allows everything. */
    private static final PermissionGuard ALLOW_ALL =
            new PermissionGuard() {
                @Override
                public Mono<Boolean> checkPermission(String toolName, Map<String, Object> input) {
                    return Mono.just(true);
                }

                @Override
                public void addDangerousPattern(String pattern) {}
            };

    @BeforeEach
    void setUp() {
        registry = new DefaultToolRegistry();
        executor = new DefaultToolExecutor(registry, ALLOW_ALL);
    }

    private void registerTool(String name, ToolSideEffect sideEffect, ToolHandler handler) {
        ToolDefinition def =
                new ToolDefinition(
                        name,
                        "test tool",
                        ToolCategory.GENERAL,
                        new JsonSchema("object", null, null, null),
                        handler.getClass(),
                        null,
                        sideEffect);
        registry.register(def);
        registry.registerInstance(name, handler);
    }

    private ToolHandler echoHandler(String toolId) {
        return input -> new ToolResult(toolId, "result-" + toolId, false, Map.of());
    }

    @Test
    void allReadsExecuteInParallel() {
        registerTool("read_file", ToolSideEffect.READ_ONLY, echoHandler("read_file"));
        registerTool("grep", ToolSideEffect.READ_ONLY, echoHandler("grep"));
        registerTool("glob", ToolSideEffect.READ_ONLY, echoHandler("glob"));

        var invocations =
                List.of(
                        new ToolInvocation("read_file", Map.of()),
                        new ToolInvocation("grep", Map.of()),
                        new ToolInvocation("glob", Map.of()));

        StepVerifier.create(executor.executePartitioned(invocations).collectList())
                .assertNext(
                        results -> {
                            assertEquals(3, results.size());
                            assertFalse(results.stream().anyMatch(ToolResult::isError));
                        })
                .verifyComplete();
    }

    @Test
    void allWritesExecuteSerially() {
        var executionOrder = new CopyOnWriteArrayList<String>();
        registerTool(
                "write_file",
                ToolSideEffect.WRITE,
                input -> {
                    executionOrder.add("write_file");
                    return new ToolResult("write_file", "ok", false, Map.of());
                });
        registerTool(
                "edit_file",
                ToolSideEffect.WRITE,
                input -> {
                    executionOrder.add("edit_file");
                    return new ToolResult("edit_file", "ok", false, Map.of());
                });

        var invocations =
                List.of(
                        new ToolInvocation("write_file", Map.of()),
                        new ToolInvocation("edit_file", Map.of()));

        StepVerifier.create(executor.executePartitioned(invocations).collectList())
                .assertNext(
                        results -> {
                            assertEquals(2, results.size());
                            assertEquals(List.of("write_file", "edit_file"), executionOrder);
                        })
                .verifyComplete();
    }

    @Test
    void mixedBatch_readsParallelThenWritesSerial() {
        var executionOrder = new CopyOnWriteArrayList<String>();
        registerTool(
                "read_file",
                ToolSideEffect.READ_ONLY,
                input -> {
                    executionOrder.add("read");
                    return new ToolResult("read_file", "data", false, Map.of());
                });
        registerTool(
                "write_file",
                ToolSideEffect.WRITE,
                input -> {
                    executionOrder.add("write");
                    return new ToolResult("write_file", "ok", false, Map.of());
                });

        var invocations =
                List.of(
                        new ToolInvocation("read_file", Map.of()),
                        new ToolInvocation("write_file", Map.of()));

        StepVerifier.create(executor.executePartitioned(invocations).collectList())
                .assertNext(
                        results -> {
                            assertEquals(2, results.size());
                            // Read should finish before write starts
                            int readIdx = executionOrder.indexOf("read");
                            int writeIdx = executionOrder.indexOf("write");
                            assertTrue(readIdx < writeIdx, "Reads should execute before writes");
                        })
                .verifyComplete();
    }

    @Test
    void resultsReturnedInOriginalCallOrder() {
        registerTool("read_file", ToolSideEffect.READ_ONLY, echoHandler("read_file"));
        registerTool("write_file", ToolSideEffect.WRITE, echoHandler("write_file"));
        registerTool("grep", ToolSideEffect.READ_ONLY, echoHandler("grep"));

        var invocations =
                List.of(
                        new ToolInvocation("read_file", Map.of()),
                        new ToolInvocation("write_file", Map.of()),
                        new ToolInvocation("grep", Map.of()));

        StepVerifier.create(executor.executePartitioned(invocations).collectList())
                .assertNext(
                        results -> {
                            assertEquals(3, results.size());
                            assertEquals("result-read_file", results.get(0).content());
                            assertEquals("result-write_file", results.get(1).content());
                            assertEquals("result-grep", results.get(2).content());
                        })
                .verifyComplete();
    }

    @Test
    void unknownToolDefaultsToSystemChange() {
        ToolSideEffect effect = executor.resolveSideEffect("nonexistent_tool");
        assertEquals(ToolSideEffect.SYSTEM_CHANGE, effect);
    }

    @Test
    void emptyCallListReturnsEmpty() {
        StepVerifier.create(executor.executePartitioned(List.of()).collectList())
                .assertNext(results -> assertTrue(results.isEmpty()))
                .verifyComplete();
    }

    @Test
    void singleReadToolExecutesSuccessfully() {
        registerTool("read_file", ToolSideEffect.READ_ONLY, echoHandler("read_file"));

        var invocations = List.of(new ToolInvocation("read_file", Map.of()));

        StepVerifier.create(executor.executePartitioned(invocations).collectList())
                .assertNext(
                        results -> {
                            assertEquals(1, results.size());
                            assertFalse(results.get(0).isError());
                            assertEquals("result-read_file", results.get(0).content());
                        })
                .verifyComplete();
    }

    @Test
    void singleWriteToolExecutesSuccessfully() {
        registerTool("write_file", ToolSideEffect.WRITE, echoHandler("write_file"));

        var invocations = List.of(new ToolInvocation("write_file", Map.of()));

        StepVerifier.create(executor.executePartitioned(invocations).collectList())
                .assertNext(
                        results -> {
                            assertEquals(1, results.size());
                            assertFalse(results.get(0).isError());
                            assertEquals("result-write_file", results.get(0).content());
                        })
                .verifyComplete();
    }

    @Test
    void parallelReadsAreFasterThanSerial() {
        int sleepMs = 100;
        int numReads = 3;
        for (int i = 0; i < numReads; i++) {
            String name = "read_" + i;
            registerTool(
                    name,
                    ToolSideEffect.READ_ONLY,
                    input -> {
                        Thread.sleep(sleepMs);
                        return new ToolResult(name, "ok", false, Map.of());
                    });
        }

        var invocations = new ArrayList<ToolInvocation>();
        for (int i = 0; i < numReads; i++) {
            invocations.add(new ToolInvocation("read_" + i, Map.of()));
        }

        long start = System.currentTimeMillis();
        List<ToolResult> results = executor.executePartitioned(invocations).collectList().block();
        long elapsed = System.currentTimeMillis() - start;

        assertNotNull(results);
        assertEquals(numReads, results.size());
        // If parallel, should take ~100ms, not 300ms. Allow generous headroom.
        assertTrue(
                elapsed < sleepMs * numReads,
                "Parallel reads took " + elapsed + "ms, expected < " + (sleepMs * numReads) + "ms");
    }

    @Test
    void serialWritesPreserveOrder() {
        var order = new CopyOnWriteArrayList<String>();
        for (int i = 0; i < 5; i++) {
            String name = "write_" + i;
            registerTool(
                    name,
                    ToolSideEffect.WRITE,
                    input -> {
                        order.add(name);
                        return new ToolResult(name, "ok", false, Map.of());
                    });
        }

        var invocations = new ArrayList<ToolInvocation>();
        for (int i = 0; i < 5; i++) {
            invocations.add(new ToolInvocation("write_" + i, Map.of()));
        }

        executor.executePartitioned(invocations).collectList().block();

        assertEquals(List.of("write_0", "write_1", "write_2", "write_3", "write_4"), order);
    }

    @Test
    void systemChangeToolTreatedAsWrite() {
        var executionOrder = new CopyOnWriteArrayList<String>();
        registerTool(
                "bash",
                ToolSideEffect.SYSTEM_CHANGE,
                input -> {
                    executionOrder.add("bash");
                    return new ToolResult("bash", "ok", false, Map.of());
                });
        registerTool(
                "read_file",
                ToolSideEffect.READ_ONLY,
                input -> {
                    executionOrder.add("read");
                    return new ToolResult("read_file", "data", false, Map.of());
                });

        // SYSTEM_CHANGE defaults to ASK, so set to ALLOWED for this test
        executor.setToolPermission("bash", ToolPermission.ALLOWED);

        var invocations =
                List.of(
                        new ToolInvocation("read_file", Map.of()),
                        new ToolInvocation("bash", Map.of()));

        StepVerifier.create(executor.executePartitioned(invocations).collectList())
                .assertNext(
                        results -> {
                            assertEquals(2, results.size());
                            int readIdx = executionOrder.indexOf("read");
                            int bashIdx = executionOrder.indexOf("bash");
                            assertTrue(
                                    readIdx < bashIdx,
                                    "Read should complete before system_change tool");
                        })
                .verifyComplete();
    }

    @Test
    void readToolSideEffectResolvedCorrectly() {
        registerTool("read_file", ToolSideEffect.READ_ONLY, echoHandler("read_file"));
        assertEquals(ToolSideEffect.READ_ONLY, executor.resolveSideEffect("read_file"));
    }

    @Test
    void writeToolSideEffectResolvedCorrectly() {
        registerTool("write_file", ToolSideEffect.WRITE, echoHandler("write_file"));
        assertEquals(ToolSideEffect.WRITE, executor.resolveSideEffect("write_file"));
    }

    @Test
    void failedToolDoesNotBlockOthers() {
        registerTool("good_read", ToolSideEffect.READ_ONLY, echoHandler("good_read"));
        registerTool(
                "bad_read",
                ToolSideEffect.READ_ONLY,
                input -> {
                    throw new RuntimeException("tool failure");
                });

        var invocations =
                List.of(
                        new ToolInvocation("good_read", Map.of()),
                        new ToolInvocation("bad_read", Map.of()));

        StepVerifier.create(executor.executePartitioned(invocations).collectList())
                .assertNext(
                        results -> {
                            assertEquals(2, results.size());
                            assertFalse(results.get(0).isError());
                            assertTrue(results.get(1).isError());
                        })
                .verifyComplete();
    }
}
