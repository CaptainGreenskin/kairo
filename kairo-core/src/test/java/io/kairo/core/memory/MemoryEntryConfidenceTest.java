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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemoryEntryConfidenceTest {

    private MemoryEntry entry(double importance) {
        return new MemoryEntry(
                "id1",
                null,
                "content",
                null,
                MemoryScope.SESSION,
                importance,
                null,
                null,
                Instant.now(),
                null);
    }

    @Test
    @DisplayName("Valid importance 0.5")
    void validImportance() {
        MemoryEntry e = entry(0.5);
        assertEquals(0.5, e.importance());
    }

    @Test
    @DisplayName("Boundary value 0.0")
    void boundaryZero() {
        MemoryEntry e = entry(0.0);
        assertEquals(0.0, e.importance());
    }

    @Test
    @DisplayName("Boundary value 1.0")
    void boundaryOne() {
        MemoryEntry e = entry(1.0);
        assertEquals(1.0, e.importance());
    }

    @Test
    @DisplayName("Importance below 0.0 throws IllegalArgumentException")
    void importanceBelowZeroThrows() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> entry(-0.1));
        assertTrue(ex.getMessage().contains("-0.1"));
    }

    @Test
    @DisplayName("Importance above 1.0 throws IllegalArgumentException")
    void importanceAboveOneThrows() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> entry(1.1));
        assertTrue(ex.getMessage().contains("1.1"));
    }
}
