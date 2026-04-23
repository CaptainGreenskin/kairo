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

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link DurableExecution} record compact constructor. */
class DurableExecutionTest {

    private static final Instant NOW = Instant.now();

    @Test
    @DisplayName("null executionId throws NullPointerException")
    void nullExecutionIdThrows() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new DurableExecution(
                                null,
                                "agent-1",
                                List.of(),
                                null,
                                ExecutionStatus.RUNNING,
                                0,
                                NOW,
                                NOW));
    }

    @Test
    @DisplayName("null agentId throws NullPointerException")
    void nullAgentIdThrows() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new DurableExecution(
                                "exec-1",
                                null,
                                List.of(),
                                null,
                                ExecutionStatus.RUNNING,
                                0,
                                NOW,
                                NOW));
    }

    @Test
    @DisplayName("null status throws NullPointerException")
    void nullStatusThrows() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new DurableExecution(
                                "exec-1", "agent-1", List.of(), null, null, 0, NOW, NOW));
    }

    @Test
    @DisplayName("null events defaults to empty list")
    void nullEventsDefaultsToEmptyList() {
        DurableExecution exec =
                new DurableExecution(
                        "exec-1", "agent-1", null, null, ExecutionStatus.RUNNING, 0, NOW, NOW);
        assertNotNull(exec.events());
        assertTrue(exec.events().isEmpty());
    }

    @Test
    @DisplayName("events list is defensive-copied")
    void eventsListIsDefensiveCopied() {
        ExecutionEvent event =
                new ExecutionEvent(
                        "evt-1", ExecutionEventType.MODEL_CALL_REQUEST, NOW, "{}", "abc123", 1);
        ArrayList<ExecutionEvent> mutableList = new ArrayList<>();
        mutableList.add(event);

        DurableExecution exec =
                new DurableExecution(
                        "exec-1",
                        "agent-1",
                        mutableList,
                        null,
                        ExecutionStatus.RUNNING,
                        0,
                        NOW,
                        NOW);

        // Mutating original list must not affect the record
        mutableList.clear();
        assertEquals(1, exec.events().size());
    }

    @Test
    @DisplayName("events list in record is unmodifiable")
    void eventsListIsUnmodifiable() {
        DurableExecution exec =
                new DurableExecution(
                        "exec-1", "agent-1", List.of(), null, ExecutionStatus.RUNNING, 0, NOW, NOW);
        assertThrows(UnsupportedOperationException.class, () -> exec.events().add(null));
    }

    @Test
    @DisplayName("valid construction with all fields")
    void validConstruction() {
        DurableExecution exec =
                new DurableExecution(
                        "exec-1",
                        "agent-1",
                        List.of(),
                        "checkpoint-data",
                        ExecutionStatus.COMPLETED,
                        3,
                        NOW,
                        NOW);
        assertEquals("exec-1", exec.executionId());
        assertEquals("agent-1", exec.agentId());
        assertEquals("checkpoint-data", exec.checkpoint());
        assertEquals(ExecutionStatus.COMPLETED, exec.status());
        assertEquals(3, exec.version());
    }
}
