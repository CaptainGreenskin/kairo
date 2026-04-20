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
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

class FileMemoryStoreTest {

    @TempDir Path tempDir;

    private MemoryEntry entry(String id, String content, MemoryScope scope, Set<String> tags) {
        return new MemoryEntry(
                id,
                null,
                content,
                null,
                scope,
                0.5,
                null,
                tags,
                Instant.parse("2025-01-15T10:00:00Z"),
                null);
    }

    @Test
    @DisplayName("Save entry and read it back")
    void testSaveAndGet() {
        FileMemoryStore store = new FileMemoryStore(tempDir);
        MemoryEntry e = entry("e1", "hello world", MemoryScope.SESSION, Set.of("test"));

        StepVerifier.create(store.save(e))
                .assertNext(saved -> assertEquals("e1", saved.id()))
                .verifyComplete();

        StepVerifier.create(store.get("e1"))
                .assertNext(
                        found -> {
                            assertEquals("hello world", found.content());
                            assertEquals(MemoryScope.SESSION, found.scope());
                            assertTrue(found.tags().contains("test"));
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Persistence across store instances")
    void testPersistenceAcrossInstances() {
        FileMemoryStore store1 = new FileMemoryStore(tempDir);
        store1.save(entry("e1", "persistent data", MemoryScope.AGENT, Set.of("persist"))).block();

        FileMemoryStore store2 = new FileMemoryStore(tempDir);

        StepVerifier.create(store2.get("e1"))
                .assertNext(found -> assertEquals("persistent data", found.content()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Search entries by content")
    void testSearch() {
        FileMemoryStore store = new FileMemoryStore(tempDir);
        store.save(entry("e1", "Java programming", MemoryScope.AGENT, Set.of())).block();
        store.save(entry("e2", "Python scripting", MemoryScope.AGENT, Set.of())).block();

        StepVerifier.create(store.search("java", MemoryScope.AGENT).collectList())
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
        store.save(entry("e1", "content", MemoryScope.SESSION, Set.of("java", "coding"))).block();
        store.save(entry("e2", "content", MemoryScope.SESSION, Set.of("python"))).block();

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
                                null,
                                "first",
                                null,
                                MemoryScope.GLOBAL,
                                0.5,
                                null,
                                Set.of(),
                                Instant.parse("2025-01-10T10:00:00Z"),
                                null))
                .block();
        store.save(
                        new MemoryEntry(
                                "e2",
                                null,
                                "second",
                                null,
                                MemoryScope.GLOBAL,
                                0.5,
                                null,
                                Set.of(),
                                Instant.parse("2025-01-15T10:00:00Z"),
                                null))
                .block();

        StepVerifier.create(store.list(MemoryScope.GLOBAL).collectList())
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
        store.save(entry("e1", "to delete", MemoryScope.SESSION, Set.of())).block();

        StepVerifier.create(store.delete("e1")).verifyComplete();

        StepVerifier.create(store.get("e1")).verifyComplete();
    }

    @Test
    @DisplayName("Corrupted JSON file is skipped gracefully")
    void testCorruptedFile() throws Exception {
        FileMemoryStore store = new FileMemoryStore(tempDir);
        store.save(entry("e1", "valid", MemoryScope.SESSION, Set.of())).block();

        Path sessionDir = tempDir.resolve("session");
        Files.writeString(sessionDir.resolve("bad.json"), "this is not valid json\n");

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

        assertFalse(Files.exists(subDir.resolve("session")));

        store.save(entry("e1", "test", MemoryScope.SESSION, Set.of())).block();
        assertTrue(Files.exists(subDir.resolve("session")));
    }

    @Test
    @DisplayName("Multiple entries in same scope file")
    void testMultipleEntries() {
        FileMemoryStore store = new FileMemoryStore(tempDir);
        store.save(entry("e1", "first", MemoryScope.SESSION, Set.of())).block();
        store.save(entry("e2", "second", MemoryScope.SESSION, Set.of())).block();
        store.save(entry("e3", "third", MemoryScope.SESSION, Set.of())).block();

        StepVerifier.create(store.list(MemoryScope.SESSION).collectList())
                .assertNext(results -> assertEquals(3, results.size()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Get searches across all scopes")
    void testGetAcrossScopes() {
        FileMemoryStore store = new FileMemoryStore(tempDir);
        store.save(entry("e1", "in agent", MemoryScope.AGENT, Set.of())).block();

        StepVerifier.create(store.get("e1"))
                .assertNext(found -> assertEquals("in agent", found.content()))
                .verifyComplete();
    }
}
