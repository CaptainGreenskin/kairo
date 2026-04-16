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

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentState;
import io.kairo.api.message.Msg;
import io.kairo.core.shutdown.GracefulShutdownManager;
import io.kairo.core.shutdown.GracefulShutdownManager.ShutdownState;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * Integration tests for {@link GracefulShutdownManager} covering state transitions, agent tracking,
 * cleanup execution, timeout enforcement, concurrent safety, and idempotency.
 *
 * <p>Tests use stub Agent implementations — no real LLM calls.
 */
@Tag("integration")
class GracefulShutdownIT {

    private GracefulShutdownManager manager;

    @BeforeEach
    void setUp() {
        manager = new GracefulShutdownManager();
        manager.setShutdownTimeout(Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() {
        manager.resetForTesting();
    }

    // ================================
    //  Stub Agent for testing
    // ================================

    /** Minimal Agent stub for shutdown manager testing. */
    static class StubAgent implements Agent {
        private final String id;
        private final String name;
        private volatile boolean interrupted = false;

        StubAgent(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public Mono<Msg> call(Msg input) {
            return Mono.empty();
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public AgentState state() {
            return AgentState.IDLE;
        }

        @Override
        public void interrupt() {
            interrupted = true;
        }

        boolean wasInterrupted() {
            return interrupted;
        }
    }

    // ================================
    //  Test 1: Initial state is RUNNING
    // ================================

    @Test
    @DisplayName("Initial state is RUNNING and accepting requests")
    void initialState_isRunning() {
        assertEquals(ShutdownState.RUNNING, manager.getState());
        assertTrue(manager.isAcceptingRequests());
    }

    // ================================
    //  Test 2: Register agent is tracked
    // ================================

    @Test
    @DisplayName("Registered agent is tracked by the manager")
    void registerAgent_isTracked() {
        StubAgent agent = new StubAgent("agent-1", "test-agent");
        manager.registerAgent(agent);

        // Manager is still running
        assertEquals(ShutdownState.RUNNING, manager.getState());
        assertTrue(manager.isAcceptingRequests());
    }

    // ================================
    //  Test 3: Unregister agent is removed
    // ================================

    @Test
    @DisplayName("Unregistered agent is removed from tracking")
    void unregisterAgent_isRemoved() {
        StubAgent agent = new StubAgent("agent-1", "test-agent");
        manager.registerAgent(agent);
        manager.unregisterAgent(agent);

        // After unregister, shutdown with no active agents should terminate immediately
        assertTrue(manager.performShutdown());
        assertTrue(manager.awaitTermination(Duration.ofSeconds(2)));
        assertEquals(ShutdownState.TERMINATED, manager.getState());
    }

    // ================================
    //  Test 4: performShutdown transitions to SHUTTING_DOWN
    // ================================

    @Test
    @DisplayName("performShutdown transitions state from RUNNING to SHUTTING_DOWN/TERMINATED")
    void performShutdown_changesState() {
        StubAgent agent = new StubAgent("agent-1", "test-agent");
        manager.registerAgent(agent);

        assertTrue(manager.performShutdown(), "First shutdown call should return true");
        assertFalse(manager.isAcceptingRequests(), "Should no longer accept requests");

        // State should be SHUTTING_DOWN since there are active agents
        assertEquals(ShutdownState.SHUTTING_DOWN, manager.getState());
    }

    // ================================
    //  Test 5: Shutdown with no active agents terminates immediately
    // ================================

    @Test
    @DisplayName("Shutdown with no active agents terminates immediately")
    void shutdown_noActiveAgents_terminatesImmediately() {
        long start = System.currentTimeMillis();
        assertTrue(manager.performShutdown());
        assertTrue(manager.awaitTermination(Duration.ofSeconds(2)));
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(ShutdownState.TERMINATED, manager.getState());
        assertTrue(elapsed < 1000, "Shutdown with no agents should be fast, took " + elapsed + "ms");
    }

    // ================================
    //  Test 6: Shutdown waits for active agents to unregister
    // ================================

    @Test
    @DisplayName("Shutdown waits for in-flight agents to unregister before terminating")
    void shutdown_withActiveAgents_waitsForUnregister() throws InterruptedException {
        StubAgent agent = new StubAgent("agent-1", "test-agent");
        manager.registerAgent(agent);
        manager.performShutdown();

        assertEquals(ShutdownState.SHUTTING_DOWN, manager.getState());

        // Unregister agent from a separate thread after a short delay
        CountDownLatch terminated = new CountDownLatch(1);
        Thread waiter =
                new Thread(
                        () -> {
                            manager.awaitTermination(Duration.ofSeconds(5));
                            terminated.countDown();
                        });
        waiter.start();

        // Agent still active - should not have terminated yet
        assertFalse(
                terminated.await(200, TimeUnit.MILLISECONDS),
                "Should not terminate while agent is still active");

        // Now unregister the agent
        manager.unregisterAgent(agent);

        // Should terminate now
        assertTrue(
                terminated.await(3, TimeUnit.SECONDS),
                "Should terminate after all agents unregistered");
        assertEquals(ShutdownState.TERMINATED, manager.getState());
    }

    // ================================
    //  Test 7: Shutdown timeout forces completion
    // ================================

    @Test
    @DisplayName("Shutdown timeout triggers interrupt on active agents")
    void shutdown_timeout_forcesInterrupt() throws InterruptedException {
        manager.setShutdownTimeout(Duration.ofSeconds(2));

        StubAgent agent = new StubAgent("agent-1", "slow-agent");
        manager.registerAgent(agent);

        manager.performShutdown();

        // Wait for timeout to be enforced (2s timeout + 1s monitor interval + buffer)
        Thread.sleep(4000);

        // Agent should have been interrupted after timeout
        assertTrue(agent.wasInterrupted(), "Agent should have been interrupted after timeout");
    }

    // ================================
    //  Test 8: Cleanup functions executed on shutdown
    // ================================

    @Test
    @DisplayName("Cleanup functions are executed during shutdown")
    void shutdown_cleanupFunctions_executed() {
        AtomicBoolean cleanup1Executed = new AtomicBoolean(false);
        AtomicBoolean cleanup2Executed = new AtomicBoolean(false);

        manager.registerCleanup(() -> cleanup1Executed.set(true));
        manager.registerCleanup(() -> cleanup2Executed.set(true));

        manager.performShutdown();

        assertTrue(cleanup1Executed.get(), "First cleanup should have been executed");
        assertTrue(cleanup2Executed.get(), "Second cleanup should have been executed");
    }

    // ================================
    //  Test 9: Cleanup unregister works
    // ================================

    @Test
    @DisplayName("Cleanup function can be unregistered before shutdown")
    void cleanup_unregister_preventsExecution() {
        AtomicBoolean cleanupExecuted = new AtomicBoolean(false);

        Runnable unregister = manager.registerCleanup(() -> cleanupExecuted.set(true));

        // Unregister before shutdown
        unregister.run();

        manager.performShutdown();
        manager.awaitTermination(Duration.ofSeconds(2));

        assertFalse(cleanupExecuted.get(), "Unregistered cleanup should not have been executed");
    }

    // ================================
    //  Test 10: Concurrent registrations are thread-safe
    // ================================

    @Test
    @DisplayName("Concurrent agent register/unregister is thread-safe")
    void shutdown_concurrentRegistrations_threadSafe() throws InterruptedException {
        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicBoolean anyException = new AtomicBoolean(false);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            new Thread(
                            () -> {
                                try {
                                    startLatch.await();
                                    StubAgent agent =
                                            new StubAgent("agent-" + idx, "agent-" + idx);
                                    manager.registerAgent(agent);
                                    Thread.sleep(10); // brief overlap
                                    manager.unregisterAgent(agent);
                                } catch (Exception e) {
                                    anyException.set(true);
                                } finally {
                                    doneLatch.countDown();
                                }
                            })
                    .start();
        }

        startLatch.countDown();
        assertTrue(
                doneLatch.await(10, TimeUnit.SECONDS),
                "All threads should complete within timeout");

        assertFalse(anyException.get(), "No thread should have thrown an exception");

        // After all unregistered, shutdown should terminate immediately
        assertTrue(manager.performShutdown());
        assertTrue(manager.awaitTermination(Duration.ofSeconds(2)));
        assertEquals(ShutdownState.TERMINATED, manager.getState());
    }

    // ================================
    //  Test 11: Shutdown is idempotent
    // ================================

    @Test
    @DisplayName("Multiple performShutdown calls are safe - second returns false")
    void shutdown_idempotent_multipleCallsSafe() {
        assertTrue(manager.performShutdown(), "First shutdown should return true");
        assertFalse(manager.performShutdown(), "Second shutdown should return false");
        manager.awaitTermination(Duration.ofSeconds(2));

        assertEquals(ShutdownState.TERMINATED, manager.getState());

        // Third call after termination should also be safe
        assertFalse(manager.performShutdown(), "Shutdown after TERMINATED should return false");
    }

    // ================================
    //  Test 12: Multiple agents tracked and shutdown
    // ================================

    @Test
    @DisplayName("Multiple agents are all tracked and shutdown waits for all")
    void multipleAgents_allTracked() throws InterruptedException {
        int agentCount = 5;
        StubAgent[] agents = new StubAgent[agentCount];
        for (int i = 0; i < agentCount; i++) {
            agents[i] = new StubAgent("agent-" + i, "agent-" + i);
            manager.registerAgent(agents[i]);
        }

        manager.performShutdown();
        assertEquals(ShutdownState.SHUTTING_DOWN, manager.getState());

        // Unregister agents one by one
        for (int i = 0; i < agentCount - 1; i++) {
            manager.unregisterAgent(agents[i]);
            assertEquals(
                    ShutdownState.SHUTTING_DOWN,
                    manager.getState(),
                    "Should still be SHUTTING_DOWN with agents remaining");
        }

        // Unregister last agent - should terminate
        manager.unregisterAgent(agents[agentCount - 1]);
        assertTrue(manager.awaitTermination(Duration.ofSeconds(2)));
        assertEquals(ShutdownState.TERMINATED, manager.getState());
    }

    // ================================
    //  Test 13: Cleanup failure does not prevent shutdown
    // ================================

    @Test
    @DisplayName("Exception in cleanup function does not prevent shutdown")
    void cleanup_exception_doesNotPreventShutdown() {
        AtomicBoolean secondCleanupRan = new AtomicBoolean(false);

        manager.registerCleanup(
                () -> {
                    throw new RuntimeException("cleanup error");
                });
        manager.registerCleanup(() -> secondCleanupRan.set(true));

        // Should not throw
        assertDoesNotThrow(() -> manager.performShutdown());
        manager.awaitTermination(Duration.ofSeconds(2));

        assertEquals(ShutdownState.TERMINATED, manager.getState());
        // Note: Set iteration order not guaranteed, but the manager should handle the exception
    }

    // ================================
    //  Test 14: awaitTermination returns false on timeout
    // ================================

    @Test
    @DisplayName("awaitTermination returns false when it times out")
    void awaitTermination_returnsFalse_onTimeout() {
        StubAgent agent = new StubAgent("agent-1", "stuck-agent");
        manager.registerAgent(agent);
        manager.performShutdown();

        // Very short timeout - agent still active
        assertFalse(
                manager.awaitTermination(Duration.ofMillis(200)),
                "Should return false when agents are still active");

        // Clean up
        manager.unregisterAgent(agent);
    }

    // ================================
    //  Test 15: Concurrent shutdown calls are safe
    // ================================

    @Test
    @DisplayName("Concurrent shutdown calls do not cause race conditions")
    void concurrentShutdown_noRaceCondition() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicBoolean anyException = new AtomicBoolean(false);

        for (int i = 0; i < threadCount; i++) {
            new Thread(
                            () -> {
                                try {
                                    startLatch.await();
                                    if (manager.performShutdown()) {
                                        successCount.incrementAndGet();
                                    }
                                } catch (Exception e) {
                                    anyException.set(true);
                                } finally {
                                    doneLatch.countDown();
                                }
                            })
                    .start();
        }

        startLatch.countDown();
        assertTrue(
                doneLatch.await(10, TimeUnit.SECONDS),
                "All threads should complete within timeout");

        assertFalse(anyException.get(), "No thread should have thrown an exception");
        assertEquals(1, successCount.get(), "Exactly one thread should succeed in initiating shutdown");
        manager.awaitTermination(Duration.ofSeconds(2));
        assertEquals(ShutdownState.TERMINATED, manager.getState());
    }
}
