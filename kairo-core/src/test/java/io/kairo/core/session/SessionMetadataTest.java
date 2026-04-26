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
import org.junit.jupiter.api.Test;

class SessionMetadataTest {

    private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-02T00:00:00Z");

    @Test
    void sessionIdAccessor() {
        var meta = new SessionMetadata("abc123", T1, T2, 5);
        assertEquals("abc123", meta.sessionId());
    }

    @Test
    void createdAtAccessor() {
        var meta = new SessionMetadata("id", T1, T2, 0);
        assertEquals(T1, meta.createdAt());
    }

    @Test
    void updatedAtAccessor() {
        var meta = new SessionMetadata("id", T1, T2, 0);
        assertEquals(T2, meta.updatedAt());
    }

    @Test
    void turnCountAccessor() {
        var meta = new SessionMetadata("id", T1, T2, 42);
        assertEquals(42, meta.turnCount());
    }

    @Test
    void equalWhenSameFields() {
        var a = new SessionMetadata("id", T1, T2, 3);
        var b = new SessionMetadata("id", T1, T2, 3);
        assertEquals(a, b);
    }

    @Test
    void notEqualWhenSessionIdDiffers() {
        var a = new SessionMetadata("x", T1, T2, 3);
        var b = new SessionMetadata("y", T1, T2, 3);
        assertNotEquals(a, b);
    }

    @Test
    void notEqualWhenTurnCountDiffers() {
        var a = new SessionMetadata("id", T1, T2, 1);
        var b = new SessionMetadata("id", T1, T2, 2);
        assertNotEquals(a, b);
    }

    @Test
    void toStringContainsSessionId() {
        var meta = new SessionMetadata("session-xyz", T1, T2, 7);
        assertTrue(
                meta.toString().contains("session-xyz"), "Expected sessionId in toString: " + meta);
    }

    @Test
    void hashCodeConsistentWithEquals() {
        var a = new SessionMetadata("id", T1, T2, 3);
        var b = new SessionMetadata("id", T1, T2, 3);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
