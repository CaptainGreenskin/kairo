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
import io.kairo.api.tool.SyncTool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolResult;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CircuitBreakerTest {

    private DefaultToolRegistry registry;
    private DefaultPermissionGuard guard;

    @BeforeEach
    void setUp() {
        registry = new DefaultToolRegistry();
        guard = new DefaultPermissionGuard();
    }

    private DefaultToolExecutor executorWithThreshold(int threshold) {
        return new DefaultToolExecutor(registry, guard, null, null, threshold);
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
    void successfulExecutionResetsFailureCount() {
        DefaultToolExecutor executor = executorWithThreshold(3);
        AtomicInteger callCount = new AtomicInteger();

        registerToolHandler(
                "flaky",
                (input, ctx) -> {
                    int n = callCount.incrementAndGet();
                    if (n <= 2) {
                        return Mono.just(ToolResult.error("flaky", "Error: failed"));
                    }
                    return Mono.just(ToolResult.success("flaky", "success"));
                });

        // Two failures
        StepVerifier.create(executor.execute("flaky", Map.of()))
                .assertNext(r -> assertTrue(r.isError()))
                .verifyComplete();
        StepVerifier.create(executor.execute("flaky", Map.of()))
                .assertNext(r -> assertTrue(r.isError()))
                .verifyComplete();

        // One success — should reset the counter
        StepVerifier.create(executor.execute("flaky", Map.of()))
                .assertNext(r -> assertFalse(r.isError()))
                .verifyComplete();

        // Next failure should NOT trigger circuit breaker (counter was reset)
        callCount.set(0); // reset so it fails again
        StepVerifier.create(executor.execute("flaky", Map.of()))
                .assertNext(r -> assertTrue(r.isError()))
                .verifyComplete();

        // Should still execute (only 1 failure since reset)
        StepVerifier.create(executor.execute("flaky", Map.of()))
                .assertNext(
                        r -> {
                            assertTrue(r.isError());
                            assertFalse(r.content().contains("circuit-broken"));
                        })
                .verifyComplete();
    }

    @Test
    void consecutiveFailuresTriggersCircuitBreaker() {
        DefaultToolExecutor executor = executorWithThreshold(3);
        registerToolHandler(
                "bad_tool",
                (input, ctx) -> Mono.just(ToolResult.error("bad_tool", "Error: always fails")));

        // 3 consecutive failures
        for (int i = 0; i < 3; i++) {
            StepVerifier.create(executor.execute("bad_tool", Map.of()))
                    .assertNext(r -> assertTrue(r.isError()))
                    .verifyComplete();
        }

        // 4th call should be circuit-broken
        StepVerifier.create(executor.execute("bad_tool", Map.of()))
                .assertNext(
                        r -> {
                            assertTrue(r.isError());
                            assertTrue(r.content().contains("circuit-broken"));
                            assertTrue(r.content().contains("3 consecutive failures"));
                        })
                .verifyComplete();
    }

    @Test
    void circuitBrokenToolReturnsErrorWithoutExecuting() {
        DefaultToolExecutor executor = executorWithThreshold(2);
        AtomicInteger callCount = new AtomicInteger();

        registerToolHandler(
                "tracked",
                (input, ctx) -> {
                    callCount.incrementAndGet();
                    return Mono.just(ToolResult.error("tracked", "Error: fail"));
                });

        // Trigger circuit breaker with 2 failures
        StepVerifier.create(executor.execute("tracked", Map.of()))
                .assertNext(r -> assertTrue(r.isError()))
                .verifyComplete();
        StepVerifier.create(executor.execute("tracked", Map.of()))
                .assertNext(r -> assertTrue(r.isError()))
                .verifyComplete();
        assertEquals(2, callCount.get());

        // Circuit-broken: handler should NOT be called
        StepVerifier.create(executor.execute("tracked", Map.of()))
                .assertNext(r -> assertTrue(r.content().contains("circuit-broken")))
                .verifyComplete();
        assertEquals(2, callCount.get()); // still 2, not 3
    }

    @Test
    void resetClearsCircuitBreakerState() {
        DefaultToolExecutor executor = executorWithThreshold(2);
        AtomicInteger callCount = new AtomicInteger();

        registerToolHandler(
                "resettable",
                (input, ctx) -> {
                    callCount.incrementAndGet();
                    return Mono.just(ToolResult.error("resettable", "Error: fail"));
                });

        // Trigger circuit breaker
        StepVerifier.create(executor.execute("resettable", Map.of()))
                .assertNext(r -> assertTrue(r.isError()))
                .verifyComplete();
        StepVerifier.create(executor.execute("resettable", Map.of()))
                .assertNext(r -> assertTrue(r.isError()))
                .verifyComplete();

        // Verify it's circuit-broken
        StepVerifier.create(executor.execute("resettable", Map.of()))
                .assertNext(r -> assertTrue(r.content().contains("circuit-broken")))
                .verifyComplete();

        // Reset and verify it executes again
        executor.resetCircuitBreaker();
        StepVerifier.create(executor.execute("resettable", Map.of()))
                .assertNext(
                        r -> {
                            assertTrue(r.isError());
                            assertFalse(r.content().contains("circuit-broken"));
                        })
                .verifyComplete();
        assertEquals(3, callCount.get());
    }

    @Test
    void resetSpecificToolClearsOnlyThatTool() {
        DefaultToolExecutor executor = executorWithThreshold(2);

        registerToolHandler(
                "tool_a", (input, ctx) -> Mono.just(ToolResult.error("tool_a", "Error: fail a")));
        registerToolHandler(
                "tool_b", (input, ctx) -> Mono.just(ToolResult.error("tool_b", "Error: fail b")));

        // Trigger circuit breaker for both tools
        for (int i = 0; i < 2; i++) {
            StepVerifier.create(executor.execute("tool_a", Map.of()))
                    .assertNext(r -> assertTrue(r.isError()))
                    .verifyComplete();
            StepVerifier.create(executor.execute("tool_b", Map.of()))
                    .assertNext(r -> assertTrue(r.isError()))
                    .verifyComplete();
        }

        // Both should be circuit-broken
        StepVerifier.create(executor.execute("tool_a", Map.of()))
                .assertNext(r -> assertTrue(r.content().contains("circuit-broken")))
                .verifyComplete();
        StepVerifier.create(executor.execute("tool_b", Map.of()))
                .assertNext(r -> assertTrue(r.content().contains("circuit-broken")))
                .verifyComplete();

        // Reset only tool_a
        executor.resetCircuitBreaker("tool_a");

        // tool_a should execute, tool_b should still be broken
        StepVerifier.create(executor.execute("tool_a", Map.of()))
                .assertNext(r -> assertFalse(r.content().contains("circuit-broken")))
                .verifyComplete();
        StepVerifier.create(executor.execute("tool_b", Map.of()))
                .assertNext(r -> assertTrue(r.content().contains("circuit-broken")))
                .verifyComplete();
    }

    @Test
    void customThresholdWorks() {
        DefaultToolExecutor executor = executorWithThreshold(5);
        registerToolHandler(
                "custom", (input, ctx) -> Mono.just(ToolResult.error("custom", "Error: fail")));

        // 4 failures should NOT trigger circuit breaker
        for (int i = 0; i < 4; i++) {
            StepVerifier.create(executor.execute("custom", Map.of()))
                    .assertNext(
                            r -> {
                                assertTrue(r.isError());
                                assertFalse(r.content().contains("circuit-broken"));
                            })
                    .verifyComplete();
        }

        // 5th failure still executes (threshold = 5, need 5 to trip)
        StepVerifier.create(executor.execute("custom", Map.of()))
                .assertNext(
                        r -> {
                            assertTrue(r.isError());
                            assertFalse(r.content().contains("circuit-broken"));
                        })
                .verifyComplete();

        // 6th call should be circuit-broken
        StepVerifier.create(executor.execute("custom", Map.of()))
                .assertNext(
                        r -> {
                            assertTrue(r.isError());
                            assertTrue(r.content().contains("circuit-broken"));
                            assertTrue(r.content().contains("5 consecutive failures"));
                        })
                .verifyComplete();
    }

    @Test
    void differentToolsHaveIndependentCounters() {
        DefaultToolExecutor executor = executorWithThreshold(2);

        registerToolHandler(
                "tool_x", (input, ctx) -> Mono.just(ToolResult.error("tool_x", "Error: fail x")));
        registerToolHandler(
                "tool_y", (input, ctx) -> Mono.just(ToolResult.success("tool_y", "success y")));

        // Fail tool_x twice
        for (int i = 0; i < 2; i++) {
            StepVerifier.create(executor.execute("tool_x", Map.of()))
                    .assertNext(r -> assertTrue(r.isError()))
                    .verifyComplete();
        }

        // tool_x should be circuit-broken
        StepVerifier.create(executor.execute("tool_x", Map.of()))
                .assertNext(r -> assertTrue(r.content().contains("circuit-broken")))
                .verifyComplete();

        // tool_y should still work fine
        StepVerifier.create(executor.execute("tool_y", Map.of()))
                .assertNext(r -> assertFalse(r.isError()))
                .verifyComplete();
    }

    @Test
    void circuitBreakerTracksByToolNameNotToolResultId() {
        DefaultToolExecutor executor = executorWithThreshold(2);

        registerToolHandler(
                "unstable_tool",
                (input, ctx) ->
                        // Simulate tools returning invocation-scoped IDs instead of static tool
                        // names
                        Mono.just(
                                ToolResult.error(
                                        "invocation-" + System.nanoTime(), "Error: transient")));

        StepVerifier.create(executor.execute("unstable_tool", Map.of()))
                .assertNext(r -> assertTrue(r.isError()))
                .verifyComplete();
        StepVerifier.create(executor.execute("unstable_tool", Map.of()))
                .assertNext(r -> assertTrue(r.isError()))
                .verifyComplete();

        StepVerifier.create(executor.execute("unstable_tool", Map.of()))
                .assertNext(r -> assertTrue(r.content().contains("circuit-broken")))
                .verifyComplete();
    }

    @Test
    void defaultThresholdIsThree() {
        // Use the 4-arg constructor (no explicit threshold)
        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard, null, null);

        registerToolHandler(
                "default_thresh",
                (input, ctx) -> Mono.just(ToolResult.error("default_thresh", "Error: fail")));

        // 3 failures should NOT be circuit-broken (they execute normally)
        for (int i = 0; i < 3; i++) {
            StepVerifier.create(executor.execute("default_thresh", Map.of()))
                    .assertNext(
                            r -> {
                                assertTrue(r.isError());
                                assertFalse(r.content().contains("circuit-broken"));
                            })
                    .verifyComplete();
        }

        // 4th call should be circuit-broken (default threshold = 3)
        StepVerifier.create(executor.execute("default_thresh", Map.of()))
                .assertNext(r -> assertTrue(r.content().contains("circuit-broken")))
                .verifyComplete();
    }
}
