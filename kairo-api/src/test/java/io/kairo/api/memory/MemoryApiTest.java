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
package io.kairo.api.memory;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MemoryApiTest {

    @Test
    void memoryEntryFields() {
        Instant now = Instant.now();
        MemoryEntry entry =
                new MemoryEntry(
                        "m-1",
                        "user prefers dark mode",
                        MemoryScope.USER,
                        now,
                        List.of("preference", "ui"),
                        true);

        assertEquals("m-1", entry.id());
        assertEquals("user prefers dark mode", entry.content());
        assertEquals(MemoryScope.USER, entry.scope());
        assertEquals(now, entry.timestamp());
        assertEquals(List.of("preference", "ui"), entry.tags());
        assertTrue(entry.verbatim());
    }

    @Test
    void memoryEntryNonVerbatim() {
        MemoryEntry entry =
                new MemoryEntry(
                        "m-2", "summary", MemoryScope.SESSION, Instant.now(), List.of(), false);
        assertFalse(entry.verbatim());
    }

    @Test
    void memoryScopeValues() {
        MemoryScope[] values = MemoryScope.values();
        assertEquals(3, values.length);
        assertNotNull(MemoryScope.valueOf("SESSION"));
        assertNotNull(MemoryScope.valueOf("PROJECT"));
        assertNotNull(MemoryScope.valueOf("USER"));
    }
}
