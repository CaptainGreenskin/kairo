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

import io.kairo.api.exception.AgentInterruptedException;
import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.PermissionGuard;
import io.kairo.api.tool.SyncTool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tracing.NoopSpan;
import io.kairo.api.tracing.Span;
import io.kairo.api.tracing.Tracer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
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

    private void registerToolHandler(String name, SyncTool handler) {
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
                (input, ctx) ->
                        Mono.just(ToolResult.success("echo", "echoed: " + input.get("text"))));

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
                (input, ctx) ->
                        Mono.fromCallable(
                                () -> {
                                    throw new RuntimeException("boom");
                                }));

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
        registerToolHandler(
                "bash", (input, ctx) -> Mono.just(ToolResult.success("bash", "executed")));

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
                "read_file",
                (input, ctx) -> Mono.just(ToolResult.success("read_file", "file content")));

        // Even with dangerous-looking input, non-bash tools pass
        StepVerifier.create(executor.execute("read_file", Map.of("command", "rm -rf /")))
                .assertNext(result -> assertFalse(result.isError()))
                .verifyComplete();
    }

    @Test
    void executeParallelMultipleTools() {
        registerToolHandler(
                "tool_a", (input, ctx) -> Mono.just(ToolResult.success("a", "result_a")));
        registerToolHandler(
                "tool_b", (input, ctx) -> Mono.just(ToolResult.success("b", "result_b")));

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
        registerToolHandler(
                "bash", (input, ctx) -> Mono.just(ToolResult.success("bash", "executed")));

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
        // Register a tool with a 200ms timeout whose handler sleeps 2s — timeout must fire first
        ToolDefinition def =
                new ToolDefinition(
                        "slow_tool",
                        "a slow tool",
                        ToolCategory.GENERAL,
                        new JsonSchema("object", null, null, null),
                        SlowHandler.class,
                        Duration.ofMillis(200));
        registry.register(def);
        registry.registerInstance(
                "slow_tool",
                (SyncTool)
                        (input, ctx) ->
                                Mono.fromCallable(
                                        () -> {
                                            try {
                                                Thread.sleep(2000);
                                            } catch (InterruptedException e) {
                                                Thread.currentThread().interrupt();
                                            }
                                            return ToolResult.success("slow_tool", "done");
                                        }));

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
                "fast_tool",
                (input, ctx) -> Mono.just(ToolResult.success("fast_tool", "quick result")));

        StepVerifier.create(executor.execute("fast_tool", Map.of()))
                .assertNext(
                        result -> {
                            assertFalse(result.isError());
                            assertEquals("quick result", result.content());
                        })
                .verifyComplete();
    }

    // Dummy class for the per-tool timeout test
    static class SlowHandler implements SyncTool {
        @Override
        public Mono<ToolResult> execute(Map<String, Object> input, ToolContext ctx) {
            return Mono.just(ToolResult.success("slow", "done"));
        }
    }

    // ==================== CANCELLATION PROPAGATION ====================

    @Test
    void toolThrowingAgentInterruptedExceptionPropagatesNotWrapped() {
        registerToolHandler(
                "cancelling_tool",
                (input, ctx) ->
                        Mono.fromCallable(
                                () -> {
                                    throw new AgentInterruptedException("Tool execution cancelled");
                                }));

        StepVerifier.create(executor.execute("cancelling_tool", Map.of()))
                .expectErrorMatches(
                        e ->
                                e instanceof AgentInterruptedException
                                        && e.getMessage().contains("cancelled"))
                .verify();
    }

    // ==================== TRACER OBSERVATION ====================

    @Test
    void execute_success_emitsLangfuseToolObservation() {
        RecordingTracer tracer = new RecordingTracer();
        DefaultToolExecutor tracedExecutor = new DefaultToolExecutor(registry, guard, tracer);
        registerToolHandler(
                "echo",
                (input, ctx) ->
                        Mono.just(ToolResult.success("echo", "echoed: " + input.get("text"))));

        StepVerifier.create(tracedExecutor.execute("echo", Map.of("text", "hi")))
                .assertNext(r -> assertFalse(r.isError()))
                .verifyComplete();

        Assertions.assertThat(tracer.spans).hasSize(1);
        RecordingSpan span = tracer.spans.get(0);
        Assertions.assertThat(span.ended).isTrue();
        Assertions.assertThat(span.statusSuccess).isTrue();
        Assertions.assertThat(span.attributes)
                .containsEntry("langfuse.observation.type", "tool")
                .containsEntry("langfuse.observation.level", "DEFAULT")
                .containsEntry("langfuse.observation.output", "echoed: hi")
                .containsEntry("tool.name", "echo")
                .containsEntry("tool.success", true)
                .containsKey("tool.duration_ms")
                .containsKey("tool.output.length");
    }

    @Test
    void execute_failure_emitsErrorLevelToolObservation() {
        RecordingTracer tracer = new RecordingTracer();
        DefaultToolExecutor tracedExecutor = new DefaultToolExecutor(registry, guard, tracer);
        registerToolHandler(
                "failing",
                (input, ctx) ->
                        Mono.fromCallable(
                                () -> {
                                    throw new RuntimeException("kaboom");
                                }));

        StepVerifier.create(tracedExecutor.execute("failing", Map.of()))
                .assertNext(r -> assertTrue(r.isError()))
                .verifyComplete();

        Assertions.assertThat(tracer.spans).hasSize(1);
        RecordingSpan span = tracer.spans.get(0);
        Assertions.assertThat(span.ended).isTrue();
        Assertions.assertThat(span.attributes)
                .containsEntry("langfuse.observation.type", "tool")
                .containsEntry("langfuse.observation.level", "ERROR")
                .containsEntry("tool.success", false);
        Assertions.assertThat((String) span.attributes.get("langfuse.observation.status_message"))
                .contains("kaboom");
    }

    @Test
    void toolThrowingWrappedAgentInterruptedExceptionPropagates() {
        registerToolHandler(
                "wrapped_cancel_tool",
                (input, ctx) ->
                        Mono.fromCallable(
                                () -> {
                                    throw new RuntimeException(
                                            "wrapper",
                                            new AgentInterruptedException(
                                                    "Tool execution cancelled"));
                                }));

        StepVerifier.create(executor.execute("wrapped_cancel_tool", Map.of()))
                .expectErrorMatches(
                        e ->
                                e.getCause() != null
                                        && e.getCause() instanceof AgentInterruptedException)
                .verify();
    }

    // ==================== TRACER TEST DOUBLES ====================

    private static final class RecordingTracer implements Tracer {
        final List<RecordingSpan> spans = new ArrayList<>();

        @Override
        public Span startToolSpan(Span parent, String toolName, Map<String, Object> input) {
            RecordingSpan span = new RecordingSpan("tool:" + toolName);
            spans.add(span);
            return span;
        }
    }

    private static final class RecordingSpan implements Span {
        final String name;
        final Map<String, Object> attributes = new HashMap<>();
        boolean statusSuccess;
        String statusMessage;
        boolean ended;

        RecordingSpan(String name) {
            this.name = name;
        }

        @Override
        public String spanId() {
            return "test-span";
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Span parent() {
            return NoopSpan.INSTANCE;
        }

        @Override
        public void setAttribute(String key, Object value) {
            attributes.put(key, value);
        }

        @Override
        public void setStatus(boolean success, String message) {
            this.statusSuccess = success;
            this.statusMessage = message;
        }

        @Override
        public void end() {
            this.ended = true;
        }
    }
}
