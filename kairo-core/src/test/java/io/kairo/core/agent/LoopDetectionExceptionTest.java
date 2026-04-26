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
package io.kairo.core.agent;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.exception.KairoException;
import org.junit.jupiter.api.Test;

class LoopDetectionExceptionTest {

    @Test
    void isKairoException() {
        LoopDetectionException ex = new LoopDetectionException("stuck in loop");
        assertInstanceOf(KairoException.class, ex);
    }

    @Test
    void isRuntimeException() {
        LoopDetectionException ex = new LoopDetectionException("loop detected");
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void messageIsPreserved() {
        LoopDetectionException ex = new LoopDetectionException("tool X called 5 times");
        assertEquals("tool X called 5 times", ex.getMessage());
    }

    @Test
    void canBeCaughtAsKairoException() {
        assertThrows(
                KairoException.class,
                () -> {
                    throw new LoopDetectionException("loop");
                });
    }

    @Test
    void canBeCaughtAsRuntimeException() {
        assertThrows(
                RuntimeException.class,
                () -> {
                    throw new LoopDetectionException("loop");
                });
    }
}
