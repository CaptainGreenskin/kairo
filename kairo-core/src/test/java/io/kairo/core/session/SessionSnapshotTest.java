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

    private static final String SESSION_ID = "sess-abc-123";
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final int TURN_COUNT = 5;
    private static final List<Map<String, Object>> MESSAGES =
            List.of(Map.of("role", "user", "content", "hello"));
    private static final Map<String, Object> AGENT_STATE = Map.of("planMode", true);

    private SessionSnapshot snapshot() {
        return new SessionSnapshot(SESSION_ID, CREATED_AT, TURN_COUNT, MESSAGES, AGENT_STATE);
    }

    @Test
    void sessionIdAccessor() {
        assertThat(snapshot().sessionId()).isEqualTo(SESSION_ID);
    }

    @Test
    void createdAtAccessor() {
        assertThat(snapshot().createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void turnCountAccessor() {
        assertThat(snapshot().turnCount()).isEqualTo(TURN_COUNT);
    }

    @Test
    void messagesAccessor() {
        assertThat(snapshot().messages()).isEqualTo(MESSAGES);
    }

    @Test
    void agentStateAccessor() {
        assertThat(snapshot().agentState()).isEqualTo(AGENT_STATE);
    }

    @Test
    void equalSnapshotsAreEqual() {
        assertThat(snapshot()).isEqualTo(snapshot());
    }

    @Test
    void differentSessionIdNotEqual() {
        SessionSnapshot a = snapshot();
        SessionSnapshot b =
                new SessionSnapshot("other-id", CREATED_AT, TURN_COUNT, MESSAGES, AGENT_STATE);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void hashCodeConsistentWithEquals() {
        assertThat(snapshot().hashCode()).isEqualTo(snapshot().hashCode());
    }

    @Test
    void toStringContainsSessionId() {
        assertThat(snapshot().toString()).contains(SESSION_ID);
    }

    @Test
    void emptyMessagesAndState() {
        SessionSnapshot s = new SessionSnapshot("s1", CREATED_AT, 0, List.of(), Map.of());
        assertThat(s.messages()).isEmpty();
        assertThat(s.agentState()).isEmpty();
        assertThat(s.turnCount()).isZero();
    }
}
