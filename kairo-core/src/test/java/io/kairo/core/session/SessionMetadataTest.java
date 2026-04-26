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

    private static final Instant NOW = Instant.parse("2026-04-27T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-04-27T11:00:00Z");

    @Test
    void fieldAccessorsReturnConstructorValues() {
        SessionMetadata meta = new SessionMetadata("sess-1", NOW, LATER, 5);
        assertEquals("sess-1", meta.sessionId());
        assertEquals(NOW, meta.createdAt());
        assertEquals(LATER, meta.updatedAt());
        assertEquals(5, meta.turnCount());
    }

    @Test
    void equalInstancesAreEqual() {
        SessionMetadata a = new SessionMetadata("sess-1", NOW, LATER, 5);
        SessionMetadata b = new SessionMetadata("sess-1", NOW, LATER, 5);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentTurnCountIsNotEqual() {
        SessionMetadata a = new SessionMetadata("sess-1", NOW, LATER, 5);
        SessionMetadata b = new SessionMetadata("sess-1", NOW, LATER, 6);
        assertNotEquals(a, b);
    }

    @Test
    void differentSessionIdIsNotEqual() {
        SessionMetadata a = new SessionMetadata("sess-1", NOW, LATER, 5);
        SessionMetadata b = new SessionMetadata("sess-2", NOW, LATER, 5);
        assertNotEquals(a, b);
    }

    @Test
    void toStringContainsSessionId() {
        SessionMetadata meta = new SessionMetadata("sess-xyz", NOW, LATER, 3);
        assertTrue(meta.toString().contains("sess-xyz"));
    }

    @Test
    void zeroTurnCountIsValid() {
        SessionMetadata meta = new SessionMetadata("sess-0", NOW, NOW, 0);
        assertEquals(0, meta.turnCount());
    }

    @Test
    void sameObjectIsEqual() {
        SessionMetadata meta = new SessionMetadata("sess-1", NOW, LATER, 5);
        assertEquals(meta, meta);
    }
}
