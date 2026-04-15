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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class GracefulShutdownManagerTest {

    private GracefulShutdownManager manager;

    @BeforeEach
    void setUp() {
        manager = GracefulShutdownManager.getInstance();
        manager.resetForTesting();
    }

    @AfterEach
    void tearDown() {
        manager.resetForTesting();
    }

    private Agent stubAgent(String id, String name) {
        return new Agent() {
            private volatile AgentState state = AgentState.IDLE;

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
                return state;
            }

            @Override
            public void interrupt() {
                state = AgentState.SUSPENDED;
            }
        };
    }

    @Test
    void initialStateIsRunning() {
        assertEquals(GracefulShutdownManager.ShutdownState.RUNNING, manager.getState());
        assertTrue(manager.isAcceptingRequests());
    }

    @Test
    void performShutdownTransitionsToShuttingDown() {
        // Register an agent so it doesn't immediately transition to TERMINATED
        Agent agent = stubAgent("a1", "blocker");
        manager.registerAgent(agent);

        assertTrue(manager.performShutdown());
        assertEquals(GracefulShutdownManager.ShutdownState.SHUTTING_DOWN, manager.getState());
        assertFalse(manager.isAcceptingRequests());
    }

    @Test
    void performShutdownReturnsFalseOnSecondCall() {
        Agent agent = stubAgent("a1", "blocker");
        manager.registerAgent(agent);
        assertTrue(manager.performShutdown());
        assertFalse(manager.performShutdown());
    }

    @Test
    void shutdownTerminatesImmediatelyWithNoActiveAgents() {
        manager.performShutdown();
        boolean terminated = manager.awaitTermination(Duration.ofSeconds(2));
        assertTrue(terminated);
        assertEquals(GracefulShutdownManager.ShutdownState.TERMINATED, manager.getState());
    }

    @Test
    void registerAndUnregisterAgent() {
        Agent agent = stubAgent("a1", "test-agent");
        manager.registerAgent(agent);

        manager.performShutdown();
        // Should not terminate yet because agent is active
        boolean terminated = manager.awaitTermination(Duration.ofMillis(200));
        assertFalse(terminated);
        assertEquals(GracefulShutdownManager.ShutdownState.SHUTTING_DOWN, manager.getState());

        // Unregister agent — should transition to TERMINATED
        manager.unregisterAgent(agent);
        terminated = manager.awaitTermination(Duration.ofSeconds(2));
        assertTrue(terminated);
        assertEquals(GracefulShutdownManager.ShutdownState.TERMINATED, manager.getState());
    }

    @Test
    void cleanupFunctionsRunDuringShutdown() {
        AtomicBoolean cleaned = new AtomicBoolean(false);
        manager.registerCleanup(() -> cleaned.set(true));

        manager.performShutdown();
        assertTrue(cleaned.get());
    }

    @Test
    void cleanupUnregisterPreventsExecution() {
        AtomicBoolean cleaned = new AtomicBoolean(false);
        Runnable unregister = manager.registerCleanup(() -> cleaned.set(true));
        unregister.run();

        manager.performShutdown();
        assertFalse(cleaned.get());
    }

    @Test
    void multipleCleanupFunctionsAllRun() {
        AtomicInteger count = new AtomicInteger(0);
        manager.registerCleanup(count::incrementAndGet);
        manager.registerCleanup(count::incrementAndGet);
        manager.registerCleanup(count::incrementAndGet);

        manager.performShutdown();
        assertEquals(3, count.get());
    }

    @Test
    void cleanupFailureDoesNotPreventShutdown() {
        AtomicBoolean secondRan = new AtomicBoolean(false);
        manager.registerCleanup(
                () -> {
                    throw new RuntimeException("cleanup failed");
                });
        manager.registerCleanup(() -> secondRan.set(true));

        // Register an agent so shutdown doesn't immediately terminate
        Agent agent = stubAgent("a1", "blocker");
        manager.registerAgent(agent);

        manager.performShutdown();
        // Shutdown should still proceed despite first cleanup failing
        assertEquals(GracefulShutdownManager.ShutdownState.SHUTTING_DOWN, manager.getState());
    }

    @Test
    void timeoutInterruptsActiveAgents() throws InterruptedException {
        manager.setShutdownTimeout(Duration.ofSeconds(1));
        Agent agent = stubAgent("slow", "slow-agent");
        manager.registerAgent(agent);

        manager.performShutdown();

        // Wait for timeout to fire
        Thread.sleep(2000);

        // Agent should have been interrupted
        assertEquals(AgentState.SUSPENDED, agent.state());
    }

    @Test
    void resetForTestingRestoresInitialState() {
        Agent agent = stubAgent("a1", "test");
        manager.registerAgent(agent);
        manager.performShutdown();

        manager.resetForTesting();

        assertEquals(GracefulShutdownManager.ShutdownState.RUNNING, manager.getState());
        assertTrue(manager.isAcceptingRequests());
    }

    @Test
    void shutdownSignalCompletesOnTimeout() throws InterruptedException {
        manager.setShutdownTimeout(Duration.ofSeconds(1));
        Agent agent = stubAgent("a1", "test");
        manager.registerAgent(agent);

        AtomicBoolean signalFired = new AtomicBoolean(false);
        // Sinks.Empty completes the Mono (not emits a value), so use doOnTerminate
        manager.getShutdownSignal().doOnTerminate(() -> signalFired.set(true)).subscribe();

        manager.performShutdown();
        Thread.sleep(2500);

        assertTrue(signalFired.get());
    }
}
