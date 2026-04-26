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
import java.util.Set;
import org.junit.jupiter.api.Test;

class MemoryApiTest {

    @Test
    void memoryEntryFields() {
        Instant now = Instant.now();
        MemoryEntry entry =
                new MemoryEntry(
                        "m-1",
                        null,
                        "user prefers dark mode",
                        null,
                        MemoryScope.GLOBAL,
                        0.8,
                        null,
                        Set.of("preference", "ui"),
                        now,
                        null);

        assertEquals("m-1", entry.id());
        assertNull(entry.agentId());
        assertEquals("user prefers dark mode", entry.content());
        assertEquals(MemoryScope.GLOBAL, entry.scope());
        assertEquals(now, entry.timestamp());
        assertEquals(Set.of("preference", "ui"), entry.tags());
        assertEquals(0.8, entry.importance());
    }

    @Test
    void memoryEntrySessionFactory() {
        MemoryEntry entry = MemoryEntry.session("m-2", "summary", Set.of("tag1"));
        assertEquals(MemoryScope.SESSION, entry.scope());
        assertEquals(0.5, entry.importance());
    }

    @Test
    void memoryScopeValues() {
        MemoryScope[] values = MemoryScope.values();
        assertEquals(3, values.length);
        assertNotNull(MemoryScope.valueOf("SESSION"));
        assertNotNull(MemoryScope.valueOf("AGENT"));
        assertNotNull(MemoryScope.valueOf("GLOBAL"));
    }
}
