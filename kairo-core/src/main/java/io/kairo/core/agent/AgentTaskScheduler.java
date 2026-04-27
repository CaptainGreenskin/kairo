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
package io.kairo.core.agent;

import io.kairo.api.agent.Agent;
import io.kairo.api.message.Msg;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Submits agent tasks for asynchronous execution with optional wall-clock timeout.
 *
 * <p>Uses virtual threads on JDK 21+ ({@code Executors.newVirtualThreadPerTaskExecutor}) with a
 * fallback to {@code newCachedThreadPool} on JDK 17. A single-threaded watchdog fires {@link
 * AgentTaskHandle#cancel()} when {@link AgentTaskOptions#maxDuration()} elapses.
 *
 * <p>Call {@link #shutdown()} when the scheduler is no longer needed; running tasks are not
 * interrupted by shutdown.
 */
public final class AgentTaskScheduler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AgentTaskScheduler.class);

    private final ExecutorService executor;
    private final ScheduledExecutorService watchdog;

    public AgentTaskScheduler() {
        this(createDefaultExecutor());
    }

    public AgentTaskScheduler(ExecutorService executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.watchdog =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "kairo-task-watchdog");
                            t.setDaemon(true);
                            return t;
                        });
    }

    /**
     * Submit an agent task.
     *
     * @param agent the agent to invoke
     * @param input the input message
     * @param options task options (timeout, callback)
     * @return a handle to the running task
     * @throws RejectedExecutionException if the scheduler has been shut down
     */
    public AgentTaskHandle submit(Agent agent, Msg input, AgentTaskOptions options) {
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(options, "options");

        Callable<Msg> task = () -> agent.call(input).block();
        Future<Msg> future = executor.submit(task);
        AgentTaskHandle handle = new AgentTaskHandle(future, agent);

        scheduleWatchdog(handle, agent, options);
        return handle;
    }

    /** Submit with default options (30-minute timeout). */
    public AgentTaskHandle submit(Agent agent, Msg input) {
        return submit(agent, input, AgentTaskOptions.defaults());
    }

    /** Shut down the executor and watchdog. Running tasks are not interrupted. */
    public void shutdown() {
        executor.shutdown();
        watchdog.shutdown();
    }

    @Override
    public void close() {
        shutdown();
    }

    private void scheduleWatchdog(AgentTaskHandle handle, Agent agent, AgentTaskOptions options) {
        Duration maxDuration = options.maxDuration();
        ScheduledFuture<?> watchdogFuture =
                watchdog.schedule(
                        () -> {
                            if (handle.isDone()) return;
                            log.warn(
                                    "Agent task '{}' timed out after {}; cancelling",
                                    agent.name(),
                                    maxDuration);
                            handle.cancel();
                            if (options.onTimeout() != null) {
                                try {
                                    options.onTimeout().run();
                                } catch (Exception ex) {
                                    log.warn(
                                            "onTimeout callback threw for agent '{}': {}",
                                            agent.name(),
                                            ex.getMessage());
                                }
                            }
                        },
                        maxDuration.toMillis(),
                        TimeUnit.MILLISECONDS);

        // Cancel the watchdog early if the task completes on its own.
        // We use a listener thread — lightweight since most tasks complete before timeout.
        executor.submit(
                () -> {
                    try {
                        handle.get();
                    } catch (Exception ignored) {
                        // task failed or was cancelled — watchdog still cleans up
                    } finally {
                        watchdogFuture.cancel(false);
                    }
                });
    }

    private static ExecutorService createDefaultExecutor() {
        try {
            // JDK 21: virtual threads
            return (ExecutorService)
                    Executors.class.getMethod("newVirtualThreadPerTaskExecutor").invoke(null);
        } catch (ReflectiveOperationException ignored) {
            // JDK 17 fallback
            return Executors.newCachedThreadPool(
                    r -> {
                        Thread t = new Thread(r, "kairo-agent-task");
                        t.setDaemon(true);
                        return t;
                    });
        }
    }
}
