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
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle to an agent task submitted via {@link AgentTaskScheduler}.
 *
 * <p>Wraps the underlying {@link Future} and the {@link Agent} instance so that callers can cancel,
 * poll status, or block for the result without knowing the scheduler internals.
 */
public final class AgentTaskHandle {

    private static final Logger log = LoggerFactory.getLogger(AgentTaskHandle.class);

    private final Future<Msg> future;
    private final Agent agent;

    AgentTaskHandle(Future<Msg> future, Agent agent) {
        this.future = Objects.requireNonNull(future, "future");
        this.agent = Objects.requireNonNull(agent, "agent");
    }

    /**
     * Cancel the task.
     *
     * <p>Calls {@link Agent#interrupt()} for cooperative cancellation, then cancels the underlying
     * Future. Idempotent.
     */
    public void cancel() {
        log.debug("Cancelling agent task for agent '{}'", agent.name());
        try {
            agent.interrupt();
        } catch (Exception ex) {
            log.warn("interrupt() threw for agent '{}': {}", agent.name(), ex.getMessage());
        }
        future.cancel(true);
    }

    /** Returns {@code true} if the task has completed (success, failure, or cancellation). */
    public boolean isDone() {
        return future.isDone();
    }

    /** Returns {@code true} if the task is still running. */
    public boolean isRunning() {
        return !future.isDone();
    }

    /**
     * Blocks until the task completes and returns the agent's response.
     *
     * @throws CancellationException if the task was cancelled
     * @throws ExecutionException if the agent threw an exception
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public Msg get() throws ExecutionException, InterruptedException, CancellationException {
        return future.get();
    }

    /**
     * Blocks up to {@code timeout} for the task to complete.
     *
     * @throws TimeoutException if the task did not complete within the timeout
     */
    public Msg get(long timeout, TimeUnit unit)
            throws ExecutionException, InterruptedException, TimeoutException {
        return future.get(timeout, unit);
    }
}
