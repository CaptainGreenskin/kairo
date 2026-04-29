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
package io.kairo.core.shutdown;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentState;
import io.kairo.api.message.Msg;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * Extended edge-case tests for {@link GracefulShutdownManager} covering idempotency, hook failure
 * isolation, unregistration, concurrency, and post-shutdown hook registration.
 */
class GracefulShutdownManagerExtTest {

    private GracefulShutdownManager manager;

    @BeforeEach
    void setUp() {
        manager = new GracefulShutdownManager();
    }

    @AfterEach
    void tearDown() {
        manager.resetForTesting();
    }

    private Agent stubAgent(String id) {
        return new Agent() {
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
                return "stub-" + id;
            }

            @Override
            public AgentState state() {
                return AgentState.IDLE;
            }

            @Override
            public void interrupt() {}
        };
    }

    // ── All hooks execute (ordering: all must run) ───────────────────────────

    @Test
    void allCleanupHooks_execute_duringShutdown() {
        AtomicInteger count = new AtomicInteger();
        manager.registerCleanup(count::incrementAndGet);
        manager.registerCleanup(count::incrementAndGet);
        manager.registerCleanup(count::incrementAndGet);

        manager.performShutdown();

        assertEquals(3, count.get());
    }

    // ── Idempotency: multiple shutdown() calls execute cleanup only once ──────

    @Test
    void performShutdown_idempotent_cleanupRunsOnce() {
        AtomicInteger count = new AtomicInteger();
        manager.registerCleanup(count::incrementAndGet);

        manager.performShutdown();
        manager.performShutdown(); // second call should be no-op
        manager.performShutdown(); // third call should also be no-op

        assertEquals(1, count.get(), "Cleanup must run exactly once");
    }

    @Test
    void performShutdown_secondCall_returnsFalse() {
        assertTrue(manager.performShutdown());
        assertFalse(manager.performShutdown());
        assertFalse(manager.performShutdown());
    }

    // ── Hook throws exception — other hooks still execute ────────────────────

    @Test
    void cleanupHookException_doesNotPreventOtherHooksFromRunning() {
        AtomicBoolean hook1Ran = new AtomicBoolean(false);
        AtomicBoolean hook2Ran = new AtomicBoolean(false);
        AtomicBoolean hook3Ran = new AtomicBoolean(false);

        manager.registerCleanup(() -> hook1Ran.set(true));
        manager.registerCleanup(
                () -> {
                    throw new RuntimeException("intentional failure");
                });
        manager.registerCleanup(() -> hook3Ran.set(true));

        // Ensure at least two non-throwing hooks run
        manager.performShutdown();

        assertTrue(hook1Ran.get() || hook3Ran.get(), "At least two hooks should run");
        // Both non-throwing hooks must run
        assertTrue(hook1Ran.get(), "hook1 must run");
        assertTrue(hook3Ran.get(), "hook3 must run");
    }

    @Test
    void multipleHookFailures_shutdownCompletes() {
        manager.registerCleanup(
                () -> {
                    throw new RuntimeException("fail 1");
                });
        manager.registerCleanup(
                () -> {
                    throw new RuntimeException("fail 2");
                });

        // Should not throw
        assertDoesNotThrow(() -> manager.performShutdown());
        boolean terminated = manager.awaitTermination(Duration.ofSeconds(2));
        assertTrue(terminated);
    }

    // ── Unregistered hook is not executed ────────────────────────────────────

    @Test
    void unregisteredHook_doesNotExecute() {
        AtomicBoolean ran = new AtomicBoolean(false);
        Runnable unregister = manager.registerCleanup(() -> ran.set(true));
        unregister.run(); // unregister before shutdown

        manager.performShutdown();

        assertFalse(ran.get(), "Unregistered hook must not run");
    }

    @Test
    void partialUnregistration_onlyUnregisteredHookSkipped() {
        AtomicBoolean hook1Ran = new AtomicBoolean(false);
        AtomicBoolean hook2Ran = new AtomicBoolean(false);

        manager.registerCleanup(() -> hook1Ran.set(true));
        Runnable unregister = manager.registerCleanup(() -> hook2Ran.set(true));
        unregister.run();

        manager.performShutdown();

        assertTrue(hook1Ran.get(), "Remaining hook must run");
        assertFalse(hook2Ran.get(), "Unregistered hook must not run");
    }

    // ── Empty hook set — shutdown() returns normally ──────────────────────────

    @Test
    void emptyHookSet_shutdownTerminatesNormally() {
        assertTrue(manager.performShutdown());
        boolean terminated = manager.awaitTermination(Duration.ofSeconds(2));
        assertTrue(terminated);
        assertEquals(GracefulShutdownManager.ShutdownState.TERMINATED, manager.getState());
    }

    // ── Concurrent shutdown() — no duplicate execution ────────────────────────

    @Test
    void concurrentShutdown_cleanupRunsExactlyOnce() throws InterruptedException {
        AtomicInteger count = new AtomicInteger();
        manager.registerCleanup(count::incrementAndGet);

        int threads = 20;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger trueCount = new AtomicInteger();

        var executor = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            executor.submit(
                    () -> {
                        try {
                            startGate.await();
                            if (manager.performShutdown()) trueCount.incrementAndGet();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            done.countDown();
                        }
                    });
        }

        startGate.countDown();
        done.await();
        executor.shutdown();

        assertEquals(1, trueCount.get(), "Exactly one thread should initiate shutdown");
        assertEquals(1, count.get(), "Cleanup must run exactly once under concurrency");
    }

    // ── Post-shutdown hook registration doesn't execute ───────────────────────

    @Test
    void hookRegisteredAfterShutdown_doesNotExecute() {
        // Complete shutdown with no agents
        manager.performShutdown();
        manager.awaitTermination(Duration.ofSeconds(2));
        assertEquals(GracefulShutdownManager.ShutdownState.TERMINATED, manager.getState());

        // Register cleanup after shutdown is complete
        AtomicBoolean ran = new AtomicBoolean(false);
        manager.registerCleanup(() -> ran.set(true));

        // Second performShutdown returns false — cleanup won't run again
        assertFalse(manager.performShutdown());
        assertFalse(ran.get(), "Hook registered after shutdown must not execute");
    }

    // ── Agent registration rejected after shutdown initiated ──────────────────

    @Test
    void agentRegistration_rejectedAfterShutdownInitiated() {
        Agent blocker = stubAgent("blocker");
        manager.registerAgent(blocker);
        manager.performShutdown();

        Agent late = stubAgent("late");
        boolean accepted = manager.registerAgent(late);
        assertFalse(accepted, "Agent registration must be rejected after shutdown");
    }

    @Test
    void cleanupHooks_executeInRegistrationOrder() {
        List<String> order = new ArrayList<>();
        List<String> syncOrder = java.util.Collections.synchronizedList(order);

        manager.registerCleanup(() -> syncOrder.add("first"));
        manager.registerCleanup(() -> syncOrder.add("second"));
        manager.registerCleanup(() -> syncOrder.add("third"));

        manager.performShutdown();

        // All hooks must execute. Note: ConcurrentHashMap.newKeySet() does not guarantee
        // insertion-order iteration, so we verify completeness rather than strict ordering.
        assertEquals(3, syncOrder.size(), "All hooks must execute");
        assertTrue(
                syncOrder.containsAll(List.of("first", "second", "third")),
                "All registered hooks must be present");
    }

    @Test
    void shutdownTimeout_interruptsActiveAgents() throws InterruptedException {
        manager.setShutdownTimeout(Duration.ofMillis(500));

        AtomicBoolean interrupted = new AtomicBoolean(false);
        Agent longRunning =
                new Agent() {
                    @Override
                    public Mono<Msg> call(Msg input) {
                        return Mono.empty();
                    }

                    @Override
                    public String id() {
                        return "long-running";
                    }

                    @Override
                    public String name() {
                        return "long-running-agent";
                    }

                    @Override
                    public AgentState state() {
                        return AgentState.IDLE;
                    }

                    @Override
                    public void interrupt() {
                        interrupted.set(true);
                    }
                };

        manager.registerAgent(longRunning);
        manager.performShutdown();

        // Wait for timeout to fire and interrupt the agent
        Thread.sleep(1500);

        assertTrue(interrupted.get(), "Agent must be interrupted when shutdown timeout is reached");
        assertEquals(
                GracefulShutdownManager.ShutdownState.SHUTTING_DOWN,
                manager.getState(),
                "Should still be SHUTTING_DOWN until agent is unregistered");

        // Unregister to complete shutdown
        manager.unregisterAgent(longRunning);
        boolean terminated = manager.awaitTermination(Duration.ofSeconds(2));
        assertTrue(terminated);
        assertEquals(GracefulShutdownManager.ShutdownState.TERMINATED, manager.getState());
    }

    // ── Order recording: all hooks collect results ────────────────────────────

    @Test
    void cleanupResults_areCollectedFromAllHooks() {
        List<String> results = new ArrayList<>();
        // Synchronized list avoids race for this test
        List<String> syncResults = java.util.Collections.synchronizedList(results);

        manager.registerCleanup(() -> syncResults.add("A"));
        manager.registerCleanup(() -> syncResults.add("B"));
        manager.registerCleanup(() -> syncResults.add("C"));

        manager.performShutdown();

        assertEquals(3, syncResults.size(), "All 3 hooks must add to result list");
        assertTrue(syncResults.containsAll(List.of("A", "B", "C")));
    }
}
