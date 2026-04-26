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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecoveryResultTest {

    @Test
    void fullConstructorFieldAccessors() {
        RecoveryResult result =
                new RecoveryResult(
                        "exec-1",
                        3,
                        List.of(),
                        "cached-output",
                        false,
                        Map.of("tc-1", "resp-1"),
                        List.of("tc-2"));
        assertEquals("exec-1", result.executionId());
        assertEquals(3, result.resumeFromIteration());
        assertTrue(result.rebuiltHistory().isEmpty());
        assertEquals("cached-output", result.lastToolCallCachedResult());
        assertFalse(result.requiresHumanConfirmation());
        assertEquals(Map.of("tc-1", "resp-1"), result.cachedToolResults());
        assertEquals(List.of("tc-2"), result.interruptedToolCallIds());
    }

    @Test
    void backwardCompatConstructorDefaultsEmptyCollections() {
        RecoveryResult result = new RecoveryResult("exec-2", 0, List.of(), null, true);
        assertTrue(result.cachedToolResults().isEmpty());
        assertTrue(result.interruptedToolCallIds().isEmpty());
    }

    @Test
    void backwardCompatConstructorFieldAccessors() {
        RecoveryResult result = new RecoveryResult("exec-3", 5, List.of(), "hit", false);
        assertEquals("exec-3", result.executionId());
        assertEquals(5, result.resumeFromIteration());
        assertEquals("hit", result.lastToolCallCachedResult());
        assertFalse(result.requiresHumanConfirmation());
    }

    @Test
    void nullLastToolCallCachedResultAllowed() {
        RecoveryResult result = new RecoveryResult("exec-4", 0, List.of(), null, false);
        assertNull(result.lastToolCallCachedResult());
    }

    @Test
    void requiresHumanConfirmationTrue() {
        RecoveryResult result = new RecoveryResult("exec-5", 1, List.of(), null, true);
        assertTrue(result.requiresHumanConfirmation());
    }

    @Test
    void recordEquality() {
        RecoveryResult a = new RecoveryResult("exec-6", 2, List.of(), null, false);
        RecoveryResult b = new RecoveryResult("exec-6", 2, List.of(), null, false);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void inequalityOnDifferentExecutionId() {
        RecoveryResult a = new RecoveryResult("exec-7", 0, List.of(), null, false);
        RecoveryResult b = new RecoveryResult("exec-8", 0, List.of(), null, false);
        assertNotEquals(a, b);
    }

    @Test
    void toStringContainsExecutionId() {
        RecoveryResult result = new RecoveryResult("exec-xyz", 0, List.of(), null, false);
        assertTrue(result.toString().contains("exec-xyz"));
    }
}
