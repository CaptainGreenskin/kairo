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

import io.kairo.api.agent.Agent;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Manages graceful shutdown of the Kairo agent runtime.
 *
 * <p>Inspired by claude-code-best's cleanup registry pattern. Provides:
 *
 * <ul>
 *   <li>State machine: RUNNING → SHUTTING_DOWN → TERMINATED
 *   <li>Active agent tracking with interrupt-on-timeout
 *   <li>Configurable shutdown timeout
 *   <li>Cleanup function registry (like claude-code-best's cleanupRegistry)
 *   <li>JVM shutdown hook integration
 * </ul>
 */
public final class GracefulShutdownManager {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdownManager.class);

    /** Shutdown states. */
    public enum ShutdownState {
        RUNNING,
        SHUTTING_DOWN,
        TERMINATED
    }

    private final AtomicReference<ShutdownState> state =
            new AtomicReference<>(ShutdownState.RUNNING);
    private final Set<String> activeAgentIds = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Agent> activeAgents = new ConcurrentHashMap<>();
    private final Set<Runnable> cleanupFunctions = ConcurrentHashMap.newKeySet();
    private final AtomicReference<Instant> shutdownStartedAt = new AtomicReference<>(null);
    private final AtomicBoolean monitorStarted = new AtomicBoolean(false);
    private final AtomicReference<Sinks.Empty<Void>> shutdownSignal =
            new AtomicReference<>(Sinks.empty());
    private volatile Duration shutdownTimeout = Duration.ofSeconds(30);
    private final Object terminationLock = new Object();

    private final ScheduledExecutorService monitor =
            new ScheduledThreadPoolExecutor(
                    1,
                    r -> {
                        Thread t = new Thread(r, "kairo-shutdown-monitor");
                        t.setDaemon(true);
                        return t;
                    });
    private volatile ScheduledFuture<?> monitorFuture;

    public GracefulShutdownManager() {
        // Register JVM shutdown hook
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    if (state.get() == ShutdownState.RUNNING) {
                                        performShutdown();
                                        awaitTermination(shutdownTimeout);
                                    }
                                },
                                "kairo-jvm-shutdown"));
    }

    public ShutdownState getState() {
        return state.get();
    }

    public boolean isAcceptingRequests() {
        return state.get() == ShutdownState.RUNNING;
    }

    /**
     * Set the shutdown timeout.
     *
     * @param timeout max time to wait for agents to finish
     */
    public void setShutdownTimeout(Duration timeout) {
        this.shutdownTimeout = timeout;
    }

    /**
     * Returns a Mono that completes when shutdown timeout is reached. Tool executors can race
     * against this signal to abort early.
     */
    public Mono<Void> getShutdownSignal() {
        return shutdownSignal.get().asMono();
    }

    /**
     * Register an active agent. Called when an agent starts processing.
     *
     * <p>Registration is rejected if the manager is no longer in RUNNING state. Uses double-check
     * pattern to prevent race with shutdown initiation.
     *
     * @param agent the agent to track
     * @return true if the agent was registered, false if shutdown is in progress
     */
    public boolean registerAgent(Agent agent) {
        if (state.get() != ShutdownState.RUNNING) {
            log.warn("Rejected agent registration '{}' — shutdown in progress", agent.name());
            return false;
        }
        synchronized (terminationLock) {
            // Double-check after acquiring lock to prevent race with performShutdown
            if (state.get() != ShutdownState.RUNNING) {
                log.warn("Rejected agent registration '{}' — shutdown in progress", agent.name());
                return false;
            }
            activeAgentIds.add(agent.id());
            activeAgents.put(agent.id(), agent);
        }
        return true;
    }

    /**
     * Unregister an agent. Called when an agent completes or fails.
     *
     * @param agent the agent to untrack
     */
    public void unregisterAgent(Agent agent) {
        activeAgentIds.remove(agent.id());
        activeAgents.remove(agent.id());
        checkTermination();
    }

    /**
     * Register a cleanup function to run during shutdown. Returns an unregister function (like
     * claude-code-best's cleanupRegistry).
     *
     * @param cleanup the cleanup function
     * @return a Runnable that removes the cleanup function
     */
    public Runnable registerCleanup(Runnable cleanup) {
        cleanupFunctions.add(cleanup);
        return () -> cleanupFunctions.remove(cleanup);
    }

    /**
     * Initiate graceful shutdown.
     *
     * @return true if shutdown was initiated, false if already shutting down
     */
    public boolean performShutdown() {
        ShutdownState prev =
                state.getAndUpdate(
                        s ->
                                s == ShutdownState.TERMINATED
                                        ? ShutdownState.TERMINATED
                                        : ShutdownState.SHUTTING_DOWN);
        if (prev != ShutdownState.RUNNING) {
            return false;
        }

        shutdownStartedAt.set(Instant.now());
        log.info(
                "Graceful shutdown initiated, {} active agent(s), timeout={}",
                activeAgentIds.size(),
                shutdownTimeout);

        // Run cleanup functions first (like claude-code-best)
        for (Runnable cleanup : cleanupFunctions) {
            try {
                cleanup.run();
            } catch (Exception e) {
                log.warn("Cleanup function failed: {}", e.getMessage());
            }
        }

        // Start monitor to enforce timeout
        startMonitor();
        checkTermination();
        return true;
    }

    private void startMonitor() {
        if (!monitorStarted.compareAndSet(false, true)) return;
        monitorFuture = monitor.scheduleAtFixedRate(this::enforceTimeout, 1, 1, TimeUnit.SECONDS);
    }

    private void enforceTimeout() {
        if (state.get() != ShutdownState.SHUTTING_DOWN) return;
        Instant started = shutdownStartedAt.get();
        if (started == null) return;

        Duration elapsed = Duration.between(started, Instant.now());
        if (elapsed.compareTo(shutdownTimeout) >= 0) {
            log.info(
                    "Shutdown timeout reached ({}s), force interrupting {} active agent(s)",
                    elapsed.getSeconds(),
                    activeAgents.size());
            shutdownSignal.get().tryEmitEmpty();

            // Interrupt all active agents
            for (Agent agent : activeAgents.values()) {
                try {
                    agent.interrupt();
                    log.info("Interrupted agent '{}' for shutdown", agent.name());
                } catch (Exception e) {
                    log.warn("Failed to interrupt agent '{}': {}", agent.name(), e.getMessage());
                }
            }
        }
    }

    private void checkTermination() {
        synchronized (terminationLock) {
            if (state.get() == ShutdownState.SHUTTING_DOWN && activeAgentIds.isEmpty()) {
                if (state.compareAndSet(ShutdownState.SHUTTING_DOWN, ShutdownState.TERMINATED)) {
                    log.info("All agents terminated, shutdown complete");
                    if (monitorFuture != null) monitorFuture.cancel(false);
                    terminationLock.notifyAll();
                }
            }
        }
    }

    /**
     * Block until shutdown completes or timeout elapses.
     *
     * @param timeout max time to wait
     * @return true if terminated, false if timed out
     */
    public boolean awaitTermination(Duration timeout) {
        checkTermination();
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        synchronized (terminationLock) {
            while (state.get() != ShutdownState.TERMINATED) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) return false;
                try {
                    terminationLock.wait(Math.min(remaining, 1000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return true;
    }

    /** Reset for testing. */
    public void resetForTesting() {
        if (monitorFuture != null) monitorFuture.cancel(false);
        state.set(ShutdownState.RUNNING);
        activeAgentIds.clear();
        activeAgents.clear();
        cleanupFunctions.clear();
        shutdownStartedAt.set(null);
        monitorStarted.set(false);
        shutdownSignal.set(Sinks.empty());
    }
}
