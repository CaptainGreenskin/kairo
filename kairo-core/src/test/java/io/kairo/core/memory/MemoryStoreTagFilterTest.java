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
import io.kairo.api.memory.MemoryStore;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

class MemoryStoreTagFilterTest {

    private static MemoryEntry entry(
            String id, String content, MemoryScope scope, List<String> tags) {
        return new MemoryEntry(
                id, content, scope, Instant.parse("2025-01-15T10:00:00Z"), tags, true);
    }

    private static void seedEntries(MemoryStore store) {
        store.save(entry("e1", "java guide", MemoryScope.PROJECT, List.of("java", "tutorial")))
                .block();
        store.save(
                        entry(
                                "e2",
                                "java concurrency",
                                MemoryScope.PROJECT,
                                List.of("java", "concurrency")))
                .block();
        store.save(entry("e3", "python basics", MemoryScope.PROJECT, List.of("python"))).block();
        store.save(entry("e4", "java streams", MemoryScope.PROJECT, List.of("java", "tutorial")))
                .block();
    }

    private static void assertAllTagsMatch(MemoryStore store) {
        seedEntries(store);
        StepVerifier.create(
                        store.search("java", MemoryScope.PROJECT, List.of("java", "tutorial"))
                                .collectList())
                .assertNext(
                        results -> {
                            assertEquals(2, results.size());
                            assertTrue(results.stream().anyMatch(e -> e.id().equals("e1")));
                            assertTrue(results.stream().anyMatch(e -> e.id().equals("e4")));
                        })
                .verifyComplete();
    }

    private static void assertPartialTagMatchExcluded(MemoryStore store) {
        seedEntries(store);
        StepVerifier.create(
                        store.search("java", MemoryScope.PROJECT, List.of("java", "concurrency"))
                                .collectList())
                .assertNext(
                        results -> {
                            assertEquals(1, results.size());
                            assertEquals("e2", results.get(0).id());
                        })
                .verifyComplete();
    }

    private static void assertNullTagsFallsBack(MemoryStore store) {
        seedEntries(store);
        StepVerifier.create(store.search("java", MemoryScope.PROJECT, null).collectList())
                .assertNext(results -> assertEquals(3, results.size()))
                .verifyComplete();
    }

    private static void assertEmptyTagsFallsBack(MemoryStore store) {
        seedEntries(store);
        StepVerifier.create(store.search("java", MemoryScope.PROJECT, List.of()).collectList())
                .assertNext(results -> assertEquals(3, results.size()))
                .verifyComplete();
    }

    private static void assertNoMatchingTagsReturnsEmpty(MemoryStore store) {
        seedEntries(store);
        StepVerifier.create(
                        store.search("java", MemoryScope.PROJECT, List.of("nonexistent"))
                                .collectList())
                .assertNext(results -> assertTrue(results.isEmpty()))
                .verifyComplete();
    }

    private static void assertNullTagsOnEntryNoNPE(MemoryStore store) {
        MemoryEntry entryWithNullTags =
                new MemoryEntry(
                        "e-null", "some content", MemoryScope.SESSION, Instant.now(), null, true);
        store.save(entryWithNullTags).block();
        StepVerifier.create(
                        store.search("some", MemoryScope.SESSION, List.of("tag1")).collectList())
                .assertNext(results -> assertTrue(results.isEmpty()))
                .verifyComplete();
    }

    private static void assertSingleTagFilter(MemoryStore store) {
        seedEntries(store);
        StepVerifier.create(
                        store.search("java", MemoryScope.PROJECT, List.of("concurrency"))
                                .collectList())
                .assertNext(
                        results -> {
                            assertEquals(1, results.size());
                            assertEquals("e2", results.get(0).id());
                        })
                .verifyComplete();
    }

    private static void assertTagFilterRespectsScope(MemoryStore store) {
        store.save(entry("s1", "java guide", MemoryScope.SESSION, List.of("java", "tutorial")))
                .block();
        store.save(entry("p1", "java guide", MemoryScope.PROJECT, List.of("java", "tutorial")))
                .block();
        StepVerifier.create(
                        store.search("java", MemoryScope.SESSION, List.of("java")).collectList())
                .assertNext(
                        results -> {
                            assertEquals(1, results.size());
                            assertEquals("s1", results.get(0).id());
                        })
                .verifyComplete();
    }

    @Nested
    @DisplayName("InMemoryStore tag filtering")
    class InMemoryStoreTagFilterTest {

        private InMemoryStore store;

        @BeforeEach
        void setUp() {
            store = new InMemoryStore();
        }

        @Test
        @DisplayName("Entries with ALL specified tags are returned")
        void allTagsMatch() {
            assertAllTagsMatch(store);
        }

        @Test
        @DisplayName("Entries missing ANY specified tag are excluded")
        void partialTagMatchExcluded() {
            assertPartialTagMatchExcluded(store);
        }

        @Test
        @DisplayName("Null tags falls back to regular search")
        void nullTagsFallsBack() {
            assertNullTagsFallsBack(store);
        }

        @Test
        @DisplayName("Empty tags list falls back to regular search")
        void emptyTagsFallsBack() {
            assertEmptyTagsFallsBack(store);
        }

        @Test
        @DisplayName("No matching tags returns empty Flux")
        void noMatchingTagsReturnsEmpty() {
            assertNoMatchingTagsReturnsEmpty(store);
        }

        @Test
        @DisplayName("Entry with null tags does not cause NPE")
        void nullTagsOnEntryNoNPE() {
            assertNullTagsOnEntryNoNPE(store);
        }

        @Test
        @DisplayName("Single tag filter works correctly")
        void singleTagFilter() {
            assertSingleTagFilter(store);
        }

        @Test
        @DisplayName("Tag filter respects scope boundary")
        void tagFilterRespectsScope() {
            assertTagFilterRespectsScope(store);
        }
    }

    @Nested
    @DisplayName("FileMemoryStore tag filtering")
    class FileMemoryStoreTagFilterTest {

        @TempDir Path tempDir;
        private FileMemoryStore store;

        @BeforeEach
        void setUp() {
            store = new FileMemoryStore(tempDir);
        }

        @Test
        @DisplayName("Entries with ALL specified tags are returned")
        void allTagsMatch() {
            assertAllTagsMatch(store);
        }

        @Test
        @DisplayName("Entries missing ANY specified tag are excluded")
        void partialTagMatchExcluded() {
            assertPartialTagMatchExcluded(store);
        }

        @Test
        @DisplayName("Null tags falls back to regular search")
        void nullTagsFallsBack() {
            assertNullTagsFallsBack(store);
        }

        @Test
        @DisplayName("Empty tags list falls back to regular search")
        void emptyTagsFallsBack() {
            assertEmptyTagsFallsBack(store);
        }

        @Test
        @DisplayName("No matching tags returns empty Flux")
        void noMatchingTagsReturnsEmpty() {
            assertNoMatchingTagsReturnsEmpty(store);
        }

        @Test
        @DisplayName("Entry with null tags does not cause NPE")
        void nullTagsOnEntryNoNPE() {
            assertNullTagsOnEntryNoNPE(store);
        }

        @Test
        @DisplayName("Single tag filter works correctly")
        void singleTagFilter() {
            assertSingleTagFilter(store);
        }

        @Test
        @DisplayName("Tag filter respects scope boundary")
        void tagFilterRespectsScope() {
            assertTagFilterRespectsScope(store);
        }
    }
}
