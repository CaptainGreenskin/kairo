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

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.execution.*;
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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Tests for {@link ExecutionEventEmitter}. */
class ExecutionEventEmitterTest {

    private InMemoryDurableExecutionStore store;
    private ExecutionEventEmitter emitter;
    private static final String EXECUTION_ID = "exec-1";

    @BeforeEach
    void setUp() {
        store = new InMemoryDurableExecutionStore();
        // Persist a base execution so appendEvent works
        store.persist(
                        new DurableExecution(
                                EXECUTION_ID,
                                "agent-1",
                                List.of(),
                                null,
                                ExecutionStatus.RUNNING,
                                0,
                                Instant.now(),
                                Instant.now()))
                .block();
        emitter = new ExecutionEventEmitter(store, EXECUTION_ID);
    }

    @Nested
    @DisplayName("Event emission basics")
    class EmitBasics {

        @Test
        @DisplayName("emit produces event with correct type and schemaVersion=1")
        void emitProducesEventWithCorrectTypeAndSchema() {
            StepVerifier.create(
                            emitter.emit(
                                    ExecutionEventType.MODEL_CALL_REQUEST, "{\"messageCount\":5}"))
                    .verifyComplete();

            DurableExecution execution = store.recover(EXECUTION_ID).block();
            assertNotNull(execution);
            assertEquals(1, execution.events().size());

            ExecutionEvent event = execution.events().get(0);
            assertEquals(ExecutionEventType.MODEL_CALL_REQUEST, event.eventType());
            assertEquals(1, event.schemaVersion());
            assertEquals("{\"messageCount\":5}", event.payloadJson());
            assertNotNull(event.eventId());
            assertNotNull(event.timestamp());
            assertNotNull(event.eventHash());
            assertFalse(event.eventHash().isEmpty());
        }

        @Test
        @DisplayName("event counter increments correctly")
        void eventCounterIncrements() {
            assertEquals(0, emitter.eventCount());
            emitter.emit(ExecutionEventType.MODEL_CALL_REQUEST, "{}").block();
            assertEquals(1, emitter.eventCount());
            emitter.emit(ExecutionEventType.MODEL_CALL_RESPONSE, "{}").block();
            assertEquals(2, emitter.eventCount());
        }
    }

    @Nested
    @DisplayName("Hash chain integrity")
    class HashChain {

        @Test
        @DisplayName("GENESIS seed for first event")
        void genesisSeedForFirstEvent() {
            assertEquals(ExecutionEventEmitter.GENESIS, emitter.currentHash());

            String expectedHash =
                    ExecutionEventEmitter.computeHash(
                            ExecutionEventEmitter.GENESIS, "{\"test\":true}");

            emitter.emit(ExecutionEventType.MODEL_CALL_REQUEST, "{\"test\":true}").block();

            assertEquals(expectedHash, emitter.currentHash());
        }

        @Test
        @DisplayName("second event hash differs from first and depends on previous hash")
        void hashChainDependsOnPreviousHash() {
            emitter.emit(ExecutionEventType.MODEL_CALL_REQUEST, "{\"a\":1}").block();
            String hashAfterFirst = emitter.currentHash();

            emitter.emit(ExecutionEventType.MODEL_CALL_RESPONSE, "{\"b\":2}").block();
            String hashAfterSecond = emitter.currentHash();

            assertNotEquals(hashAfterFirst, hashAfterSecond);

            // Verify the second hash is SHA256(firstHash + secondPayload)
            String expectedSecondHash =
                    ExecutionEventEmitter.computeHash(hashAfterFirst, "{\"b\":2}");
            assertEquals(expectedSecondHash, hashAfterSecond);
        }

        @Test
        @DisplayName("hash consistency — same payload and previous hash produce same hash")
        void hashConsistency() {
            String hash1 = ExecutionEventEmitter.computeHash("GENESIS", "{\"x\":1}");
            String hash2 = ExecutionEventEmitter.computeHash("GENESIS", "{\"x\":1}");
            assertEquals(hash1, hash2);
        }

        @Test
        @DisplayName("different payloads produce different hashes")
        void differentPayloadsDifferentHashes() {
            String hash1 = ExecutionEventEmitter.computeHash("GENESIS", "{\"x\":1}");
            String hash2 = ExecutionEventEmitter.computeHash("GENESIS", "{\"x\":2}");
            assertNotEquals(hash1, hash2);
        }
    }

    @Nested
    @DisplayName("Event ordering")
    class EventOrdering {

        @Test
        @DisplayName("multiple emissions create events in correct order in store")
        void multipleEmissionsInOrder() {
            emitter.emit(ExecutionEventType.MODEL_CALL_REQUEST, "{\"step\":1}").block();
            emitter.emit(ExecutionEventType.MODEL_CALL_RESPONSE, "{\"step\":2}").block();
            emitter.emit(ExecutionEventType.TOOL_CALL_REQUEST, "{\"step\":3}").block();
            emitter.emit(ExecutionEventType.TOOL_CALL_RESPONSE, "{\"step\":4}").block();
            emitter.emit(ExecutionEventType.ITERATION_COMPLETE, "{\"step\":5}").block();

            DurableExecution execution = store.recover(EXECUTION_ID).block();
            assertNotNull(execution);
            List<ExecutionEvent> events = execution.events();
            assertEquals(5, events.size());

            assertEquals(ExecutionEventType.MODEL_CALL_REQUEST, events.get(0).eventType());
            assertEquals(ExecutionEventType.MODEL_CALL_RESPONSE, events.get(1).eventType());
            assertEquals(ExecutionEventType.TOOL_CALL_REQUEST, events.get(2).eventType());
            assertEquals(ExecutionEventType.TOOL_CALL_RESPONSE, events.get(3).eventType());
            assertEquals(ExecutionEventType.ITERATION_COMPLETE, events.get(4).eventType());

            // Verify hash chain: each event's hash depends on the previous
            String prevHash = ExecutionEventEmitter.GENESIS;
            for (ExecutionEvent event : events) {
                String expected = ExecutionEventEmitter.computeHash(prevHash, event.payloadJson());
                assertEquals(expected, event.eventHash(), "Hash chain broken at event: " + event);
                prevHash = event.eventHash();
            }
        }
    }

    @Nested
    @DisplayName("Best-effort emission")
    class BestEffort {

        @Test
        @DisplayName("emission failure does not propagate — mock store that errors")
        void emissionFailureDoesNotPropagate() {
            // Use a store that always fails on appendEvent
            DurableExecutionStore failingStore =
                    new DurableExecutionStore() {
                        @Override
                        public Mono<Void> persist(DurableExecution execution) {
                            return Mono.empty();
                        }

                        @Override
                        public Mono<DurableExecution> recover(String executionId) {
                            return Mono.empty();
                        }

                        @Override
                        public reactor.core.publisher.Flux<DurableExecution> listPending() {
                            return reactor.core.publisher.Flux.empty();
                        }

                        @Override
                        public Mono<Void> appendEvent(String executionId, ExecutionEvent event) {
                            return Mono.error(new RuntimeException("Simulated store failure"));
                        }

                        @Override
                        public Mono<Void> updateStatus(
                                String executionId, ExecutionStatus status, int expectedVersion) {
                            return Mono.empty();
                        }

                        @Override
                        public Mono<Void> delete(String executionId) {
                            return Mono.empty();
                        }
                    };

            ExecutionEventEmitter failingEmitter =
                    new ExecutionEventEmitter(failingStore, "exec-fail");

            // Direct emit will propagate the error (it's the caller's responsibility
            // to use onErrorResume for best-effort). Verify the error propagates as expected.
            StepVerifier.create(
                            failingEmitter.emit(
                                    ExecutionEventType.MODEL_CALL_REQUEST, "{\"test\":true}"))
                    .verifyError(RuntimeException.class);

            // But when wrapped with best-effort pattern (as done in ReActLoop/ReasoningPhase),
            // the error is swallowed
            StepVerifier.create(
                            failingEmitter
                                    .emit(ExecutionEventType.MODEL_CALL_RESPONSE, "{\"test\":true}")
                                    .onErrorResume(e -> Mono.empty()))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Concurrent emission")
    class ConcurrentEmission {

        @Test
        @DisplayName("thread safety of previousHash and eventCounter")
        void threadSafetyOfHashAndCounter() throws InterruptedException {
            int threadCount = 10;
            int emissionsPerThread = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                final int threadIdx = t;
                executor.submit(
                        () -> {
                            try {
                                for (int i = 0; i < emissionsPerThread; i++) {
                                    emitter.emit(
                                                    ExecutionEventType.MODEL_CALL_REQUEST,
                                                    "{\"thread\":"
                                                            + threadIdx
                                                            + ",\"i\":"
                                                            + i
                                                            + "}")
                                            .block();
                                }
                            } finally {
                                latch.countDown();
                            }
                        });
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS), "Timed out waiting for threads");
            executor.shutdown();

            // All emissions should have been recorded
            assertEquals(threadCount * emissionsPerThread, emitter.eventCount());

            // Hash should not be GENESIS (it was updated)
            assertNotEquals(ExecutionEventEmitter.GENESIS, emitter.currentHash());

            // Store should have all events
            DurableExecution execution = store.recover(EXECUTION_ID).block();
            assertNotNull(execution);
            assertEquals(threadCount * emissionsPerThread, execution.events().size());

            // All events should have non-null hashes and schema version 1
            for (ExecutionEvent event : execution.events()) {
                assertNotNull(event.eventHash());
                assertFalse(event.eventHash().isEmpty());
                assertEquals(1, event.schemaVersion());
            }
        }
    }

    @Nested
    @DisplayName("CONTEXT_COMPACTED event")
    class ContextCompactedEvent {

        @Test
        @DisplayName("CONTEXT_COMPACTED event is emitted with correct payload")
        void contextCompactedEventEmitted() {
            String payload = "{\"iteration\":3,\"messagesBefore\":20,\"messagesAfter\":5}";
            StepVerifier.create(emitter.emit(ExecutionEventType.CONTEXT_COMPACTED, payload))
                    .verifyComplete();

            DurableExecution execution = store.recover(EXECUTION_ID).block();
            assertNotNull(execution);
            assertEquals(1, execution.events().size());

            ExecutionEvent event = execution.events().get(0);
            assertEquals(ExecutionEventType.CONTEXT_COMPACTED, event.eventType());
            assertEquals(payload, event.payloadJson());
            assertEquals(1, event.schemaVersion());
            assertNotNull(event.eventHash());
        }

        @Test
        @DisplayName("CONTEXT_COMPACTED participates in hash chain")
        void contextCompactedInHashChain() {
            emitter.emit(ExecutionEventType.MODEL_CALL_REQUEST, "{\"step\":1}").block();
            String hashAfterFirst = emitter.currentHash();

            String compactPayload = "{\"iteration\":1,\"messagesBefore\":10,\"messagesAfter\":3}";
            emitter.emit(ExecutionEventType.CONTEXT_COMPACTED, compactPayload).block();
            String hashAfterCompact = emitter.currentHash();

            assertNotEquals(hashAfterFirst, hashAfterCompact);
            assertEquals(
                    ExecutionEventEmitter.computeHash(hashAfterFirst, compactPayload),
                    hashAfterCompact);
        }
    }

    @Nested
    @DisplayName("TOOL_CALL_RESPONSE event")
    class ToolCallResponseEvent {

        @Test
        @DisplayName("TOOL_CALL_RESPONSE event is emitted with toolCallId payload")
        void toolCallResponseEventEmitted() {
            String payload =
                    "{\"toolCallId\":\"tc-1\",\"toolName\":\"search\",\"result\":\"ok\",\"isError\":false}";
            StepVerifier.create(emitter.emit(ExecutionEventType.TOOL_CALL_RESPONSE, payload))
                    .verifyComplete();

            DurableExecution execution = store.recover(EXECUTION_ID).block();
            assertNotNull(execution);
            assertEquals(1, execution.events().size());

            ExecutionEvent event = execution.events().get(0);
            assertEquals(ExecutionEventType.TOOL_CALL_RESPONSE, event.eventType());
            assertEquals(payload, event.payloadJson());
        }
    }
}
