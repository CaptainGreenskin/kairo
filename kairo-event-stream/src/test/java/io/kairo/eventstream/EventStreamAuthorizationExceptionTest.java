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

import io.kairo.api.Experimental;
import org.junit.jupiter.api.Test;

class EventStreamAuthorizationExceptionTest {

    @Test
    void messageSetFromConstructor() {
        EventStreamAuthorizationException ex =
                new EventStreamAuthorizationException("access denied");
        assertEquals("access denied", ex.getMessage());
    }

    @Test
    void isRuntimeException() {
        assertInstanceOf(RuntimeException.class, new EventStreamAuthorizationException("reason"));
    }

    @Test
    void isAnnotatedExperimental() {
        assertTrue(EventStreamAuthorizationException.class.isAnnotationPresent(Experimental.class));
    }

    @Test
    void canBeThrown() {
        assertThrows(
                EventStreamAuthorizationException.class,
                () -> {
                    throw new EventStreamAuthorizationException("unauthorized");
                });
    }
}
