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
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemoryEntryBuilderTest {

    @Test
    @DisplayName("session() creates SESSION-scoped entry with default importance")
    void testSessionFactory() {
        MemoryEntry entry = MemoryEntryBuilder.session("session data");

        assertNotNull(entry.id());
        assertFalse(entry.id().isBlank());
        assertEquals("session data", entry.content());
        assertEquals(MemoryScope.SESSION, entry.scope());
        assertNotNull(entry.timestamp());
        assertEquals(Set.of(), entry.tags());
        assertEquals(0.5, entry.importance());
    }

    @Test
    @DisplayName("agent() creates AGENT-scoped entry with tags")
    void testAgentFactory() {
        MemoryEntry entry = MemoryEntryBuilder.agent("agent data", "java", "kairo");

        assertEquals("agent data", entry.content());
        assertEquals(MemoryScope.AGENT, entry.scope());
        assertEquals(Set.of("java", "kairo"), entry.tags());
        assertEquals(0.5, entry.importance());
    }

    @Test
    @DisplayName("agent() with no tags creates empty tag set")
    void testAgentNoTags() {
        MemoryEntry entry = MemoryEntryBuilder.agent("data");

        assertEquals(Set.of(), entry.tags());
    }

    @Test
    @DisplayName("global() creates GLOBAL-scoped entry with tags")
    void testGlobalFactory() {
        MemoryEntry entry = MemoryEntryBuilder.global("global pref", "preference", "theme");

        assertEquals("global pref", entry.content());
        assertEquals(MemoryScope.GLOBAL, entry.scope());
        assertEquals(Set.of("preference", "theme"), entry.tags());
        assertEquals(0.5, entry.importance());
    }

    @Test
    @DisplayName("create() allows full control over all fields")
    void testCreateFactory() {
        MemoryEntry entry =
                MemoryEntryBuilder.create("custom", MemoryScope.AGENT, Set.of("t1"), 0.9);

        assertEquals("custom", entry.content());
        assertEquals(MemoryScope.AGENT, entry.scope());
        assertEquals(Set.of("t1"), entry.tags());
        assertEquals(0.9, entry.importance());
    }

    @Test
    @DisplayName("Each entry gets a unique ID")
    void testUniqueIds() {
        MemoryEntry e1 = MemoryEntryBuilder.session("a");
        MemoryEntry e2 = MemoryEntryBuilder.session("b");

        assertNotEquals(e1.id(), e2.id());
    }
}
