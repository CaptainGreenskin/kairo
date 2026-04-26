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

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-01-02T00:00:00Z");

    private static SessionMetadata meta() {
        return new SessionMetadata("session-1", T0, T1, 5);
    }

    @Test
    void constructorDoesNotThrow() {
        assertThat(meta()).isNotNull();
    }

    @Test
    void sessionIdPreserved() {
        assertThat(meta().sessionId()).isEqualTo("session-1");
    }

    @Test
    void createdAtPreserved() {
        assertThat(meta().createdAt()).isEqualTo(T0);
    }

    @Test
    void updatedAtPreserved() {
        assertThat(meta().updatedAt()).isEqualTo(T1);
    }

    @Test
    void turnCountPreserved() {
        assertThat(meta().turnCount()).isEqualTo(5);
    }

    @Test
    void equalityViaRecord() {
        assertThat(meta()).isEqualTo(new SessionMetadata("session-1", T0, T1, 5));
    }

    @Test
    void inequalityOnDifferentSessionId() {
        assertThat(meta()).isNotEqualTo(new SessionMetadata("other", T0, T1, 5));
    }

    @Test
    void hashCodeConsistentWithEquals() {
        assertThat(meta().hashCode())
                .isEqualTo(new SessionMetadata("session-1", T0, T1, 5).hashCode());
    }
}
