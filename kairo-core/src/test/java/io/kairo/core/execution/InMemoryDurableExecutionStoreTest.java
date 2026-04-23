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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.execution.DurableExecution;
import io.kairo.api.execution.DurableExecutionStore;
import io.kairo.api.execution.ExecutionEvent;
import io.kairo.api.execution.ExecutionEventType;
import io.kairo.api.execution.ExecutionStatus;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/** Tests for {@link InMemoryDurableExecutionStore}. */
class InMemoryDurableExecutionStoreTest {

    private DurableExecutionStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryDurableExecutionStore();
    }

    private DurableExecution testExecution(String executionId, ExecutionStatus status) {
        return new DurableExecution(
                executionId, "agent-1", List.of(), null, status, 0, Instant.now(), Instant.now());
    }

    private ExecutionEvent testEvent(String eventId, ExecutionEventType type) {
        return new ExecutionEvent(
                eventId, type, Instant.now(), "{\"key\":\"value\"}", "hash-" + eventId, 1);
    }

    @Nested
    @DisplayName("Persist and Recover")
    class PersistRecover {

        @Test
        @DisplayName("persist and recover returns all fields")
        void persistAndRecover() {
            DurableExecution exec = testExecution("exec-1", ExecutionStatus.RUNNING);

            StepVerifier.create(store.persist(exec)).verifyComplete();

            StepVerifier.create(store.recover("exec-1"))
                    .assertNext(
                            loaded -> {
                                assertThat(loaded.executionId()).isEqualTo("exec-1");
                                assertThat(loaded.agentId()).isEqualTo("agent-1");
                                assertThat(loaded.status()).isEqualTo(ExecutionStatus.RUNNING);
                                assertThat(loaded.version()).isZero();
                                assertThat(loaded.events()).isEmpty();
                                assertThat(loaded.checkpoint()).isNull();
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("persist duplicate errors")
        void persistDuplicate() {
            DurableExecution exec = testExecution("exec-1", ExecutionStatus.RUNNING);
            store.persist(exec).block();

            StepVerifier.create(store.persist(exec))
                    .expectErrorSatisfies(
                            e -> {
                                assertThat(e).isInstanceOf(IllegalStateException.class);
                                assertThat(e.getMessage()).contains("already exists");
                            })
                    .verify();
        }

        @Test
        @DisplayName("recover non-existent returns empty")
        void recoverNonExistent() {
            StepVerifier.create(store.recover("no-such-id")).verifyComplete();
        }
    }

    @Nested
    @DisplayName("AppendEvent")
    class AppendEventTests {

        @Test
        @DisplayName("append events and verify ordering")
        void appendEventAndRecover() {
            store.persist(testExecution("exec-1", ExecutionStatus.RUNNING)).block();

            ExecutionEvent e1 = testEvent("ev-1", ExecutionEventType.MODEL_CALL_REQUEST);
            ExecutionEvent e2 = testEvent("ev-2", ExecutionEventType.MODEL_CALL_RESPONSE);
            ExecutionEvent e3 = testEvent("ev-3", ExecutionEventType.ITERATION_COMPLETE);

            store.appendEvent("exec-1", e1).block();
            store.appendEvent("exec-1", e2).block();
            store.appendEvent("exec-1", e3).block();

            StepVerifier.create(store.recover("exec-1"))
                    .assertNext(
                            loaded -> {
                                assertThat(loaded.events()).hasSize(3);
                                assertThat(loaded.events().get(0).eventId()).isEqualTo("ev-1");
                                assertThat(loaded.events().get(1).eventId()).isEqualTo("ev-2");
                                assertThat(loaded.events().get(2).eventId()).isEqualTo("ev-3");
                                assertThat(loaded.events().get(0).eventType())
                                        .isEqualTo(ExecutionEventType.MODEL_CALL_REQUEST);
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("append event to non-existent execution errors")
        void appendEventNonExistent() {
            ExecutionEvent event = testEvent("ev-1", ExecutionEventType.MODEL_CALL_REQUEST);

            StepVerifier.create(store.appendEvent("no-such-id", event))
                    .expectErrorSatisfies(
                            e -> {
                                assertThat(e).isInstanceOf(IllegalStateException.class);
                                assertThat(e.getMessage()).contains("not found");
                            })
                    .verify();
        }

        @Test
        @DisplayName("event ordering preserved across multiple appends")
        void eventOrderingPreserved() {
            store.persist(testExecution("exec-1", ExecutionStatus.RUNNING)).block();

            for (int i = 0; i < 10; i++) {
                store.appendEvent(
                                "exec-1",
                                testEvent("ev-" + i, ExecutionEventType.TOOL_CALL_REQUEST))
                        .block();
            }

            StepVerifier.create(store.recover("exec-1"))
                    .assertNext(
                            loaded -> {
                                assertThat(loaded.events()).hasSize(10);
                                for (int i = 0; i < 10; i++) {
                                    assertThat(loaded.events().get(i).eventId())
                                            .isEqualTo("ev-" + i);
                                }
                            })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("UpdateStatus")
    class UpdateStatusTests {

        @Test
        @DisplayName("update with correct version succeeds and increments version")
        void updateStatusSuccess() {
            store.persist(testExecution("exec-1", ExecutionStatus.RUNNING)).block();

            StepVerifier.create(store.updateStatus("exec-1", ExecutionStatus.COMPLETED, 0))
                    .verifyComplete();

            StepVerifier.create(store.recover("exec-1"))
                    .assertNext(
                            loaded -> {
                                assertThat(loaded.status()).isEqualTo(ExecutionStatus.COMPLETED);
                                assertThat(loaded.version()).isEqualTo(1);
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("update with wrong version errors (optimistic locking)")
        void updateStatusVersionMismatch() {
            store.persist(testExecution("exec-1", ExecutionStatus.RUNNING)).block();

            StepVerifier.create(store.updateStatus("exec-1", ExecutionStatus.COMPLETED, 99))
                    .expectErrorSatisfies(
                            e -> {
                                assertThat(e).isInstanceOf(IllegalStateException.class);
                                assertThat(e.getMessage()).contains("Version mismatch");
                            })
                    .verify();

            // verify status unchanged
            StepVerifier.create(store.recover("exec-1"))
                    .assertNext(
                            loaded -> {
                                assertThat(loaded.status()).isEqualTo(ExecutionStatus.RUNNING);
                                assertThat(loaded.version()).isZero();
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("update non-existent execution errors")
        void updateStatusNonExistent() {
            StepVerifier.create(store.updateStatus("no-such-id", ExecutionStatus.COMPLETED, 0))
                    .expectErrorSatisfies(
                            e -> {
                                assertThat(e).isInstanceOf(IllegalStateException.class);
                                assertThat(e.getMessage()).contains("not found");
                            })
                    .verify();
        }
    }

    @Nested
    @DisplayName("ListPending")
    class ListPendingTests {

        @Test
        @DisplayName("returns only RUNNING and RECOVERING executions")
        void listPendingFilters() {
            store.persist(testExecution("e-running", ExecutionStatus.RUNNING)).block();
            store.persist(testExecution("e-recovering", ExecutionStatus.RECOVERING)).block();
            store.persist(testExecution("e-completed", ExecutionStatus.COMPLETED)).block();
            store.persist(testExecution("e-failed", ExecutionStatus.FAILED)).block();
            store.persist(testExecution("e-paused", ExecutionStatus.PAUSED)).block();

            StepVerifier.create(store.listPending().collectList())
                    .assertNext(
                            list -> {
                                assertThat(list).hasSize(2);
                                assertThat(list)
                                        .extracting(DurableExecution::executionId)
                                        .containsExactlyInAnyOrder("e-running", "e-recovering");
                            })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Delete")
    class DeleteTests {

        @Test
        @DisplayName("delete removes execution")
        void deleteRemoves() {
            store.persist(testExecution("exec-1", ExecutionStatus.RUNNING)).block();

            StepVerifier.create(store.delete("exec-1")).verifyComplete();

            StepVerifier.create(store.recover("exec-1")).verifyComplete();
        }

        @Test
        @DisplayName("delete non-existent is a no-op")
        void deleteNonExistent() {
            StepVerifier.create(store.delete("no-such-id")).verifyComplete();
        }
    }

    @Nested
    @DisplayName("Concurrent access")
    class ConcurrentAccessTests {

        @Test
        @DisplayName("concurrent appendEvent calls are thread-safe")
        void concurrentAppendEvent() throws Exception {
            store.persist(testExecution("exec-1", ExecutionStatus.RUNNING)).block();

            int threads = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);

            for (int i = 0; i < threads; i++) {
                final int idx = i;
                executor.submit(
                        () -> {
                            try {
                                latch.await();
                                for (int j = 0; j < 5; j++) {
                                    store.appendEvent(
                                                    "exec-1",
                                                    testEvent(
                                                            "ev-" + idx + "-" + j,
                                                            ExecutionEventType.TOOL_CALL_REQUEST))
                                            .block();
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } finally {
                                done.countDown();
                            }
                        });
            }

            latch.countDown(); // release all threads
            done.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            StepVerifier.create(store.recover("exec-1"))
                    .assertNext(loaded -> assertThat(loaded.events()).hasSize(50))
                    .verifyComplete();
        }
    }
}
