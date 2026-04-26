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
import io.kairo.api.tool.*;
import io.kairo.core.execution.IdempotencyResolver.ReplayStrategy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * End-to-end crash recovery tests.
 *
 * <p>Simulates the full lifecycle: start execution → emit events → simulate crash (stop emitting) →
 * recover → verify state rebuilt correctly.
 */
class CrashRecoveryE2ETest {

    private InMemoryDurableExecutionStore store;
    private RecoveryHandler recoveryHandler;
    private IdempotencyResolver idempotencyResolver;

    private static final String EXEC_ID = "e2e-exec-1";
    private static final String AGENT_ID = "e2e-agent-1";

    @BeforeEach
    void setUp() {
        store = new InMemoryDurableExecutionStore();
        recoveryHandler = new RecoveryHandler(store);
        idempotencyResolver = new IdempotencyResolver();
    }

    @Test
    @DisplayName("Full cycle: start → emit events → simulate crash → recover → verify state")
    void fullCycleRecovery() {
        // Phase 1: Start execution and emit events (simulate normal execution)
        DurableExecution initial =
                new DurableExecution(
                        EXEC_ID,
                        AGENT_ID,
                        List.of(),
                        null,
                        ExecutionStatus.RUNNING,
                        0,
                        Instant.now(),
                        Instant.now());
        store.persist(initial).block();

        ExecutionEventEmitter emitter = new ExecutionEventEmitter(store, EXEC_ID);

        // Iteration 0 — complete
        emitter.emit(ExecutionEventType.MODEL_CALL_REQUEST, "{\"messageCount\":1}").block();
        emitter.emit(
                        ExecutionEventType.MODEL_CALL_RESPONSE,
                        "{\"response\":\"I'll search for that\"}")
                .block();
        emitter.emit(
                        ExecutionEventType.TOOL_CALL_REQUEST,
                        "{\"toolCallId\":\"tc-1\",\"toolName\":\"search\",\"args\":{\"q\":\"test\"}}")
                .block();
        emitter.emit(
                        ExecutionEventType.TOOL_CALL_RESPONSE,
                        "{\"toolCallId\":\"tc-1\",\"result\":\"found 42 results\"}")
                .block();
        emitter.emit(ExecutionEventType.ITERATION_COMPLETE, "{\"iteration\":0}").block();

        // Iteration 1 — complete
        emitter.emit(ExecutionEventType.MODEL_CALL_REQUEST, "{\"messageCount\":3}").block();
        emitter.emit(
                        ExecutionEventType.MODEL_CALL_RESPONSE,
                        "{\"response\":\"Let me process those results\"}")
                .block();
        emitter.emit(ExecutionEventType.ITERATION_COMPLETE, "{\"iteration\":1}").block();

        // Phase 2: "Crash" — execution stops here (no more events emitted)

        // Phase 3: Recovery
        StepVerifier.create(recoveryHandler.recover(EXEC_ID))
                .assertNext(
                        result -> {
                            assertEquals(EXEC_ID, result.executionId());
                            assertEquals(2, result.resumeFromIteration());
                            // Should have rebuilt messages from MODEL_CALL_RESPONSE and
                            // TOOL_CALL_RESPONSE events
                            assertFalse(result.rebuiltHistory().isEmpty());
                            assertNull(result.lastToolCallCachedResult());
                            assertFalse(result.requiresHumanConfirmation());
                        })
                .verifyComplete();

        // Verify status transitioned to RECOVERING
        DurableExecution recovered = store.recover(EXEC_ID).block();
        assertNotNull(recovered);
        assertEquals(ExecutionStatus.RECOVERING, recovered.status());
    }

    @Test
    @DisplayName("Idempotent tool is flagged for REPLAY on recovery")
    void idempotentToolReplayedOnRecovery() {
        @Idempotent("safe lookup")
        class SearchTool implements ToolHandler {
            @Override
            public ToolResult execute(Map<String, Object> input) {
                return new ToolResult("t1", "search result", false, Map.of());
            }
        }

        ReplayStrategy strategy = idempotencyResolver.resolveStrategy(new SearchTool());
        assertEquals(ReplayStrategy.REPLAY, strategy);

        // Also verify the idempotency key is generated correctly for the recovery coordinates
        String key = idempotencyResolver.generateKey(EXEC_ID, 2, 0);
        assertNotNull(key);
        assertEquals(32, key.length());
    }

    @Test
    @DisplayName("Non-idempotent tool returns cached result on recovery")
    void nonIdempotentToolReturnsCachedOnRecovery() {
        // Set up execution with a non-idempotent tool call that completed before crash
        DurableExecution initial =
                new DurableExecution(
                        EXEC_ID,
                        AGENT_ID,
                        List.of(),
                        null,
                        ExecutionStatus.RUNNING,
                        0,
                        Instant.now(),
                        Instant.now());
        store.persist(initial).block();

        ExecutionEventEmitter emitter = new ExecutionEventEmitter(store, EXEC_ID);

        // Iteration 0 — complete
        emitter.emit(ExecutionEventType.MODEL_CALL_REQUEST, "{\"messageCount\":1}").block();
        emitter.emit(ExecutionEventType.MODEL_CALL_RESPONSE, "{\"response\":\"sending email\"}")
                .block();
        emitter.emit(ExecutionEventType.ITERATION_COMPLETE, "{\"iteration\":0}").block();

        // Iteration 1 — tool called but crash happened after response was logged
        emitter.emit(ExecutionEventType.MODEL_CALL_REQUEST, "{\"messageCount\":2}").block();
        emitter.emit(
                        ExecutionEventType.MODEL_CALL_RESPONSE,
                        "{\"response\":\"calling send_email\"}")
                .block();
        emitter.emit(
                        ExecutionEventType.TOOL_CALL_REQUEST,
                        "{\"toolCallId\":\"tc-email\",\"toolName\":\"send_email\",\"args\":{\"to\":\"user@test.com\"}}")
                .block();
        emitter.emit(
                        ExecutionEventType.TOOL_CALL_RESPONSE,
                        "{\"toolCallId\":\"tc-email\",\"result\":\"email sent to user@test.com\"}")
                .block();
        // Crash before ITERATION_COMPLETE

        // Verify the @NonIdempotent tool should use CACHED strategy
        @NonIdempotent("sends email")
        class SendEmailTool implements ToolHandler {
            @Override
            public ToolResult execute(Map<String, Object> input) {
                return new ToolResult("t1", "email sent", false, Map.of());
            }
        }
        assertEquals(
                ReplayStrategy.CACHED, idempotencyResolver.resolveStrategy(new SendEmailTool()));

        // Recovery should find the cached tool result
        StepVerifier.create(recoveryHandler.recover(EXEC_ID))
                .assertNext(
                        result -> {
                            assertEquals(1, result.resumeFromIteration());
                            // The TOOL_CALL_RESPONSE after the last ITERATION_COMPLETE is cached
                            assertNotNull(result.lastToolCallCachedResult());
                            assertTrue(
                                    result.lastToolCallCachedResult().contains("email sent"),
                                    "Cached result should contain the tool response");
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Unannotated tool defaults to cached result on recovery")
    void unannotatedToolDefaultsToCachedOnRecovery() {
        // An unannotated tool should behave the same as @NonIdempotent
        class MysteryTool implements ToolHandler {
            @Override
            public ToolResult execute(Map<String, Object> input) {
                return new ToolResult("t1", "mystery result", false, Map.of());
            }
        }

        assertEquals(
                ReplayStrategy.CACHED,
                idempotencyResolver.resolveStrategy(new MysteryTool()),
                "Unannotated tools should default to CACHED (safe default)");

        // Set up execution with the unannotated tool's result cached
        DurableExecution initial =
                new DurableExecution(
                        EXEC_ID,
                        AGENT_ID,
                        List.of(),
                        null,
                        ExecutionStatus.RUNNING,
                        0,
                        Instant.now(),
                        Instant.now());
        store.persist(initial).block();

        ExecutionEventEmitter emitter = new ExecutionEventEmitter(store, EXEC_ID);
        emitter.emit(ExecutionEventType.MODEL_CALL_REQUEST, "{\"messageCount\":1}").block();
        emitter.emit(ExecutionEventType.MODEL_CALL_RESPONSE, "{\"response\":\"running mystery\"}")
                .block();
        emitter.emit(
                        ExecutionEventType.TOOL_CALL_REQUEST,
                        "{\"toolCallId\":\"tc-mystery\",\"toolName\":\"mystery\"}")
                .block();
        emitter.emit(
                        ExecutionEventType.TOOL_CALL_RESPONSE,
                        "{\"toolCallId\":\"tc-mystery\",\"result\":\"mystery output\"}")
                .block();
        // No ITERATION_COMPLETE — simulates crash mid-iteration

        StepVerifier.create(recoveryHandler.recover(EXEC_ID))
                .assertNext(
                        result -> {
                            assertEquals(0, result.resumeFromIteration());
                            assertNotNull(result.lastToolCallCachedResult());
                            assertTrue(
                                    result.lastToolCallCachedResult().contains("mystery output"));
                        })
                .verifyComplete();
    }
}
