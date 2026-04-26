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
package io.kairo.core.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class HashChainViolationExceptionTest {

    @Test
    void isRuntimeException() {
        HashChainViolationException ex =
                new HashChainViolationException("evt-1", "abc123", "def456");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void eventIdIsPreserved() {
        HashChainViolationException ex = new HashChainViolationException("event-42", "exp", "act");
        assertThat(ex.eventId()).isEqualTo("event-42");
    }

    @Test
    void expectedHashIsPreserved() {
        HashChainViolationException ex =
                new HashChainViolationException("evt", "expected-hash", "actual-hash");
        assertThat(ex.expectedHash()).isEqualTo("expected-hash");
    }

    @Test
    void actualHashIsPreserved() {
        HashChainViolationException ex =
                new HashChainViolationException("evt", "expected-hash", "actual-hash");
        assertThat(ex.actualHash()).isEqualTo("actual-hash");
    }

    @Test
    void messageContainsAllFields() {
        HashChainViolationException ex = new HashChainViolationException("evt-7", "AAA", "BBB");
        assertThat(ex.getMessage()).contains("evt-7").contains("AAA").contains("BBB");
    }

    @Test
    void canBeThrownAndCaught() {
        assertThatThrownBy(
                        () -> {
                            throw new HashChainViolationException("e1", "h1", "h2");
                        })
                .isInstanceOf(HashChainViolationException.class);
    }

    @Test
    void causeIsNull() {
        HashChainViolationException ex = new HashChainViolationException("e", "a", "b");
        assertThat(ex.getCause()).isNull();
    }
}
