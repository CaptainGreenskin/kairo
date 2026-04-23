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
package io.kairo.core.execution;

import io.kairo.api.execution.DurableExecution;
import io.kairo.api.execution.DurableExecutionStore;
import io.kairo.api.execution.ExecutionEvent;
import io.kairo.api.execution.ExecutionStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * In-memory implementation of {@link DurableExecutionStore} for testing.
 *
 * <p>Backed by a {@link ConcurrentHashMap}. Thread-safe for concurrent access. Not suitable for
 * production use — all data is lost on process exit.
 *
 * @since v0.8
 */
public class InMemoryDurableExecutionStore implements DurableExecutionStore {

    private final ConcurrentHashMap<String, DurableExecution> store = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> persist(DurableExecution execution) {
        return Mono.defer(
                () -> {
                    DurableExecution existing =
                            store.putIfAbsent(execution.executionId(), execution);
                    if (existing != null) {
                        return Mono.error(
                                new IllegalStateException(
                                        "Execution already exists: " + execution.executionId()));
                    }
                    return Mono.empty();
                });
    }

    @Override
    public Mono<DurableExecution> recover(String executionId) {
        return Mono.defer(() -> Mono.justOrEmpty(store.get(executionId)));
    }

    @Override
    public Flux<DurableExecution> listPending() {
        return Flux.defer(
                () ->
                        Flux.fromIterable(store.values())
                                .filter(
                                        e ->
                                                e.status() == ExecutionStatus.RUNNING
                                                        || e.status()
                                                                == ExecutionStatus.RECOVERING));
    }

    @Override
    public Mono<Void> appendEvent(String executionId, ExecutionEvent event) {
        return Mono.defer(
                () -> {
                    DurableExecution updated =
                            store.computeIfPresent(
                                    executionId,
                                    (id, current) -> {
                                        List<ExecutionEvent> newEvents =
                                                new ArrayList<>(current.events());
                                        newEvents.add(event);
                                        return new DurableExecution(
                                                current.executionId(),
                                                current.agentId(),
                                                List.copyOf(newEvents),
                                                current.checkpoint(),
                                                current.status(),
                                                current.version(),
                                                current.createdAt(),
                                                Instant.now());
                                    });
                    if (updated == null) {
                        return Mono.error(
                                new IllegalStateException("Execution not found: " + executionId));
                    }
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> updateStatus(
            String executionId, ExecutionStatus status, int expectedVersion) {
        return Mono.defer(
                () -> {
                    boolean[] success = {false};
                    boolean[] found = {false};
                    store.computeIfPresent(
                            executionId,
                            (id, current) -> {
                                found[0] = true;
                                if (current.version() != expectedVersion) {
                                    return current; // leave unchanged — will check below
                                }
                                success[0] = true;
                                return new DurableExecution(
                                        current.executionId(),
                                        current.agentId(),
                                        current.events(),
                                        current.checkpoint(),
                                        status,
                                        current.version() + 1,
                                        current.createdAt(),
                                        Instant.now());
                            });
                    if (!found[0]) {
                        return Mono.error(
                                new IllegalStateException("Execution not found: " + executionId));
                    }
                    if (!success[0]) {
                        return Mono.error(
                                new IllegalStateException(
                                        "Version mismatch for execution "
                                                + executionId
                                                + ": expected "
                                                + expectedVersion));
                    }
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> delete(String executionId) {
        return Mono.defer(
                () -> {
                    store.remove(executionId);
                    return Mono.empty();
                });
    }
}
