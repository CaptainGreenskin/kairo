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
package io.kairo.core.memory;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.memory.MemoryEntry;
import io.kairo.api.memory.MemoryScope;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemoryEntryConfidenceTest {

    @Test
    @DisplayName("6-arg constructor sets confidence to null")
    void sixArgConstructorSetsConfidenceNull() {
        MemoryEntry entry =
                new MemoryEntry(
                        "id1", "content", MemoryScope.SESSION, Instant.now(), List.of(), true);
        assertNull(entry.confidence());
    }

    @Test
    @DisplayName("7-arg constructor with valid confidence (0.5)")
    void sevenArgConstructorWithValidConfidence() {
        MemoryEntry entry =
                new MemoryEntry(
                        "id1", "content", MemoryScope.SESSION, Instant.now(), List.of(), true, 0.5);
        assertEquals(0.5, entry.confidence());
    }

    @Test
    @DisplayName("7-arg constructor with boundary value 0.0")
    void sevenArgConstructorWithBoundaryZero() {
        MemoryEntry entry =
                new MemoryEntry(
                        "id1", "content", MemoryScope.SESSION, Instant.now(), List.of(), true, 0.0);
        assertEquals(0.0, entry.confidence());
    }

    @Test
    @DisplayName("7-arg constructor with boundary value 1.0")
    void sevenArgConstructorWithBoundaryOne() {
        MemoryEntry entry =
                new MemoryEntry(
                        "id1", "content", MemoryScope.SESSION, Instant.now(), List.of(), true, 1.0);
        assertEquals(1.0, entry.confidence());
    }

    @Test
    @DisplayName("Confidence below 0.0 throws IllegalArgumentException")
    void confidenceBelowZeroThrows() {
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new MemoryEntry(
                                        "id1",
                                        "content",
                                        MemoryScope.SESSION,
                                        Instant.now(),
                                        List.of(),
                                        true,
                                        -0.1));
        assertTrue(ex.getMessage().contains("-0.1"));
    }

    @Test
    @DisplayName("Confidence above 1.0 throws IllegalArgumentException")
    void confidenceAboveOneThrows() {
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new MemoryEntry(
                                        "id1",
                                        "content",
                                        MemoryScope.SESSION,
                                        Instant.now(),
                                        List.of(),
                                        true,
                                        1.1));
        assertTrue(ex.getMessage().contains("1.1"));
    }

    @Test
    @DisplayName("Null confidence does not throw")
    void nullConfidenceDoesNotThrow() {
        assertDoesNotThrow(
                () ->
                        new MemoryEntry(
                                "id1",
                                "content",
                                MemoryScope.SESSION,
                                Instant.now(),
                                List.of(),
                                true,
                                null));
    }
}
