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

import org.junit.jupiter.api.Test;

class HashChainViolationExceptionTest {

    @Test
    void storesEventId() {
        HashChainViolationException ex = new HashChainViolationException("evt-1", "abc", "xyz");
        assertThat(ex.eventId()).isEqualTo("evt-1");
    }

    @Test
    void storesExpectedHash() {
        HashChainViolationException ex = new HashChainViolationException("e", "expected", "actual");
        assertThat(ex.expectedHash()).isEqualTo("expected");
    }

    @Test
    void storesActualHash() {
        HashChainViolationException ex = new HashChainViolationException("e", "expected", "actual");
        assertThat(ex.actualHash()).isEqualTo("actual");
    }

    @Test
    void messageContainsEventId() {
        HashChainViolationException ex = new HashChainViolationException("evt-42", "abc", "xyz");
        assertThat(ex.getMessage()).contains("evt-42");
    }

    @Test
    void isRuntimeException() {
        assertThat(new HashChainViolationException("e", "a", "b"))
                .isInstanceOf(RuntimeException.class);
    }
}
