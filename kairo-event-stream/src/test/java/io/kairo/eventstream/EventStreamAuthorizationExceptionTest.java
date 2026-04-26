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
package io.kairo.eventstream;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EventStreamAuthorizationExceptionTest {

    @Test
    void isRuntimeException() {
        EventStreamAuthorizationException ex = new EventStreamAuthorizationException("denied");
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void messageIsPreserved() {
        EventStreamAuthorizationException ex =
                new EventStreamAuthorizationException("forbidden topic");
        assertEquals("forbidden topic", ex.getMessage());
    }

    @Test
    void canBeThrownAndCaught() {
        assertThrows(
                EventStreamAuthorizationException.class,
                () -> {
                    throw new EventStreamAuthorizationException("access denied");
                });
    }

    @Test
    void causeIsNull() {
        EventStreamAuthorizationException ex = new EventStreamAuthorizationException("reason");
        assertNull(ex.getCause());
    }
}
