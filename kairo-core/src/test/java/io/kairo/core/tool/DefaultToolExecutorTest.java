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
import io.kairo.api.tool.PermissionGuard;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolResult;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DefaultToolExecutorTest {

    private DefaultToolRegistry registry;
    private DefaultPermissionGuard guard;
    private DefaultToolExecutor executor;

    @BeforeEach
    void setUp() {
        registry = new DefaultToolRegistry();
        guard = new DefaultPermissionGuard();
        executor = new DefaultToolExecutor(registry, guard);
    }

    private void registerToolHandler(String name, ToolHandler handler) {
        ToolDefinition def =
                new ToolDefinition(
                        name,
                        "test tool",
                        ToolCategory.GENERAL,
                        new JsonSchema("object", null, null, null),
                        handler.getClass());
        registry.register(def);
        registry.registerInstance(name, handler);
    }

    @Test
    void executeSuccessfully() {
        registerToolHandler(
                "echo",
                input -> new ToolResult("echo", "echoed: " + input.get("text"), false, Map.of()));

        StepVerifier.create(executor.execute("echo", Map.of("text", "hello")))
                .assertNext(
                        result -> {
                            assertFalse(result.isError());
                            assertEquals("echoed: hello", result.content());
                        })
                .verifyComplete();
    }

    @Test
    void executeUnknownToolReturnsError() {
        StepVerifier.create(executor.execute("nonexistent", Map.of()))
                .assertNext(
                        result -> {
                            assertTrue(result.isError());
                            assertTrue(result.content().contains("Unknown tool"));
                        })
                .verifyComplete();
    }

    @Test
    void executeToolWithNoHandlerReturnsError() {
        // Register definition but no handler instance
        ToolDefinition def =
                new ToolDefinition(
                        "orphan",
                        "no handler",
                        ToolCategory.GENERAL,
                        new JsonSchema("object", null, null, null),
                        Object.class);
        registry.register(def);

        StepVerifier.create(executor.execute("orphan", Map.of()))
                .assertNext(
                        result -> {
                            assertTrue(result.isError());
                            assertTrue(result.content().contains("no executable handler"));
                        })
                .verifyComplete();
    }

    @Test
    void executeToolThatThrowsReturnsError() {
        registerToolHandler(
                "failing",
                input -> {
                    throw new RuntimeException("boom");
                });

        StepVerifier.create(executor.execute("failing", Map.of()))
                .assertNext(
                        result -> {
                            assertTrue(result.isError());
                            assertTrue(result.content().contains("boom"));
                        })
                .verifyComplete();
    }

    @Test
    void permissionDeniedForDangerousCommand() {
        registerToolHandler("bash", input -> new ToolResult("bash", "executed", false, Map.of()));

        // rm -rf should be blocked by DefaultPermissionGuard
        StepVerifier.create(executor.execute("bash", Map.of("command", "rm -rf /")))
                .assertNext(
                        result -> {
                            assertTrue(result.isError());
                            assertTrue(result.content().contains("Permission denied"));
                        })
                .verifyComplete();
    }

    @Test
    void nonBashToolsSkipPermissionCheck() {
        registerToolHandler(
                "read_file", input -> new ToolResult("read_file", "file content", false, Map.of()));

        // Even with dangerous-looking input, non-bash tools pass
        StepVerifier.create(executor.execute("read_file", Map.of("command", "rm -rf /")))
                .assertNext(result -> assertFalse(result.isError()))
                .verifyComplete();
    }

    @Test
    void executeParallelMultipleTools() {
        registerToolHandler("tool_a", input -> new ToolResult("a", "result_a", false, Map.of()));
        registerToolHandler("tool_b", input -> new ToolResult("b", "result_b", false, Map.of()));

        var invocations =
                java.util.List.of(
                        new io.kairo.api.tool.ToolInvocation("tool_a", Map.of()),
                        new io.kairo.api.tool.ToolInvocation("tool_b", Map.of()));

        StepVerifier.create(executor.executeParallel(invocations).collectList())
                .assertNext(
                        results -> {
                            assertEquals(2, results.size());
                            assertTrue(
                                    results.stream().anyMatch(r -> r.content().equals("result_a")));
                            assertTrue(
                                    results.stream().anyMatch(r -> r.content().equals("result_b")));
                        })
                .verifyComplete();
    }

    @Test
    void executeWithCustomPermissionGuard() {
        // Use a guard that denies everything
        PermissionGuard denyAll =
                new PermissionGuard() {
                    @Override
                    public Mono<Boolean> checkPermission(
                            String toolName, Map<String, Object> input) {
                        return Mono.just(false);
                    }

                    @Override
                    public void addDangerousPattern(String pattern) {}
                };

        DefaultToolExecutor strictExecutor = new DefaultToolExecutor(registry, denyAll);
        registerToolHandler("bash", input -> new ToolResult("bash", "executed", false, Map.of()));

        StepVerifier.create(strictExecutor.execute("bash", Map.of("command", "echo hi")))
                .assertNext(
                        result -> {
                            assertTrue(result.isError());
                            assertTrue(result.content().contains("Permission denied"));
                        })
                .verifyComplete();
    }

    // ==================== PER-TOOL TIMEOUT ====================

    @Test
    void executeUsesPerToolTimeoutFromDefinition() {
        // Register a tool with a 1-second timeout that takes 5 seconds
        ToolDefinition def =
                new ToolDefinition(
                        "slow_tool",
                        "a slow tool",
                        ToolCategory.GENERAL,
                        new JsonSchema("object", null, null, null),
                        SlowHandler.class,
                        Duration.ofSeconds(1));
        registry.register(def);
        registry.registerInstance(
                "slow_tool",
                (ToolHandler)
                        input -> {
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return new ToolResult("slow_tool", "done", false, Map.of());
                        });

        StepVerifier.create(executor.execute("slow_tool", Map.of()))
                .assertNext(
                        result -> {
                            assertTrue(result.isError());
                            assertTrue(result.content().contains("timed out"));
                        })
                .verifyComplete();
    }

    @Test
    void executeUsesDefaultTimeoutWhenToolHasNoCustomTimeout() {
        // Register a tool without custom timeout — should use DEFAULT_TIMEOUT (120s)
        registerToolHandler(
                "fast_tool", input -> new ToolResult("fast_tool", "quick result", false, Map.of()));

        StepVerifier.create(executor.execute("fast_tool", Map.of()))
                .assertNext(
                        result -> {
                            assertFalse(result.isError());
                            assertEquals("quick result", result.content());
                        })
                .verifyComplete();
    }

    // Dummy class for the per-tool timeout test
    static class SlowHandler implements ToolHandler {
        @Override
        public ToolResult execute(Map<String, Object> input) {
            return new ToolResult("slow", "done", false, Map.of());
        }
    }
}
