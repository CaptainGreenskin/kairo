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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class InMemoryStoreTest {

    private InMemoryStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
    }

    private MemoryEntry entry(String id, String content, MemoryScope scope, List<String> tags) {
        return new MemoryEntry(id, content, scope, Instant.now(), tags, true);
    }

    @Test
    @DisplayName("Save and retrieve a memory entry")
    void testSaveAndGet() {
        MemoryEntry e = entry("e1", "test content", MemoryScope.SESSION, List.of("tag1"));

        StepVerifier.create(store.save(e))
                .assertNext(saved -> assertEquals("e1", saved.id()))
                .verifyComplete();

        StepVerifier.create(store.get("e1"))
                .assertNext(found -> assertEquals("test content", found.content()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Get non-existent entry returns empty")
    void testGetNonExistent() {
        StepVerifier.create(store.get("nonexistent")).verifyComplete();
    }

    @Test
    @DisplayName("Search by content query")
    void testSearchByContent() {
        store.save(entry("e1", "Java programming guide", MemoryScope.PROJECT, List.of())).block();
        store.save(entry("e2", "Python tutorial", MemoryScope.PROJECT, List.of())).block();
        store.save(entry("e3", "Java best practices", MemoryScope.PROJECT, List.of())).block();

        StepVerifier.create(store.search("java", MemoryScope.PROJECT).collectList())
                .assertNext(
                        results -> {
                            assertEquals(2, results.size());
                            assertTrue(results.stream().anyMatch(e -> e.id().equals("e1")));
                            assertTrue(results.stream().anyMatch(e -> e.id().equals("e3")));
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Search by tags")
    void testSearchByTags() {
        store.save(entry("e1", "content A", MemoryScope.SESSION, List.of("java", "coding")))
                .block();
        store.save(entry("e2", "content B", MemoryScope.SESSION, List.of("python"))).block();

        StepVerifier.create(store.search("java", MemoryScope.SESSION).collectList())
                .assertNext(
                        results -> {
                            assertEquals(1, results.size());
                            assertEquals("e1", results.get(0).id());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Search filters by scope")
    void testSearchFiltersByScope() {
        store.save(entry("e1", "shared content", MemoryScope.SESSION, List.of())).block();
        store.save(entry("e2", "shared content", MemoryScope.PROJECT, List.of())).block();

        StepVerifier.create(store.search("shared", MemoryScope.SESSION).collectList())
                .assertNext(results -> assertEquals(1, results.size()))
                .verifyComplete();
    }

    @Test
    @DisplayName("List all entries for a scope, ordered by timestamp descending")
    void testList() {
        MemoryEntry e1 =
                new MemoryEntry(
                        "e1",
                        "first",
                        MemoryScope.PROJECT,
                        Instant.ofEpochSecond(100),
                        List.of(),
                        true);
        MemoryEntry e2 =
                new MemoryEntry(
                        "e2",
                        "second",
                        MemoryScope.PROJECT,
                        Instant.ofEpochSecond(200),
                        List.of(),
                        true);

        store.save(e1).block();
        store.save(e2).block();

        StepVerifier.create(store.list(MemoryScope.PROJECT).collectList())
                .assertNext(
                        results -> {
                            assertEquals(2, results.size());
                            assertEquals("e2", results.get(0).id()); // newer first
                            assertEquals("e1", results.get(1).id());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Update existing entry by saving with same ID")
    void testUpdate() {
        store.save(entry("e1", "original", MemoryScope.SESSION, List.of())).block();
        store.save(entry("e1", "updated", MemoryScope.SESSION, List.of())).block();

        assertEquals(1, store.size());

        StepVerifier.create(store.get("e1"))
                .assertNext(found -> assertEquals("updated", found.content()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Delete entry")
    void testDelete() {
        store.save(entry("e1", "to delete", MemoryScope.SESSION, List.of())).block();
        assertEquals(1, store.size());

        StepVerifier.create(store.delete("e1")).verifyComplete();
        assertEquals(0, store.size());

        StepVerifier.create(store.get("e1")).verifyComplete();
    }

    @Test
    @DisplayName("Delete non-existent entry completes without error")
    void testDeleteNonExistent() {
        StepVerifier.create(store.delete("nonexistent")).verifyComplete();
    }

    @Test
    @DisplayName("Empty store operations return empty results")
    void testEmptyStoreOperations() {
        assertEquals(0, store.size());

        StepVerifier.create(store.list(MemoryScope.SESSION).collectList())
                .assertNext(results -> assertTrue(results.isEmpty()))
                .verifyComplete();

        StepVerifier.create(store.search("anything", MemoryScope.SESSION).collectList())
                .assertNext(results -> assertTrue(results.isEmpty()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Clear removes all entries")
    void testClear() {
        store.save(entry("e1", "a", MemoryScope.SESSION, List.of())).block();
        store.save(entry("e2", "b", MemoryScope.PROJECT, List.of())).block();

        assertEquals(2, store.size());
        store.clear();
        assertEquals(0, store.size());
    }

    @Test
    @DisplayName("Case-insensitive search")
    void testCaseInsensitiveSearch() {
        store.save(entry("e1", "UPPERCASE content", MemoryScope.SESSION, List.of())).block();

        StepVerifier.create(store.search("uppercase", MemoryScope.SESSION).collectList())
                .assertNext(results -> assertEquals(1, results.size()))
                .verifyComplete();
    }
}
