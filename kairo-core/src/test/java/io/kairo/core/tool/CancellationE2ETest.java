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

import io.kairo.api.agent.CancellationSignal;
import io.kairo.api.exception.AgentInterruptedException;
import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.SyncTool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolResult;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * End-to-end integration test verifying that {@link AgentInterruptedException} propagates through
 * the full {@link DefaultToolExecutor} pipeline, NOT converted into a {@link ToolResult} with error
 * text.
 *
 * <p>Scenarios:
 *
 * <ol>
 *   <li>Cancellation during tool execution (blocking tool + mid-execution signal)
 *   <li>Tool directly throwing AgentInterruptedException
 *   <li>Wrapped AgentInterruptedException propagation
 *   <li>Cancellation with cooperative signal via Reactor Context
 * </ol>
 */
class CancellationE2ETest {

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

    // ===== 1. Tool throwing AgentInterruptedException propagates as error signal =====

    @Test
    void directAgentInterruptedException_propagatesAsMonoError() {
        registerToolHandler(
                "cancel_tool",
                (input, ctx) ->
                        Mono.fromCallable(
                                () -> {
                                    throw new AgentInterruptedException("Tool execution cancelled");
                                }));

        StepVerifier.create(executor.execute("cancel_tool", Map.of()))
                .expectErrorMatches(
                        e ->
                                e instanceof AgentInterruptedException
                                        && e.getMessage().contains("cancelled"))
                .verify(Duration.ofSeconds(5));
    }

    // ===== 2. Wrapped AgentInterruptedException propagates =====

    @Test
    void wrappedAgentInterruptedException_propagatesAsMonoError() {
        registerToolHandler(
                "wrapped_cancel",
                (input, ctx) ->
                        Mono.fromCallable(
                                () -> {
                                    throw new RuntimeException(
                                            "wrapper",
                                            new AgentInterruptedException("inner cancel"));
                                }));

        StepVerifier.create(executor.execute("wrapped_cancel", Map.of()))
                .expectErrorMatches(
                        e ->
                                e.getCause() != null
                                        && e.getCause() instanceof AgentInterruptedException)
                .verify(Duration.ofSeconds(5));
    }

    // ===== 3. Cooperative cancellation via CancellationSignal during slow tool =====

    @Test
    void cooperativeCancellation_duringSlowTool_propagatesAgentInterruptedException() {
        registerToolHandler(
                "slow_tool",
                (input, ctx) ->
                        Mono.fromCallable(
                                () -> {
                                    try {
                                        Thread.sleep(10_000); // block for 10 seconds
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                    return ToolResult.success("slow_tool", "should not reach");
                                }));

        AtomicBoolean cancelled = new AtomicBoolean(false);
        CancellationSignal signal = cancelled::get;

        // Fire cancellation after 200ms
        Mono.delay(Duration.ofMillis(200)).doOnNext(tick -> cancelled.set(true)).subscribe();

        Mono<ToolResult> execution =
                executor.execute("slow_tool", Map.of())
                        .contextWrite(ctx -> ctx.put(CancellationSignal.CONTEXT_KEY, signal));

        StepVerifier.create(execution)
                .expectErrorMatches(e -> e instanceof AgentInterruptedException)
                .verify(Duration.ofSeconds(5));
    }

    // ===== 4. Pre-cancelled signal immediately terminates =====

    @Test
    void preCancelledSignal_immediatelyTerminatesWithAgentInterruptedException() {
        registerToolHandler(
                "any_tool",
                (input, ctx) ->
                        Mono.fromCallable(
                                () -> {
                                    fail("Tool should not execute when pre-cancelled");
                                    return ToolResult.success("any_tool", "nope");
                                }));

        CancellationSignal alreadyCancelled = () -> true;

        Mono<ToolResult> execution =
                executor.execute("any_tool", Map.of())
                        .contextWrite(
                                ctx -> ctx.put(CancellationSignal.CONTEXT_KEY, alreadyCancelled));

        StepVerifier.create(execution)
                .expectErrorMatches(e -> e instanceof AgentInterruptedException)
                .verify(Duration.ofSeconds(5));
    }

    // ===== 5. CancellationException also propagates (not swallowed) =====

    @Test
    void cancellationException_propagatesAsError() {
        registerToolHandler(
                "cancel_ex_tool",
                (input, ctx) ->
                        Mono.fromCallable(
                                () -> {
                                    throw new java.util.concurrent.CancellationException(
                                            "task cancelled");
                                }));

        StepVerifier.create(executor.execute("cancel_ex_tool", Map.of()))
                .expectErrorMatches(e -> e instanceof java.util.concurrent.CancellationException)
                .verify(Duration.ofSeconds(5));
    }

    // ===== 6. Normal error is NOT treated as cancellation (regression) =====

    @Test
    void normalException_convertedToErrorResult_notPropagated() {
        registerToolHandler(
                "failing_tool",
                (input, ctx) ->
                        Mono.fromCallable(
                                () -> {
                                    throw new RuntimeException("ordinary failure");
                                }));

        StepVerifier.create(executor.execute("failing_tool", Map.of()))
                .assertNext(
                        result -> {
                            assertTrue(result.isError());
                            assertTrue(result.content().contains("ordinary failure"));
                        })
                .verifyComplete();
    }

    // ===== 7. No cancellation — tool completes normally (regression) =====

    @Test
    void noCancellation_toolCompletesNormally() {
        registerToolHandler(
                "echo",
                (input, ctx) ->
                        Mono.just(ToolResult.success("echo", "echoed: " + input.get("msg"))));

        CancellationSignal neverCancelled = () -> false;

        Mono<ToolResult> execution =
                executor.execute("echo", Map.of("msg", "hello"))
                        .contextWrite(
                                ctx -> ctx.put(CancellationSignal.CONTEXT_KEY, neverCancelled));

        StepVerifier.create(execution)
                .assertNext(
                        result -> {
                            assertFalse(result.isError());
                            assertEquals("echoed: hello", result.content());
                        })
                .verifyComplete();
    }

    // ===== 8. Cancellation mid-execution via cooperative signal with Mono.delay tool =====

    @Test
    void cooperativeCancellation_monoDelayTool_propagatesAgentInterruptedException() {
        // Register a tool definition for the delayed tool
        ToolDefinition def =
                new ToolDefinition(
                        "delay_tool",
                        "delayed tool",
                        ToolCategory.GENERAL,
                        new JsonSchema("object", null, null, null),
                        SyncTool.class);
        registry.register(def);
        registry.registerInstance(
                "delay_tool",
                (SyncTool)
                        (input, ctx) ->
                                Mono.fromCallable(
                                        () -> {
                                            try {
                                                Thread.sleep(10_000);
                                            } catch (InterruptedException e) {
                                                Thread.currentThread().interrupt();
                                            }
                                            return ToolResult.success("delay_tool", "done");
                                        }));

        AtomicBoolean cancelled = new AtomicBoolean(false);
        CancellationSignal signal = cancelled::get;

        // Fire cancellation after 150ms
        Mono.delay(Duration.ofMillis(150)).doOnNext(tick -> cancelled.set(true)).subscribe();

        Mono<ToolResult> execution =
                executor.execute("delay_tool", Map.of())
                        .contextWrite(ctx -> ctx.put(CancellationSignal.CONTEXT_KEY, signal));

        StepVerifier.create(execution)
                .expectErrorMatches(
                        e ->
                                e instanceof AgentInterruptedException
                                        && e.getMessage().contains("cancelled"))
                .verify(Duration.ofSeconds(5));
    }

    // ===== 9. Concurrent cancel + execute should not deadlock =====

    @Test
    void concurrentCancelAndExecute_shouldNotDeadlock() throws Exception {
        int iterations = 10;
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        try {
            for (int iter = 0; iter < iterations; iter++) {
                // Fresh setup per iteration to avoid state leakage
                DefaultToolRegistry iterRegistry = new DefaultToolRegistry();
                DefaultPermissionGuard iterGuard = new DefaultPermissionGuard();
                DefaultToolExecutor iterExecutor = new DefaultToolExecutor(iterRegistry, iterGuard);

                String toolName = "concurrent_tool_" + iter;
                ToolDefinition def =
                        new ToolDefinition(
                                toolName,
                                "concurrent test",
                                ToolCategory.GENERAL,
                                new JsonSchema("object", null, null, null),
                                SyncTool.class);
                iterRegistry.register(def);
                iterRegistry.registerInstance(
                        toolName,
                        (SyncTool)
                                (input, ctx) ->
                                        Mono.fromCallable(
                                                () -> {
                                                    try {
                                                        Thread.sleep(500);
                                                    } catch (InterruptedException e) {
                                                        Thread.currentThread().interrupt();
                                                    }
                                                    return ToolResult.success(toolName, "done");
                                                }));

                AtomicBoolean cancelled = new AtomicBoolean(false);
                CancellationSignal signal = cancelled::get;
                CountDownLatch startLatch = new CountDownLatch(1);
                CountDownLatch doneLatch = new CountDownLatch(1);
                AtomicInteger completed = new AtomicInteger(0);
                AtomicInteger errors = new AtomicInteger(0);

                final String finalToolName = toolName;

                // Submit executor thread
                pool.submit(
                        () -> {
                            try {
                                startLatch.await();
                                iterExecutor
                                        .execute(finalToolName, Map.of())
                                        .contextWrite(
                                                ctx ->
                                                        ctx.put(
                                                                CancellationSignal.CONTEXT_KEY,
                                                                signal))
                                        .block(Duration.ofSeconds(5));
                                completed.incrementAndGet();
                            } catch (Exception e) {
                                // Either AgentInterruptedException or timeout — both acceptable
                                errors.incrementAndGet();
                            } finally {
                                doneLatch.countDown();
                            }
                        });

                // Submit cancellation thread
                pool.submit(
                        () -> {
                            try {
                                startLatch.await();
                                Thread.sleep(50); // brief delay before cancel
                                cancelled.set(true);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });

                // Release both threads simultaneously
                startLatch.countDown();

                // Wait for executor thread to finish (or hit its own 5s block timeout)
                assertTrue(
                        doneLatch.await(6, TimeUnit.SECONDS),
                        "Iteration " + iter + ": executor did not terminate (possible deadlock)");

                // Verify: either completed or got a cancellation error — no deadlock
                assertTrue(
                        completed.get() + errors.get() >= 1,
                        "Iteration "
                                + iter
                                + ": expected completion or error but got neither "
                                + "(possible deadlock). completed="
                                + completed.get()
                                + ", errors="
                                + errors.get());
            }
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS), "Pool did not terminate");
        }
    }
}
