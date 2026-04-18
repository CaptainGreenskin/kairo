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
package io.kairo.core.integration;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.tool.*;
import io.kairo.core.tool.DefaultPermissionGuard;
import io.kairo.core.tool.DefaultToolExecutor;
import io.kairo.core.tool.DefaultToolRegistry;
import io.kairo.core.tool.ToolHandler;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the tool execution pipeline covering registration, permission checks,
 * circuit breaker, allowed-tools filtering, timeout handling, and parallel execution.
 *
 * <p>Uses REAL DefaultToolRegistry + DefaultToolExecutor + DefaultPermissionGuard with mock tool
 * handlers. No real API calls.
 */
@Tag("integration")
class ToolExecutionPipelineIT {

    private DefaultToolRegistry registry;
    private DefaultPermissionGuard guard;

    @BeforeEach
    void setUp() {
        registry = new DefaultToolRegistry();
        guard = new DefaultPermissionGuard();
    }

    // ================================
    //  Helper: register a ToolHandler with a ToolDefinition
    // ================================

    private void registerHandler(String name, ToolSideEffect sideEffect, ToolHandler handler) {
        ToolDefinition def =
                new ToolDefinition(
                        name,
                        "Test tool: " + name,
                        ToolCategory.GENERAL,
                        new JsonSchema("object", null, null, null),
                        handler.getClass(),
                        null,
                        sideEffect);
        registry.register(def);
        registry.registerInstance(name, handler);
    }

    private void registerReadHandler(String name, ToolHandler handler) {
        registerHandler(name, ToolSideEffect.READ_ONLY, handler);
    }

    // ================================
    //  Test 1: toolRegistration_thenExecution_returnsResult
    // ================================

    @Test
    void toolRegistration_thenExecution_returnsResult() {
        registerReadHandler(
                "echo",
                input -> new ToolResult("echo", "echoed: " + input.get("text"), false, Map.of()));

        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);

        ToolResult result =
                executor.execute("echo", Map.of("text", "hello")).block(Duration.ofSeconds(5));

        assertNotNull(result);
        assertFalse(result.isError());
        assertEquals("echoed: hello", result.content());
    }

    // ================================
    //  Test 2: unknownTool_returnsError
    // ================================

    @Test
    void unknownTool_returnsError() {
        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);

        ToolResult result =
                executor.execute("nonexistent_tool", Map.of()).block(Duration.ofSeconds(5));

        assertNotNull(result);
        assertTrue(result.isError());
        assertTrue(result.content().contains("Unknown tool"));
    }

    // ================================
    //  Test 3: toolWithTimeout_exceedsTimeout_returnsError
    // ================================

    @Test
    void toolWithTimeout_exceedsTimeout_returnsError() {
        registerReadHandler(
                "slow_tool",
                input -> {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return new ToolResult("slow_tool", "done", false, Map.of());
                });

        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);

        ToolResult result =
                executor.execute("slow_tool", Map.of(), Duration.ofMillis(300))
                        .block(Duration.ofSeconds(5));

        assertNotNull(result);
        assertTrue(result.isError());
        assertTrue(result.content().contains("timed out"));
    }

    // ================================
    //  Test 4: circuitBreaker_afterNFailures_shortCircuits
    // ================================

    @Test
    void circuitBreaker_afterNFailures_shortCircuits() {
        // Threshold = 3 (default)
        registerReadHandler(
                "flaky",
                input -> {
                    throw new RuntimeException("boom");
                });

        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);

        // Trigger 3 consecutive failures
        for (int i = 0; i < 3; i++) {
            ToolResult r = executor.execute("flaky", Map.of()).block(Duration.ofSeconds(5));
            assertNotNull(r);
            assertTrue(r.isError());
        }

        // 4th call should be circuit-broken (not even attempt execution)
        ToolResult circuitBroken = executor.execute("flaky", Map.of()).block(Duration.ofSeconds(5));

        assertNotNull(circuitBroken);
        assertTrue(circuitBroken.isError());
        assertTrue(circuitBroken.content().contains("circuit-broken"));
    }

    // ================================
    //  Test 5: circuitBreaker_successResets_counter
    // ================================

    @Test
    void circuitBreaker_successResets_counter() {
        AtomicInteger callCount = new AtomicInteger(0);

        registerReadHandler(
                "intermittent",
                input -> {
                    int n = callCount.incrementAndGet();
                    // Fail on calls 1 and 2, succeed on call 3
                    if (n <= 2) {
                        throw new RuntimeException("fail #" + n);
                    }
                    return new ToolResult("intermittent", "ok", false, Map.of());
                });

        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);

        // 2 failures
        executor.execute("intermittent", Map.of()).block(Duration.ofSeconds(5));
        executor.execute("intermittent", Map.of()).block(Duration.ofSeconds(5));

        // 1 success — should reset circuit breaker counter
        ToolResult success =
                executor.execute("intermittent", Map.of()).block(Duration.ofSeconds(5));
        assertNotNull(success);
        assertFalse(success.isError());

        // Now fail 2 more times — should NOT trip circuit (counter was reset)
        callCount.set(0); // reset our call counter to make it fail again
        registerReadHandler(
                "intermittent",
                input -> {
                    throw new RuntimeException("fail again");
                });

        executor.execute("intermittent", Map.of()).block(Duration.ofSeconds(5));
        executor.execute("intermittent", Map.of()).block(Duration.ofSeconds(5));

        // 3rd failure after reset — now it should trip
        ToolResult r3 = executor.execute("intermittent", Map.of()).block(Duration.ofSeconds(5));
        assertNotNull(r3);
        assertTrue(r3.isError());
        // At this point we have 3 consecutive failures so next call should be circuit-broken
        ToolResult tripped =
                executor.execute("intermittent", Map.of()).block(Duration.ofSeconds(5));
        assertNotNull(tripped);
        assertTrue(tripped.isError());
        assertTrue(tripped.content().contains("circuit-broken"));
    }

    // ================================
    //  Test 6: circuitBreaker_reset_allowsExecution
    // ================================

    @Test
    void circuitBreaker_reset_allowsExecution() {
        AtomicInteger callCount = new AtomicInteger(0);

        registerReadHandler(
                "resettable",
                input -> {
                    int n = callCount.incrementAndGet();
                    if (n <= 3) {
                        throw new RuntimeException("fail #" + n);
                    }
                    return new ToolResult("resettable", "recovered", false, Map.of());
                });

        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);

        // Trip the circuit breaker with 3 failures
        for (int i = 0; i < 3; i++) {
            executor.execute("resettable", Map.of()).block(Duration.ofSeconds(5));
        }

        // Verify circuit is tripped
        ToolResult tripped = executor.execute("resettable", Map.of()).block(Duration.ofSeconds(5));
        assertNotNull(tripped);
        assertTrue(tripped.content().contains("circuit-broken"));

        // Reset circuit breaker
        executor.resetCircuitBreaker("resettable");

        // Now execution should proceed again (callCount > 3 so it succeeds)
        ToolResult recovered =
                executor.execute("resettable", Map.of()).block(Duration.ofSeconds(5));
        assertNotNull(recovered);
        assertFalse(recovered.isError());
        assertEquals("recovered", recovered.content());
    }

    // ================================
    //  Test 7: allowedTools_blocksUnauthorized
    // ================================

    @Test
    void allowedTools_blocksUnauthorized() {
        registerReadHandler(
                "allowed_tool", input -> new ToolResult("allowed_tool", "ok", false, Map.of()));
        registerReadHandler(
                "blocked_tool", input -> new ToolResult("blocked_tool", "ok", false, Map.of()));

        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);
        executor.setAllowedTools(Set.of("allowed_tool"));

        ToolResult result = executor.execute("blocked_tool", Map.of()).block(Duration.ofSeconds(5));

        assertNotNull(result);
        assertTrue(result.isError());
        assertTrue(result.content().contains("not allowed"));
    }

    // ================================
    //  Test 8: allowedTools_permitsAuthorized
    // ================================

    @Test
    void allowedTools_permitsAuthorized() {
        registerReadHandler(
                "allowed_tool",
                input -> new ToolResult("allowed_tool", "success", false, Map.of()));

        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);
        executor.setAllowedTools(Set.of("allowed_tool"));

        ToolResult result = executor.execute("allowed_tool", Map.of()).block(Duration.ofSeconds(5));

        assertNotNull(result);
        assertFalse(result.isError());
        assertEquals("success", result.content());
    }

    // ================================
    //  Test 9: clearAllowedTools_removesRestriction
    // ================================

    @Test
    void clearAllowedTools_removesRestriction() {
        registerReadHandler(
                "any_tool", input -> new ToolResult("any_tool", "executed", false, Map.of()));

        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);

        // Set restriction
        executor.setAllowedTools(Set.of("other_tool"));
        ToolResult blocked = executor.execute("any_tool", Map.of()).block(Duration.ofSeconds(5));
        assertNotNull(blocked);
        assertTrue(blocked.isError());

        // Clear restriction
        executor.clearAllowedTools();
        ToolResult unblocked = executor.execute("any_tool", Map.of()).block(Duration.ofSeconds(5));
        assertNotNull(unblocked);
        assertFalse(unblocked.isError());
        assertEquals("executed", unblocked.content());
    }

    // ================================
    //  Test 10: permissionGuard_deniedTool_returnsError
    // ================================

    @Test
    void permissionGuard_deniedTool_returnsError() {
        // Register a "bash" tool — DefaultPermissionGuard blocks dangerous commands on "bash"
        registerHandler(
                "bash",
                ToolSideEffect.SYSTEM_CHANGE,
                input -> new ToolResult("bash", "executed", false, Map.of()));

        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);
        // Need ALLOWED permission to bypass the ASK check for SYSTEM_CHANGE
        executor.setToolPermission("bash", ToolPermission.ALLOWED);

        // Execute with a dangerous command — permission guard should block
        ToolResult result =
                executor.execute("bash", Map.of("command", "rm -rf /"))
                        .block(Duration.ofSeconds(5));

        assertNotNull(result);
        assertTrue(result.isError());
        assertTrue(result.content().contains("Permission denied"));
    }

    // ================================
    //  Test 11: concurrentExecution_multipleTools_noInterference
    // ================================

    @Test
    void concurrentExecution_multipleTools_noInterference() {
        CountDownLatch latch = new CountDownLatch(1);

        registerReadHandler(
                "tool_a",
                input -> {
                    try {
                        latch.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return new ToolResult("tool_a", "result_a", false, Map.of());
                });
        registerReadHandler(
                "tool_b",
                input -> {
                    latch.countDown(); // signal tool_a to proceed
                    return new ToolResult("tool_b", "result_b", false, Map.of());
                });

        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);

        List<ToolInvocation> invocations =
                List.of(
                        new ToolInvocation("tool_a", Map.of()),
                        new ToolInvocation("tool_b", Map.of()));

        List<ToolResult> results =
                executor.executeParallel(invocations).collectList().block(Duration.ofSeconds(10));

        assertNotNull(results);
        assertEquals(2, results.size());
        // Results should be in original invocation order
        assertEquals("result_a", results.get(0).content());
        assertEquals("result_b", results.get(1).content());
        results.forEach(r -> assertFalse(r.isError()));
    }

    // ================================
    //  Test 12: toolResult_isError_trackedByCircuitBreaker
    // ================================

    @Test
    void toolResult_isError_trackedByCircuitBreaker() {
        // A tool that returns an error ToolResult (not throwing an exception)
        registerReadHandler(
                "soft_fail", input -> new ToolResult("soft_fail", "soft error", true, Map.of()));

        // Use threshold = 2 for faster test
        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard, null, null, 2);

        // 2 error results should trip the circuit breaker
        executor.execute("soft_fail", Map.of()).block(Duration.ofSeconds(5));
        executor.execute("soft_fail", Map.of()).block(Duration.ofSeconds(5));

        // Next call should be circuit-broken
        ToolResult tripped = executor.execute("soft_fail", Map.of()).block(Duration.ofSeconds(5));

        assertNotNull(tripped);
        assertTrue(tripped.isError());
        assertTrue(tripped.content().contains("circuit-broken"));
    }
}
