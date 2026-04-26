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

    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void storesSessionId() {
        var snapshot = new SessionSnapshot("s-1", CREATED, 3, List.of(), Map.of());
        assertThat(snapshot.sessionId()).isEqualTo("s-1");
    }

    @Test
    void storesCreatedAt() {
        var snapshot = new SessionSnapshot("s-1", CREATED, 3, List.of(), Map.of());
        assertThat(snapshot.createdAt()).isEqualTo(CREATED);
    }

    @Test
    void storesTournCount() {
        var snapshot = new SessionSnapshot("s-1", CREATED, 10, List.of(), Map.of());
        assertThat(snapshot.turnCount()).isEqualTo(10);
    }

    @Test
    void storesMessages() {
        var msgs = List.of(Map.<String, Object>of("role", "user", "content", "hello"));
        var snapshot = new SessionSnapshot("s-1", CREATED, 1, msgs, Map.of());
        assertThat(snapshot.messages()).hasSize(1);
        assertThat(snapshot.messages().get(0)).containsEntry("role", "user");
    }

    @Test
    void storesAgentState() {
        var state = Map.<String, Object>of("planMode", true, "iteration", 3);
        var snapshot = new SessionSnapshot("s-1", CREATED, 2, List.of(), state);
        assertThat(snapshot.agentState()).containsEntry("planMode", true);
    }

    @Test
    void equalityBasedOnAllFields() {
        var a = new SessionSnapshot("s-1", CREATED, 3, List.of(), Map.of());
        var b = new SessionSnapshot("s-1", CREATED, 3, List.of(), Map.of());
        assertThat(a).isEqualTo(b);
    }
}
