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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SessionSnapshotTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private static SessionSnapshot snap() {
        return new SessionSnapshot(
                "session-1",
                T0,
                3,
                List.of(Map.of("role", "user", "content", "hi")),
                Map.of("planMode", true));
    }

    @Test
    void constructorDoesNotThrow() {
        assertThat(snap()).isNotNull();
    }

    @Test
    void sessionIdPreserved() {
        assertThat(snap().sessionId()).isEqualTo("session-1");
    }

    @Test
    void createdAtPreserved() {
        assertThat(snap().createdAt()).isEqualTo(T0);
    }

    @Test
    void turnCountPreserved() {
        assertThat(snap().turnCount()).isEqualTo(3);
    }

    @Test
    void messagesPreserved() {
        assertThat(snap().messages()).hasSize(1);
        assertThat(snap().messages().get(0)).containsEntry("role", "user");
    }

    @Test
    void agentStatePreserved() {
        assertThat(snap().agentState()).containsEntry("planMode", true);
    }

    @Test
    void emptyMessagesAllowed() {
        SessionSnapshot s = new SessionSnapshot("s", T0, 0, List.of(), Map.of());
        assertThat(s.messages()).isEmpty();
    }

    @Test
    void equalityViaRecord() {
        SessionSnapshot a = new SessionSnapshot("s", T0, 0, List.of(), Map.of());
        SessionSnapshot b = new SessionSnapshot("s", T0, 0, List.of(), Map.of());
        assertThat(a).isEqualTo(b);
    }

    @Test
    void inequalityOnDifferentTurnCount() {
        SessionSnapshot a = new SessionSnapshot("s", T0, 1, List.of(), Map.of());
        SessionSnapshot b = new SessionSnapshot("s", T0, 2, List.of(), Map.of());
        assertThat(a).isNotEqualTo(b);
    }
}
