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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/** Tests for {@link RecoveryHandler}. */
class RecoveryHandlerTest {

    private InMemoryDurableExecutionStore store;
    private RecoveryHandler handler;
    private ExecutionEventEmitter emitter;

    private static final String EXEC_ID = "exec-recovery-1";
    private static final String AGENT_ID = "agent-1";

    @BeforeEach
    void setUp() {
        store = new InMemoryDurableExecutionStore();
        handler = new RecoveryHandler(store);
    }

    private DurableExecution createExecution(String id, ExecutionStatus status) {
        return new DurableExecution(
                id, AGENT_ID, List.of(), null, status, 0, Instant.now(), Instant.now());
    }

    private void persistAndEmitEvents(String execId, int iterationCount) {
        store.persist(createExecution(execId, ExecutionStatus.RUNNING)).block();
        emitter = new ExecutionEventEmitter(store, execId);
        for (int i = 0; i < iterationCount; i++) {
            emitter.emit(
                            ExecutionEventType.MODEL_CALL_REQUEST,
                            "{\"messageCount\":" + (i + 1) + "}")
                    .block();
            emitter.emit(
                            ExecutionEventType.MODEL_CALL_RESPONSE,
                            "{\"response\":\"iteration " + i + "\"}")
                    .block();
            emitter.emit(ExecutionEventType.ITERATION_COMPLETE, "{\"iteration\":" + i + "}")
                    .block();
        }
    }

    @Nested
    @DisplayName("recover()")
    class Recover {

        @Test
        @DisplayName("finds latest ITERATION_COMPLETE and rebuilds from there")
        void findsLatestIterationCompleteAndRebuilds() {
            persistAndEmitEvents(EXEC_ID, 3);

            StepVerifier.create(handler.recover(EXEC_ID))
                    .assertNext(
                            result -> {
                                assertEquals(EXEC_ID, result.executionId());
                                assertEquals(3, result.resumeFromIteration());
                                // MODEL_CALL_RESPONSE events become conversation messages
                                assertFalse(result.rebuiltHistory().isEmpty());
                                assertNull(result.lastToolCallCachedResult());
                                assertFalse(result.requiresHumanConfirmation());
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("no ITERATION_COMPLETE events — starts from beginning")
        void noIterationCompleteStartsFromBeginning() {
            store.persist(createExecution(EXEC_ID, ExecutionStatus.RUNNING)).block();
            // Emit only MODEL events, no ITERATION_COMPLETE
            emitter = new ExecutionEventEmitter(store, EXEC_ID);
            emitter.emit(ExecutionEventType.MODEL_CALL_REQUEST, "{\"msg\":1}").block();
            emitter.emit(ExecutionEventType.MODEL_CALL_RESPONSE, "{\"resp\":\"hi\"}").block();

            StepVerifier.create(handler.recover(EXEC_ID))
                    .assertNext(
                            result -> {
                                assertEquals(0, result.resumeFromIteration());
                                // The MODEL_CALL_RESPONSE should still produce a rebuilt message
                                assertFalse(result.rebuiltHistory().isEmpty());
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("status updated to RECOVERING during recovery")
        void statusUpdatedToRecovering() {
            store.persist(createExecution(EXEC_ID, ExecutionStatus.RUNNING)).block();

            handler.recover(EXEC_ID).block();

            DurableExecution exec = store.recover(EXEC_ID).block();
            assertNotNull(exec);
            assertEquals(ExecutionStatus.RECOVERING, exec.status());
        }

        @Test
        @DisplayName("non-existent execution returns empty")
        void nonExistentReturnsEmpty() {
            StepVerifier.create(handler.recover("non-existent")).verifyComplete();
        }

        @Test
        @DisplayName("execution already COMPLETED returns empty")
        void completedReturnsEmpty() {
            store.persist(createExecution(EXEC_ID, ExecutionStatus.COMPLETED)).block();

            StepVerifier.create(handler.recover(EXEC_ID)).verifyComplete();
        }
    }

    @Nested
    @DisplayName("recoverAllPending()")
    class RecoverAllPending {

        @Test
        @DisplayName("recovers multiple pending executions")
        void recoversMultiplePending() {
            persistAndEmitEvents("exec-a", 1);
            persistAndEmitEvents("exec-b", 2);
            // Also add a COMPLETED one that should be skipped
            store.persist(createExecution("exec-c", ExecutionStatus.COMPLETED)).block();

            StepVerifier.create(handler.recoverAllPending().collectList())
                    .assertNext(
                            results -> {
                                assertEquals(2, results.size());
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("individual failure does not stop others")
        void individualFailureDoesNotStopOthers() {
            // Create two RUNNING executions
            persistAndEmitEvents("exec-ok", 1);

            // Create a problematic execution that will cause a version mismatch
            store.persist(createExecution("exec-bad", ExecutionStatus.RUNNING)).block();
            // Update its version so the recovery's updateStatus will fail
            store.updateStatus("exec-bad", ExecutionStatus.RUNNING, 0).block();
            store.updateStatus("exec-bad", ExecutionStatus.RUNNING, 1).block();

            // Both should be attempted; one may succeed
            StepVerifier.create(handler.recoverAllPending().collectList())
                    .assertNext(results -> assertTrue(results.size() >= 1))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Hash chain verification")
    class HashChainVerification {

        @Test
        @DisplayName("valid hash chain passes — recovery succeeds")
        void validHashChainPasses() {
            persistAndEmitEvents(EXEC_ID, 2);

            StepVerifier.create(handler.recover(EXEC_ID))
                    .assertNext(
                            result -> {
                                assertEquals(EXEC_ID, result.executionId());
                                assertEquals(2, result.resumeFromIteration());
                                assertFalse(result.rebuiltHistory().isEmpty());
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("corrupted payload detected — recovery throws HashChainViolationException")
        void corruptedPayloadDetected() {
            persistAndEmitEvents(EXEC_ID, 2);

            // Tamper with an event's payload after it was hashed
            tamperEventPayload(EXEC_ID, 1, "{\"corrupted\":true}");

            StepVerifier.create(handler.recover(EXEC_ID))
                    .expectError(HashChainViolationException.class)
                    .verify();
        }

        @Test
        @DisplayName("tampered hash detected — recovery throws HashChainViolationException")
        void tamperedHashDetected() {
            persistAndEmitEvents(EXEC_ID, 2);

            // Replace an event's hash with a wrong value
            tamperEventHash(
                    EXEC_ID, 2, "0000000000000000000000000000000000000000000000000000000000000000");

            StepVerifier.create(handler.recover(EXEC_ID))
                    .expectError(HashChainViolationException.class)
                    .verify();
        }

        @Test
        @DisplayName("empty event list — recovery still works")
        void emptyEventListRecoveryWorks() {
            store.persist(createExecution(EXEC_ID, ExecutionStatus.RUNNING)).block();

            StepVerifier.create(handler.recover(EXEC_ID))
                    .assertNext(
                            result -> {
                                assertEquals(EXEC_ID, result.executionId());
                                assertEquals(0, result.resumeFromIteration());
                                assertTrue(result.rebuiltHistory().isEmpty());
                            })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("toolCallId correlation-based matching")
    class ToolCallIdCorrelation {

        @Test
        @DisplayName("multi-tool recovery matches each result to correct request by toolCallId")
        void multiToolRecoveryMatchesCorrectly() {
            store.persist(createExecution(EXEC_ID, ExecutionStatus.RUNNING)).block();
            emitter = new ExecutionEventEmitter(store, EXEC_ID);

            // Iteration 0 — complete
            emitter.emit(ExecutionEventType.MODEL_CALL_REQUEST, "{\"messageCount\":1}").block();
            emitter.emit(ExecutionEventType.MODEL_CALL_RESPONSE, "{\"response\":\"iter0\"}")
                    .block();
            emitter.emit(ExecutionEventType.ITERATION_COMPLETE, "{\"iteration\":0}").block();

            // Iteration 1 — two tool calls, both completed, then crash before ITERATION_COMPLETE
            emitter.emit(ExecutionEventType.MODEL_CALL_REQUEST, "{\"messageCount\":2}").block();
            emitter.emit(ExecutionEventType.MODEL_CALL_RESPONSE, "{\"response\":\"calling tools\"}")
                    .block();
            emitter.emit(
                            ExecutionEventType.TOOL_CALL_REQUEST,
                            "{\"toolCallId\":\"tc-search\",\"toolName\":\"search\"}")
                    .block();
            emitter.emit(
                            ExecutionEventType.TOOL_CALL_RESPONSE,
                            "{\"toolCallId\":\"tc-search\",\"result\":\"42 results\"}")
                    .block();
            emitter.emit(
                            ExecutionEventType.TOOL_CALL_REQUEST,
                            "{\"toolCallId\":\"tc-email\",\"toolName\":\"send_email\"}")
                    .block();
            emitter.emit(
                            ExecutionEventType.TOOL_CALL_RESPONSE,
                            "{\"toolCallId\":\"tc-email\",\"result\":\"email sent\"}")
                    .block();
            // No ITERATION_COMPLETE — crash

            StepVerifier.create(handler.recover(EXEC_ID))
                    .assertNext(
                            result -> {
                                assertEquals(1, result.resumeFromIteration());
                                // Both completed — no interrupted calls
                                assertTrue(result.interruptedToolCallIds().isEmpty());
                                assertFalse(result.requiresHumanConfirmation());
                                // Cached results map has both
                                Map<String, String> cached = result.cachedToolResults();
                                assertEquals(2, cached.size());
                                assertTrue(cached.containsKey("tc-search"));
                                assertTrue(cached.get("tc-search").contains("42 results"));
                                assertTrue(cached.containsKey("tc-email"));
                                assertTrue(cached.get("tc-email").contains("email sent"));
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("partial tool completion — first completed, second interrupted")
        void partialToolCompletion() {
            store.persist(createExecution(EXEC_ID, ExecutionStatus.RUNNING)).block();
            emitter = new ExecutionEventEmitter(store, EXEC_ID);

            // Iteration 0 — complete
            emitter.emit(ExecutionEventType.MODEL_CALL_REQUEST, "{\"messageCount\":1}").block();
            emitter.emit(ExecutionEventType.MODEL_CALL_RESPONSE, "{\"response\":\"iter0\"}")
                    .block();
            emitter.emit(ExecutionEventType.ITERATION_COMPLETE, "{\"iteration\":0}").block();

            // Iteration 1 — first tool completed, second tool interrupted
            emitter.emit(ExecutionEventType.MODEL_CALL_REQUEST, "{\"messageCount\":2}").block();
            emitter.emit(
                            ExecutionEventType.MODEL_CALL_RESPONSE,
                            "{\"response\":\"calling two tools\"}")
                    .block();
            emitter.emit(
                            ExecutionEventType.TOOL_CALL_REQUEST,
                            "{\"toolCallId\":\"tc-done\",\"toolName\":\"search\"}")
                    .block();
            emitter.emit(
                            ExecutionEventType.TOOL_CALL_RESPONSE,
                            "{\"toolCallId\":\"tc-done\",\"result\":\"search results\"}")
                    .block();
            emitter.emit(
                            ExecutionEventType.TOOL_CALL_REQUEST,
                            "{\"toolCallId\":\"tc-interrupted\",\"toolName\":\"process\"}")
                    .block();
            // No TOOL_CALL_RESPONSE for tc-interrupted — crash

            StepVerifier.create(handler.recover(EXEC_ID))
                    .assertNext(
                            result -> {
                                assertEquals(1, result.resumeFromIteration());
                                // One completed, one interrupted
                                assertEquals(1, result.cachedToolResults().size());
                                assertTrue(result.cachedToolResults().containsKey("tc-done"));
                                assertEquals(
                                        List.of("tc-interrupted"), result.interruptedToolCallIds());
                                assertTrue(result.requiresHumanConfirmation());
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("toolCallId mismatch — RESPONSE with unknown toolCallId is logged and ignored")
        void toolCallIdMismatchHandledGracefully() {
            store.persist(createExecution(EXEC_ID, ExecutionStatus.RUNNING)).block();
            emitter = new ExecutionEventEmitter(store, EXEC_ID);

            // Emit a TOOL_CALL_REQUEST and a RESPONSE with a different toolCallId
            emitter.emit(
                            ExecutionEventType.TOOL_CALL_REQUEST,
                            "{\"toolCallId\":\"tc-alpha\",\"toolName\":\"search\"}")
                    .block();
            emitter.emit(
                            ExecutionEventType.TOOL_CALL_RESPONSE,
                            "{\"toolCallId\":\"tc-beta\",\"result\":\"orphan result\"}")
                    .block();

            StepVerifier.create(handler.recover(EXEC_ID))
                    .assertNext(
                            result -> {
                                // tc-alpha has no matching response — it's interrupted
                                assertEquals(List.of("tc-alpha"), result.interruptedToolCallIds());
                                assertTrue(result.requiresHumanConfirmation());
                                // tc-beta response doesn't match any request — not in cached map
                                assertFalse(result.cachedToolResults().containsKey("tc-beta"));
                            })
                    .verifyComplete();
        }
    }

    /**
     * Tamper with an event's payload in the store (keeping its original hash, making it invalid).
     */
    private void tamperEventPayload(String executionId, int eventIndex, String newPayload) {
        DurableExecution exec = store.recover(executionId).block();
        assertNotNull(exec);
        List<ExecutionEvent> events = new ArrayList<>(exec.events());
        ExecutionEvent original = events.get(eventIndex);
        events.set(
                eventIndex,
                new ExecutionEvent(
                        original.eventId(),
                        original.eventType(),
                        original.timestamp(),
                        newPayload,
                        original.eventHash(),
                        original.schemaVersion()));
        store.delete(executionId).block();
        store.persist(
                        new DurableExecution(
                                exec.executionId(),
                                exec.agentId(),
                                events,
                                exec.checkpoint(),
                                exec.status(),
                                exec.version(),
                                exec.createdAt(),
                                exec.updatedAt()))
                .block();
    }

    /** Tamper with an event's hash in the store. */
    private void tamperEventHash(String executionId, int eventIndex, String newHash) {
        DurableExecution exec = store.recover(executionId).block();
        assertNotNull(exec);
        List<ExecutionEvent> events = new ArrayList<>(exec.events());
        ExecutionEvent original = events.get(eventIndex);
        events.set(
                eventIndex,
                new ExecutionEvent(
                        original.eventId(),
                        original.eventType(),
                        original.timestamp(),
                        original.payloadJson(),
                        newHash,
                        original.schemaVersion()));
        store.delete(executionId).block();
        store.persist(
                        new DurableExecution(
                                exec.executionId(),
                                exec.agentId(),
                                events,
                                exec.checkpoint(),
                                exec.status(),
                                exec.version(),
                                exec.createdAt(),
                                exec.updatedAt()))
                .block();
    }
}
