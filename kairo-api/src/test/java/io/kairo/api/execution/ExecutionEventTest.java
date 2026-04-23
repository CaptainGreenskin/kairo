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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link ExecutionEvent} record compact constructor. */
class ExecutionEventTest {

    private static final Instant NOW = Instant.now();

    @Test
    @DisplayName("null eventId throws NullPointerException")
    void nullEventIdThrows() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new ExecutionEvent(
                                null, ExecutionEventType.MODEL_CALL_REQUEST, NOW, "{}", "hash", 1));
    }

    @Test
    @DisplayName("null eventType throws NullPointerException")
    void nullEventTypeThrows() {
        assertThrows(
                NullPointerException.class,
                () -> new ExecutionEvent("evt-1", null, NOW, "{}", "hash", 1));
    }

    @Test
    @DisplayName("null eventHash throws NullPointerException")
    void nullEventHashThrows() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new ExecutionEvent(
                                "evt-1",
                                ExecutionEventType.MODEL_CALL_REQUEST,
                                NOW,
                                "{}",
                                null,
                                1));
    }

    @Test
    @DisplayName("valid construction succeeds")
    void validConstruction() {
        ExecutionEvent event =
                new ExecutionEvent(
                        "evt-1",
                        ExecutionEventType.TOOL_CALL_REQUEST,
                        NOW,
                        "{\"key\":\"val\"}",
                        "sha256hash",
                        2);
        assertEquals("evt-1", event.eventId());
        assertEquals(ExecutionEventType.TOOL_CALL_REQUEST, event.eventType());
        assertEquals("sha256hash", event.eventHash());
        assertEquals(2, event.schemaVersion());
    }
}
