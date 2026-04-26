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
import org.junit.jupiter.api.Test;

class SessionMetadataTest {

    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-01-02T00:00:00Z");

    @Test
    void storesSessionId() {
        var metadata = new SessionMetadata("session-1", CREATED, UPDATED, 5);
        assertThat(metadata.sessionId()).isEqualTo("session-1");
    }

    @Test
    void storesCreatedAt() {
        var metadata = new SessionMetadata("session-1", CREATED, UPDATED, 5);
        assertThat(metadata.createdAt()).isEqualTo(CREATED);
    }

    @Test
    void storesUpdatedAt() {
        var metadata = new SessionMetadata("session-1", CREATED, UPDATED, 5);
        assertThat(metadata.updatedAt()).isEqualTo(UPDATED);
    }

    @Test
    void storesTurnCount() {
        var metadata = new SessionMetadata("session-1", CREATED, UPDATED, 7);
        assertThat(metadata.turnCount()).isEqualTo(7);
    }

    @Test
    void equalityBasedOnAllFields() {
        var a = new SessionMetadata("s1", CREATED, UPDATED, 3);
        var b = new SessionMetadata("s1", CREATED, UPDATED, 3);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void inequalityWhenFieldDiffers() {
        var a = new SessionMetadata("s1", CREATED, UPDATED, 3);
        var b = new SessionMetadata("s2", CREATED, UPDATED, 3);
        assertThat(a).isNotEqualTo(b);
    }
}
