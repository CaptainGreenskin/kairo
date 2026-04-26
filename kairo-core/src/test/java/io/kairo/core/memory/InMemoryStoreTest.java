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
import io.kairo.api.memory.MemoryQuery;
import io.kairo.api.memory.MemoryScope;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    private MemoryEntry entry(String id, String content, MemoryScope scope, Set<String> tags) {
        return new MemoryEntry(
                id, null, content, null, scope, 0.5, null, tags, Instant.now(), null);
    }

    @Test
    @DisplayName("Save and retrieve a memory entry")
    void testSaveAndGet() {
        MemoryEntry e = entry("e1", "test content", MemoryScope.SESSION, Set.of("tag1"));

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
        store.save(entry("e1", "Java programming guide", MemoryScope.AGENT, Set.of())).block();
        store.save(entry("e2", "Python tutorial", MemoryScope.AGENT, Set.of())).block();
        store.save(entry("e3", "Java best practices", MemoryScope.AGENT, Set.of())).block();

        StepVerifier.create(store.search("java", MemoryScope.AGENT).collectList())
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
        store.save(entry("e1", "content A", MemoryScope.SESSION, Set.of("java", "coding"))).block();
        store.save(entry("e2", "content B", MemoryScope.SESSION, Set.of("python"))).block();

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
        store.save(entry("e1", "shared content", MemoryScope.SESSION, Set.of())).block();
        store.save(entry("e2", "shared content", MemoryScope.AGENT, Set.of())).block();

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
                        null,
                        "first",
                        null,
                        MemoryScope.AGENT,
                        0.5,
                        null,
                        Set.of(),
                        Instant.ofEpochSecond(100),
                        null);
        MemoryEntry e2 =
                new MemoryEntry(
                        "e2",
                        null,
                        "second",
                        null,
                        MemoryScope.AGENT,
                        0.5,
                        null,
                        Set.of(),
                        Instant.ofEpochSecond(200),
                        null);

        store.save(e1).block();
        store.save(e2).block();

        StepVerifier.create(store.list(MemoryScope.AGENT).collectList())
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
        store.save(entry("e1", "original", MemoryScope.SESSION, Set.of())).block();
        store.save(entry("e1", "updated", MemoryScope.SESSION, Set.of())).block();

        assertEquals(1, store.size());

        StepVerifier.create(store.get("e1"))
                .assertNext(found -> assertEquals("updated", found.content()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Delete entry")
    void testDelete() {
        store.save(entry("e1", "to delete", MemoryScope.SESSION, Set.of())).block();
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
        store.save(entry("e1", "a", MemoryScope.SESSION, Set.of())).block();
        store.save(entry("e2", "b", MemoryScope.AGENT, Set.of())).block();

        assertEquals(2, store.size());
        store.clear();
        assertEquals(0, store.size());
    }

    @Test
    @DisplayName("Case-insensitive search")
    void testCaseInsensitiveSearch() {
        store.save(entry("e1", "UPPERCASE content", MemoryScope.SESSION, Set.of())).block();

        StepVerifier.create(store.search("uppercase", MemoryScope.SESSION).collectList())
                .assertNext(results -> assertEquals(1, results.size()))
                .verifyComplete();
    }

    // ==================== MemoryQuery TESTS ====================

    @Test
    @DisplayName("search(MemoryQuery) filters by agentId")
    void testMemoryQueryFilterByAgentId() {
        MemoryEntry e1 =
                new MemoryEntry(
                        "e1",
                        "agent-A",
                        "content A",
                        null,
                        MemoryScope.AGENT,
                        0.5,
                        null,
                        Set.of(),
                        Instant.now(),
                        null);
        MemoryEntry e2 =
                new MemoryEntry(
                        "e2",
                        "agent-B",
                        "content B",
                        null,
                        MemoryScope.AGENT,
                        0.5,
                        null,
                        Set.of(),
                        Instant.now(),
                        null);
        store.save(e1).block();
        store.save(e2).block();

        MemoryQuery query = MemoryQuery.builder().agentId("agent-A").build();

        StepVerifier.create(store.search(query).collectList())
                .assertNext(
                        results -> {
                            assertEquals(1, results.size());
                            assertEquals("e1", results.get(0).id());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("search(MemoryQuery) filters by keyword case-insensitive")
    void testMemoryQueryFilterByKeyword() {
        store.save(entry("e1", "Java programming", MemoryScope.SESSION, Set.of())).block();
        store.save(entry("e2", "Python scripting", MemoryScope.SESSION, Set.of())).block();

        MemoryQuery query = MemoryQuery.builder().keyword("JAVA").build();

        StepVerifier.create(store.search(query).collectList())
                .assertNext(
                        results -> {
                            assertEquals(1, results.size());
                            assertEquals("e1", results.get(0).id());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("search(MemoryQuery) filters by tags containsAll")
    void testMemoryQueryFilterByTags() {
        store.save(entry("e1", "content A", MemoryScope.SESSION, Set.of("java", "coding"))).block();
        store.save(entry("e2", "content B", MemoryScope.SESSION, Set.of("java"))).block();
        store.save(entry("e3", "content C", MemoryScope.SESSION, Set.of("python"))).block();

        MemoryQuery query = MemoryQuery.builder().tags(Set.of("java", "coding")).build();

        StepVerifier.create(store.search(query).collectList())
                .assertNext(
                        results -> {
                            assertEquals(1, results.size());
                            assertEquals("e1", results.get(0).id());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("search(MemoryQuery) filters by importance threshold")
    void testMemoryQueryFilterByImportance() {
        MemoryEntry low =
                new MemoryEntry(
                        "low",
                        null,
                        "low importance",
                        null,
                        MemoryScope.SESSION,
                        0.2,
                        null,
                        Set.of(),
                        Instant.now(),
                        null);
        MemoryEntry high =
                new MemoryEntry(
                        "high",
                        null,
                        "high importance",
                        null,
                        MemoryScope.SESSION,
                        0.9,
                        null,
                        Set.of(),
                        Instant.now(),
                        null);
        store.save(low).block();
        store.save(high).block();

        MemoryQuery query = MemoryQuery.builder().minImportance(0.7).build();

        StepVerifier.create(store.search(query).collectList())
                .assertNext(
                        results -> {
                            assertEquals(1, results.size());
                            assertEquals("high", results.get(0).id());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("search(MemoryQuery) filters by date range")
    void testMemoryQueryFilterByDateRange() {
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2025-06-15T00:00:00Z");
        Instant t3 = Instant.parse("2025-12-31T00:00:00Z");

        MemoryEntry e1 =
                new MemoryEntry(
                        "e1",
                        null,
                        "January",
                        null,
                        MemoryScope.SESSION,
                        0.5,
                        null,
                        Set.of(),
                        t1,
                        null);
        MemoryEntry e2 =
                new MemoryEntry(
                        "e2",
                        null,
                        "June",
                        null,
                        MemoryScope.SESSION,
                        0.5,
                        null,
                        Set.of(),
                        t2,
                        null);
        MemoryEntry e3 =
                new MemoryEntry(
                        "e3",
                        null,
                        "December",
                        null,
                        MemoryScope.SESSION,
                        0.5,
                        null,
                        Set.of(),
                        t3,
                        null);
        store.save(e1).block();
        store.save(e2).block();
        store.save(e3).block();

        MemoryQuery query =
                MemoryQuery.builder()
                        .from(Instant.parse("2025-03-01T00:00:00Z"))
                        .to(Instant.parse("2025-09-01T00:00:00Z"))
                        .build();

        StepVerifier.create(store.search(query).collectList())
                .assertNext(
                        results -> {
                            assertEquals(1, results.size());
                            assertEquals("e2", results.get(0).id());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("search(MemoryQuery) respects limit")
    void testMemoryQueryLimit() {
        for (int i = 0; i < 10; i++) {
            store.save(entry("e" + i, "content " + i, MemoryScope.SESSION, Set.of())).block();
        }

        MemoryQuery query = MemoryQuery.builder().limit(3).build();

        StepVerifier.create(store.search(query).collectList())
                .assertNext(results -> assertEquals(3, results.size()))
                .verifyComplete();
    }

    @Test
    @DisplayName("search(MemoryQuery) with combined filters")
    void testMemoryQueryCombinedFilters() {
        MemoryEntry e1 =
                new MemoryEntry(
                        "e1",
                        "agent-A",
                        "Java guide",
                        null,
                        MemoryScope.AGENT,
                        0.8,
                        null,
                        Set.of("coding"),
                        Instant.parse("2025-06-01T00:00:00Z"),
                        null);
        MemoryEntry e2 =
                new MemoryEntry(
                        "e2",
                        "agent-A",
                        "Python guide",
                        null,
                        MemoryScope.AGENT,
                        0.8,
                        null,
                        Set.of("coding"),
                        Instant.parse("2025-06-01T00:00:00Z"),
                        null);
        MemoryEntry e3 =
                new MemoryEntry(
                        "e3",
                        "agent-B",
                        "Java tutorial",
                        null,
                        MemoryScope.AGENT,
                        0.8,
                        null,
                        Set.of("coding"),
                        Instant.parse("2025-06-01T00:00:00Z"),
                        null);
        store.save(e1).block();
        store.save(e2).block();
        store.save(e3).block();

        MemoryQuery query =
                MemoryQuery.builder()
                        .agentId("agent-A")
                        .keyword("java")
                        .tags(Set.of("coding"))
                        .minImportance(0.5)
                        .build();

        StepVerifier.create(store.search(query).collectList())
                .assertNext(
                        results -> {
                            assertEquals(1, results.size());
                            assertEquals("e1", results.get(0).id());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("search(MemoryQuery) cosine similarity ranking")
    void testMemoryQueryCosineSimilarity() {
        // Entry close to query vector [1, 0, 0]
        float[] closeEmbedding = {0.9f, 0.1f, 0.0f};
        // Entry far from query vector
        float[] farEmbedding = {0.0f, 0.0f, 1.0f};

        MemoryEntry close =
                new MemoryEntry(
                        "close",
                        null,
                        "close entry",
                        null,
                        MemoryScope.SESSION,
                        0.5,
                        closeEmbedding,
                        Set.of(),
                        Instant.now(),
                        null);
        MemoryEntry far =
                new MemoryEntry(
                        "far",
                        null,
                        "far entry",
                        null,
                        MemoryScope.SESSION,
                        0.5,
                        farEmbedding,
                        Set.of(),
                        Instant.now(),
                        null);
        store.save(far).block();
        store.save(close).block();

        float[] queryVector = {1.0f, 0.0f, 0.0f};
        MemoryQuery query = MemoryQuery.builder().queryVector(queryVector).limit(10).build();

        StepVerifier.create(store.search(query).collectList())
                .assertNext(
                        results -> {
                            assertEquals(2, results.size());
                            assertEquals("close", results.get(0).id()); // highest similarity first
                            assertEquals("far", results.get(1).id());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Thread safety: concurrent writes do not lose entries")
    void testThreadSafety() throws Exception {
        int threadCount = 10;
        int entriesPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(
                    () -> {
                        try {
                            for (int i = 0; i < entriesPerThread; i++) {
                                String id = "t" + threadId + "-e" + i;
                                store.save(entry(id, "content", MemoryScope.SESSION, Set.of()))
                                        .block();
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
        }

        latch.await();
        executor.shutdown();

        assertEquals(threadCount * entriesPerThread, store.size());
    }
}
