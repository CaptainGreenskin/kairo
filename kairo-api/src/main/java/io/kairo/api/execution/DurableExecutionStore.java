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
package io.kairo.api.execution;

import io.kairo.api.Stable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * SPI for persisting and recovering durable agent executions.
 *
 * <p>Implementations must be thread-safe. The store uses optimistic locking via the {@link
 * DurableExecution#version()} field — callers must handle version-mismatch errors (e.g. retry).
 *
 * @since v0.8 (promoted to @Stable in v1.0.0)
 */
@Stable(
        value = "Durable execution store SPI; shape frozen since v0.8, promoted post-v0.9 GA",
        since = "1.0.0")
public interface DurableExecutionStore {

    /**
     * Persist a new execution. Errors if an execution with the same ID already exists.
     *
     * @param execution the execution to persist
     * @return completes when the execution is stored
     */
    Mono<Void> persist(DurableExecution execution);

    /**
     * Recover an execution by its ID.
     *
     * @param executionId the execution ID
     * @return the execution, or empty if not found
     */
    Mono<DurableExecution> recover(String executionId);

    /**
     * List all executions with status {@link ExecutionStatus#RUNNING} or {@link
     * ExecutionStatus#RECOVERING}.
     *
     * @return a stream of pending executions
     */
    Flux<DurableExecution> listPending();

    /**
     * Append an event to an existing execution's event log.
     *
     * @param executionId the execution ID
     * @param event the event to append
     * @return completes when the event is appended
     */
    Mono<Void> appendEvent(String executionId, ExecutionEvent event);

    /**
     * Update the status of an execution with optimistic locking.
     *
     * <p>The update succeeds only if the current version matches {@code expectedVersion}. On
     * success, the version is incremented by one.
     *
     * @param executionId the execution ID
     * @param status the new status
     * @param expectedVersion the expected current version for optimistic locking
     * @return completes on success; errors on version mismatch or missing execution
     */
    Mono<Void> updateStatus(String executionId, ExecutionStatus status, int expectedVersion);

    /**
     * Delete an execution and all its events.
     *
     * @param executionId the execution ID
     * @return completes when the execution is deleted (no-op if not found)
     */
    Mono<Void> delete(String executionId);
}
