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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HashChainViolationExceptionTest {

    @Test
    void isRuntimeException() {
        HashChainViolationException ex =
                new HashChainViolationException("evt-1", "abc123", "xyz789");
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void fieldAccessorsReturnConstructorValues() {
        HashChainViolationException ex =
                new HashChainViolationException("evt-2", "expected-hash", "actual-hash");
        assertEquals("evt-2", ex.eventId());
        assertEquals("expected-hash", ex.expectedHash());
        assertEquals("actual-hash", ex.actualHash());
    }

    @Test
    void messageContainsEventId() {
        HashChainViolationException ex = new HashChainViolationException("event-42", "aaa", "bbb");
        assertTrue(ex.getMessage().contains("event-42"));
    }

    @Test
    void messageContainsExpectedHash() {
        HashChainViolationException ex =
                new HashChainViolationException("evt", "hash-expected", "hash-actual");
        assertTrue(ex.getMessage().contains("hash-expected"));
    }

    @Test
    void messageContainsActualHash() {
        HashChainViolationException ex =
                new HashChainViolationException("evt", "hash-expected", "hash-actual");
        assertTrue(ex.getMessage().contains("hash-actual"));
    }

    @Test
    void canBeCaughtAsRuntimeException() {
        assertThrows(
                RuntimeException.class,
                () -> {
                    throw new HashChainViolationException("evt-3", "e", "a");
                });
    }
}
