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
package io.kairo.core.session;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SessionSnapshotTest {

    private static final Instant NOW = Instant.parse("2026-04-27T10:00:00Z");

    private static SessionSnapshot snapshot(String sessionId, int turns) {
        List<Map<String, Object>> messages = List.of(Map.of("role", "user", "content", "hello"));
        Map<String, Object> state = Map.of("planMode", false);
        return new SessionSnapshot(sessionId, NOW, turns, messages, state);
    }

    @Test
    void fieldAccessorsReturnConstructorValues() {
        SessionSnapshot snap = snapshot("snap-1", 3);
        assertEquals("snap-1", snap.sessionId());
        assertEquals(NOW, snap.createdAt());
        assertEquals(3, snap.turnCount());
        assertEquals(1, snap.messages().size());
        assertEquals(false, snap.agentState().get("planMode"));
    }

    @Test
    void equalInstancesAreEqual() {
        SessionSnapshot a = snapshot("snap-1", 3);
        SessionSnapshot b = snapshot("snap-1", 3);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentTurnCountIsNotEqual() {
        SessionSnapshot a = snapshot("snap-1", 3);
        SessionSnapshot b = snapshot("snap-1", 4);
        assertNotEquals(a, b);
    }

    @Test
    void differentSessionIdIsNotEqual() {
        SessionSnapshot a = snapshot("snap-1", 3);
        SessionSnapshot b = snapshot("snap-2", 3);
        assertNotEquals(a, b);
    }

    @Test
    void toStringContainsSessionId() {
        SessionSnapshot snap = snapshot("snap-xyz", 1);
        assertTrue(snap.toString().contains("snap-xyz"));
    }

    @Test
    void emptyMessagesAndStateIsValid() {
        SessionSnapshot snap = new SessionSnapshot("snap-empty", NOW, 0, List.of(), Map.of());
        assertTrue(snap.messages().isEmpty());
        assertTrue(snap.agentState().isEmpty());
        assertEquals(0, snap.turnCount());
    }

    @Test
    void multipleMessagesArePreserved() {
        List<Map<String, Object>> messages =
                List.of(
                        Map.of("role", "user", "content", "hi"),
                        Map.of("role", "assistant", "content", "hello"));
        SessionSnapshot snap = new SessionSnapshot("snap-m", NOW, 1, messages, Map.of());
        assertEquals(2, snap.messages().size());
    }
}
