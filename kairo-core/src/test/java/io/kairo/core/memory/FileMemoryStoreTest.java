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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

class FileMemoryStoreTest {

    @TempDir Path tempDir;

    private MemoryEntry entry(String id, String content, MemoryScope scope, List<String> tags) {
        return new MemoryEntry(
                id, content, scope, Instant.parse("2025-01-15T10:00:00Z"), tags, true);
    }

    @Test
    @DisplayName("Save entry and read it back")
    void testSaveAndGet() {
        FileMemoryStore store = new FileMemoryStore(tempDir);
        MemoryEntry e = entry("e1", "hello world", MemoryScope.SESSION, List.of("test"));

        StepVerifier.create(store.save(e))
                .assertNext(saved -> assertEquals("e1", saved.id()))
                .verifyComplete();

        StepVerifier.create(store.get("e1"))
                .assertNext(
                        found -> {
                            assertEquals("hello world", found.content());
                            assertEquals(MemoryScope.SESSION, found.scope());
                            assertEquals(List.of("test"), found.tags());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Persistence across store instances")
    void testPersistenceAcrossInstances() {
        FileMemoryStore store1 = new FileMemoryStore(tempDir);
        store1.save(entry("e1", "persistent data", MemoryScope.PROJECT, List.of("persist")))
                .block();

        // Create a new store instance pointing to same directory
        FileMemoryStore store2 = new FileMemoryStore(tempDir);

        StepVerifier.create(store2.get("e1"))
                .assertNext(found -> assertEquals("persistent data", found.content()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Search entries by content")
    void testSearch() {
        FileMemoryStore store = new FileMemoryStore(tempDir);
        store.save(entry("e1", "Java programming", MemoryScope.PROJECT, List.of())).block();
        store.save(entry("e2", "Python scripting", MemoryScope.PROJECT, List.of())).block();

        StepVerifier.create(store.search("java", MemoryScope.PROJECT).collectList())
                .assertNext(
                        results -> {
                            assertEquals(1, results.size());
                            assertEquals("e1", results.get(0).id());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Search entries by tags")
    void testSearchByTags() {
        FileMemoryStore store = new FileMemoryStore(tempDir);
        store.save(entry("e1", "content", MemoryScope.SESSION, List.of("java", "coding"))).block();
        store.save(entry("e2", "content", MemoryScope.SESSION, List.of("python"))).block();

        StepVerifier.create(store.search("java", MemoryScope.SESSION).collectList())
                .assertNext(results -> assertEquals(1, results.size()))
                .verifyComplete();
    }

    @Test
    @DisplayName("List entries for a scope")
    void testList() {
        FileMemoryStore store = new FileMemoryStore(tempDir);
        store.save(
                        new MemoryEntry(
                                "e1",
                                "first",
                                MemoryScope.USER,
                                Instant.parse("2025-01-10T10:00:00Z"),
                                List.of(),
                                true))
                .block();
        store.save(
                        new MemoryEntry(
                                "e2",
                                "second",
                                MemoryScope.USER,
                                Instant.parse("2025-01-15T10:00:00Z"),
                                List.of(),
                                true))
                .block();

        StepVerifier.create(store.list(MemoryScope.USER).collectList())
                .assertNext(
                        results -> {
                            assertEquals(2, results.size());
                            assertEquals("e2", results.get(0).id()); // newer first
                            assertEquals("e1", results.get(1).id());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Delete entry")
    void testDelete() {
        FileMemoryStore store = new FileMemoryStore(tempDir);
        store.save(entry("e1", "to delete", MemoryScope.SESSION, List.of())).block();

        StepVerifier.create(store.delete("e1")).verifyComplete();

        StepVerifier.create(store.get("e1")).verifyComplete();
    }

    @Test
    @DisplayName("Corrupted JSON file is skipped gracefully")
    void testCorruptedFile() throws Exception {
        FileMemoryStore store = new FileMemoryStore(tempDir);
        // Save a valid entry first
        store.save(entry("e1", "valid", MemoryScope.SESSION, List.of())).block();

        // Write a corrupted JSON file alongside the valid one
        Path sessionDir = tempDir.resolve("session");
        Files.writeString(sessionDir.resolve("bad.json"), "this is not valid json\n");

        // Create new store and verify it can still list the valid entry (corrupted one is skipped)
        FileMemoryStore store2 = new FileMemoryStore(tempDir);
        StepVerifier.create(store2.list(MemoryScope.SESSION).collectList())
                .assertNext(
                        results -> {
                            assertEquals(1, results.size());
                            assertEquals("valid", results.get(0).content());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Get from empty store returns empty")
    void testGetFromEmptyStore() {
        FileMemoryStore store = new FileMemoryStore(tempDir);

        StepVerifier.create(store.get("nonexistent")).verifyComplete();
    }

    @Test
    @DisplayName("Creates directory structure on first save")
    void testDirectoryCreation() {
        Path subDir = tempDir.resolve("nested/memory");
        FileMemoryStore store = new FileMemoryStore(subDir);

        // Directories are created lazily on save, not on construction
        assertFalse(Files.exists(subDir.resolve("session")));

        // After saving, the scope directory should exist
        store.save(entry("e1", "test", MemoryScope.SESSION, List.of())).block();
        assertTrue(Files.exists(subDir.resolve("session")));
    }

    @Test
    @DisplayName("Multiple entries in same scope file")
    void testMultipleEntries() {
        FileMemoryStore store = new FileMemoryStore(tempDir);
        store.save(entry("e1", "first", MemoryScope.SESSION, List.of())).block();
        store.save(entry("e2", "second", MemoryScope.SESSION, List.of())).block();
        store.save(entry("e3", "third", MemoryScope.SESSION, List.of())).block();

        StepVerifier.create(store.list(MemoryScope.SESSION).collectList())
                .assertNext(results -> assertEquals(3, results.size()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Get searches across all scopes")
    void testGetAcrossScopes() {
        FileMemoryStore store = new FileMemoryStore(tempDir);
        store.save(entry("e1", "in project", MemoryScope.PROJECT, List.of())).block();

        // get() should find it even though it's in PROJECT scope
        StepVerifier.create(store.get("e1"))
                .assertNext(found -> assertEquals("in project", found.content()))
                .verifyComplete();
    }
}
