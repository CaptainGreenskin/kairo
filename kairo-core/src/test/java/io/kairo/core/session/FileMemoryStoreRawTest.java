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
package io.kairo.core.session;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.memory.MemoryScope;
import io.kairo.core.memory.FileMemoryStore;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

class FileMemoryStoreRawTest {

    @TempDir Path tempDir;
    private FileMemoryStore store;

    @BeforeEach
    void setUp() {
        store = new FileMemoryStore(tempDir);
    }

    @Test
    @DisplayName("saveRaw and loadRaw round-trip")
    void saveAndLoad() {
        store.saveRaw("key1", "hello world", MemoryScope.SESSION).block();

        StepVerifier.create(store.loadRaw("key1", MemoryScope.SESSION))
                .assertNext(val -> assertEquals("hello world", val))
                .verifyComplete();
    }

    @Test
    @DisplayName("loadRaw for nonexistent key returns empty")
    void loadNonexistentReturnsEmpty() {
        StepVerifier.create(store.loadRaw("nonexistent", MemoryScope.SESSION)).verifyComplete();
    }

    @Test
    @DisplayName("deleteRaw removes entry")
    void deleteRemovesEntry() {
        store.saveRaw("key1", "data", MemoryScope.SESSION).block();
        Boolean deleted = store.deleteRaw("key1", MemoryScope.SESSION).block();
        assertTrue(deleted);

        StepVerifier.create(store.loadRaw("key1", MemoryScope.SESSION)).verifyComplete();
    }

    @Test
    @DisplayName("saveRaw creates directories automatically")
    void saveCreatesDirectoriesAutomatically() {
        Path nested = tempDir.resolve("deep/nested/dir");
        FileMemoryStore nestedStore = new FileMemoryStore(nested);
        nestedStore.saveRaw("key1", "data", MemoryScope.SESSION).block();

        StepVerifier.create(nestedStore.loadRaw("key1", MemoryScope.SESSION))
                .assertNext(val -> assertEquals("data", val))
                .verifyComplete();
    }

    @Test
    @DisplayName("Overwrite existing key with new value")
    void overwriteExistingKey() {
        store.saveRaw("key1", "original", MemoryScope.SESSION).block();
        store.saveRaw("key1", "updated", MemoryScope.SESSION).block();

        StepVerifier.create(store.loadRaw("key1", MemoryScope.SESSION))
                .assertNext(val -> assertEquals("updated", val))
                .verifyComplete();
    }

    @Test
    @DisplayName("Different scopes are isolated")
    void differentScopesAreIsolated() {
        store.saveRaw("key1", "session-data", MemoryScope.SESSION).block();
        store.saveRaw("key1", "project-data", MemoryScope.PROJECT).block();

        StepVerifier.create(store.loadRaw("key1", MemoryScope.SESSION))
                .assertNext(val -> assertEquals("session-data", val))
                .verifyComplete();

        StepVerifier.create(store.loadRaw("key1", MemoryScope.PROJECT))
                .assertNext(val -> assertEquals("project-data", val))
                .verifyComplete();
    }

    @Test
    @DisplayName("Atomic write leaves no .tmp files")
    void atomicWriteWithTmpFile() throws Exception {
        store.saveRaw("key1", "data", MemoryScope.SESSION).block();

        Path scopeDir = tempDir.resolve("session");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(scopeDir, "*.tmp")) {
            assertFalse(stream.iterator().hasNext(), "No .tmp files should remain after save");
        }
    }

    @Test
    @DisplayName("Load after delete returns empty")
    void loadAfterDeleteReturnsEmpty() {
        store.saveRaw("key1", "data", MemoryScope.SESSION).block();
        store.deleteRaw("key1", MemoryScope.SESSION).block();

        StepVerifier.create(store.loadRaw("key1", MemoryScope.SESSION)).verifyComplete();
    }

    @Test
    @DisplayName("Save and load large content")
    void saveAndLoadLargeContent() {
        String largeContent = "X".repeat(100_000);
        store.saveRaw("large", largeContent, MemoryScope.SESSION).block();

        StepVerifier.create(store.loadRaw("large", MemoryScope.SESSION))
                .assertNext(val -> assertEquals(100_000, val.length()))
                .verifyComplete();
    }

    @Test
    @DisplayName("listKeys returns all saved keys")
    void listKeysReturnsAllSavedKeys() {
        store.saveRaw("alpha", "a", MemoryScope.SESSION).block();
        store.saveRaw("beta", "b", MemoryScope.SESSION).block();
        store.saveRaw("gamma", "c", MemoryScope.SESSION).block();

        StepVerifier.create(store.listKeys(MemoryScope.SESSION).collectList())
                .assertNext(
                        keys -> {
                            assertEquals(3, keys.size());
                            assertTrue(keys.containsAll(List.of("alpha", "beta", "gamma")));
                        })
                .verifyComplete();
    }
}
