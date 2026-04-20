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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemoryQueryTest {

    @Test
    @DisplayName("Builder creates query with defaults")
    void builderDefaults() {
        MemoryQuery query = MemoryQuery.builder().build();

        assertNull(query.agentId());
        assertNull(query.keyword());
        assertNull(query.queryVector());
        assertNull(query.from());
        assertNull(query.to());
        assertEquals(Set.of(), query.tags());
        assertEquals(0.0, query.minImportance());
        assertEquals(20, query.limit());
    }

    @Test
    @DisplayName("Builder sets all fields")
    void builderSetsAllFields() {
        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-12-31T23:59:59Z");
        float[] vector = new float[] {0.1f, 0.2f, 0.3f};

        MemoryQuery query =
                MemoryQuery.builder()
                        .agentId("agent-1")
                        .keyword("java")
                        .queryVector(vector)
                        .from(from)
                        .to(to)
                        .tags(Set.of("tag1", "tag2"))
                        .minImportance(0.5)
                        .limit(10)
                        .build();

        assertEquals("agent-1", query.agentId());
        assertEquals("java", query.keyword());
        assertArrayEquals(vector, query.queryVector());
        assertEquals(from, query.from());
        assertEquals(to, query.to());
        assertEquals(Set.of("tag1", "tag2"), query.tags());
        assertEquals(0.5, query.minImportance());
        assertEquals(10, query.limit());
    }

    @Test
    @DisplayName("Null tags defaults to empty set")
    void nullTagsDefaultsToEmptySet() {
        MemoryQuery query = new MemoryQuery(null, null, null, null, null, null, 0.0, 20);
        assertEquals(Set.of(), query.tags());
    }

    @Test
    @DisplayName("Invalid limit defaults to 20")
    void invalidLimitDefaults() {
        MemoryQuery query = new MemoryQuery(null, null, null, null, null, null, 0.0, 0);
        assertEquals(20, query.limit());

        MemoryQuery query2 = new MemoryQuery(null, null, null, null, null, null, 0.0, -5);
        assertEquals(20, query2.limit());
    }

    @Test
    @DisplayName("Negative minImportance defaults to 0.0")
    void negativeMinImportanceDefaults() {
        MemoryQuery query = new MemoryQuery(null, null, null, null, null, null, -1.0, 20);
        assertEquals(0.0, query.minImportance());
    }

    @Test
    @DisplayName("Tags are defensively copied")
    void tagsDefensivelyCopied() {
        MemoryQuery query = MemoryQuery.builder().tags(Set.of("a", "b")).build();
        // The Set returned should be unmodifiable
        assertThrows(UnsupportedOperationException.class, () -> query.tags().add("c"));
    }
}
