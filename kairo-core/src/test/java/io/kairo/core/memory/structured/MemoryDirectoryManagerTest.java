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
package io.kairo.core.memory.structured;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MemoryDirectoryManagerTest {

    @TempDir Path tempDir;

    private MemoryDirectoryManager manager;

    @BeforeEach
    void setUp() {
        manager = new MemoryDirectoryManager(tempDir.resolve("memory"));
    }

    // -- write / read --

    @Test
    void writeAndRead() {
        MemoryFile file =
                new MemoryFile(
                        "feedback-testing",
                        "Integration tests must hit real database",
                        MemoryType.FEEDBACK,
                        "The body content.",
                        Instant.now());

        manager.write(file);
        MemoryFile read = manager.read("feedback-testing");

        assertThat(read).isNotNull();
        assertThat(read.name()).isEqualTo("feedback-testing");
        assertThat(read.description()).isEqualTo("Integration tests must hit real database");
        assertThat(read.type()).isEqualTo(MemoryType.FEEDBACK);
        assertThat(read.body()).isEqualTo("The body content.");
    }

    @Test
    void writeCreatesDirectory() {
        Path memoryDir = tempDir.resolve("nested/memory");
        MemoryDirectoryManager nested = new MemoryDirectoryManager(memoryDir);
        nested.write(new MemoryFile("test", "desc", MemoryType.USER, "body", Instant.now()));

        assertThat(Files.exists(memoryDir)).isTrue();
        assertThat(nested.read("test")).isNotNull();
    }

    @Test
    void writeUpdatesExisting() {
        MemoryFile v1 =
                new MemoryFile("test", "v1 desc", MemoryType.USER, "v1 body", Instant.now());
        manager.write(v1);

        MemoryFile v2 =
                new MemoryFile("test", "v2 desc", MemoryType.FEEDBACK, "v2 body", Instant.now());
        manager.write(v2);

        MemoryFile read = manager.read("test");
        assertThat(read.description()).isEqualTo("v2 desc");
        assertThat(read.type()).isEqualTo(MemoryType.FEEDBACK);
        assertThat(read.body()).isEqualTo("v2 body");
    }

    @Test
    void readNonexistent() {
        assertThat(manager.read("nonexistent")).isNull();
    }

    // -- delete --

    @Test
    void deleteExisting() {
        manager.write(new MemoryFile("to-delete", "desc", MemoryType.USER, "body", Instant.now()));
        assertThat(manager.delete("to-delete")).isTrue();
        assertThat(manager.read("to-delete")).isNull();
    }

    @Test
    void deleteNonexistent() {
        assertThat(manager.delete("nonexistent")).isFalse();
    }

    // -- listAll --

    @Test
    void listAllEmpty() {
        assertThat(manager.listAll()).isEmpty();
    }

    @Test
    void listAllMultiple() {
        manager.write(new MemoryFile("a", "desc-a", MemoryType.USER, "body-a", Instant.now()));
        manager.write(new MemoryFile("b", "desc-b", MemoryType.FEEDBACK, "body-b", Instant.now()));
        manager.write(new MemoryFile("c", "desc-c", MemoryType.PROJECT, "body-c", Instant.now()));

        List<MemoryFile> all = manager.listAll();
        assertThat(all).hasSize(3);
        assertThat(all.stream().map(MemoryFile::name).toList())
                .containsExactlyInAnyOrder("a", "b", "c");
    }

    // -- listByType --

    @Test
    void listByType() {
        manager.write(new MemoryFile("u1", "desc", MemoryType.USER, "body", Instant.now()));
        manager.write(new MemoryFile("f1", "desc", MemoryType.FEEDBACK, "body", Instant.now()));
        manager.write(new MemoryFile("u2", "desc", MemoryType.USER, "body", Instant.now()));

        List<MemoryFile> users = manager.listByType(MemoryType.USER);
        assertThat(users).hasSize(2);
        assertThat(users.stream().map(MemoryFile::name).toList())
                .containsExactlyInAnyOrder("u1", "u2");

        assertThat(manager.listByType(MemoryType.REFERENCE)).isEmpty();
    }

    // -- search --

    @Test
    void searchByTermOverlap() {
        manager.write(
                new MemoryFile(
                        "db-testing",
                        "Database testing strategy",
                        MemoryType.FEEDBACK,
                        "Use real database for integration tests.",
                        Instant.now()));
        manager.write(
                new MemoryFile(
                        "api-design",
                        "REST API design guidelines",
                        MemoryType.PROJECT,
                        "Follow RESTful conventions.",
                        Instant.now()));

        List<MemoryFile> results = manager.search("database testing", 5);
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).name()).isEqualTo("db-testing");
    }

    @Test
    void searchRespectLimit() {
        for (int i = 0; i < 10; i++) {
            manager.write(
                    new MemoryFile(
                            "item-" + i, "common keyword", MemoryType.USER, "body", Instant.now()));
        }

        List<MemoryFile> results = manager.search("common keyword", 3);
        assertThat(results).hasSizeLessThanOrEqualTo(3);
    }

    // -- MEMORY.md index --

    @Test
    void indexGeneratedOnWrite() throws IOException {
        manager.write(
                new MemoryFile("test", "A test memory", MemoryType.USER, "body", Instant.now()));

        String index = manager.loadIndex();
        assertThat(index).contains("test.md");
        assertThat(index).contains("A test memory");

        Path indexFile = manager.getMemoryDir().resolve("MEMORY.md");
        assertThat(Files.exists(indexFile)).isTrue();
    }

    @Test
    void indexUpdatedOnDelete() {
        manager.write(new MemoryFile("keep", "keep this", MemoryType.USER, "body", Instant.now()));
        manager.write(
                new MemoryFile("remove", "remove this", MemoryType.USER, "body", Instant.now()));

        manager.delete("remove");
        String index = manager.loadIndex();
        assertThat(index).contains("keep.md");
        assertThat(index).doesNotContain("remove.md");
    }

    @Test
    void indexRecencyOrdered() throws InterruptedException {
        manager.write(
                new MemoryFile(
                        "old",
                        "old memory",
                        MemoryType.USER,
                        "body",
                        Instant.parse("2026-01-01T00:00:00Z")));
        manager.write(
                new MemoryFile(
                        "new",
                        "new memory",
                        MemoryType.USER,
                        "body",
                        Instant.parse("2026-05-15T00:00:00Z")));

        String index = manager.loadIndex();
        int oldPos = index.indexOf("old.md");
        int newPos = index.indexOf("new.md");
        assertThat(newPos).isLessThan(oldPos);
    }

    @Test
    void indexTruncatesAt200Lines() {
        for (int i = 0; i < 210; i++) {
            manager.write(
                    new MemoryFile(
                            "mem-" + String.format("%03d", i),
                            "memory number " + i,
                            MemoryType.USER,
                            "body",
                            Instant.now().minusSeconds(i)));
        }

        String index = manager.loadIndex();
        long lineCount = index.lines().count();
        assertThat(lineCount).isLessThanOrEqualTo(201);
        assertThat(index).contains("more memories (use memory_read to search)");
    }

    @Test
    void indexEmptyWhenNoDirectory() {
        MemoryDirectoryManager empty = new MemoryDirectoryManager(tempDir.resolve("nonexistent"));
        assertThat(empty.loadIndex()).isEmpty();
    }

    // -- name validation --

    @Test
    void rejectsNullName() {
        assertThatThrownBy(() -> manager.read(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> manager.read("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPathTraversal() {
        assertThatThrownBy(() -> manager.read("../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path traversal");
    }

    @Test
    void rejectsInvalidCharacters() {
        assertThatThrownBy(() -> manager.read("name with spaces"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void acceptsValidNames() {
        MemoryFile file =
                new MemoryFile(
                        "valid-name_with.dots", "desc", MemoryType.USER, "body", Instant.now());
        manager.write(file);
        assertThat(manager.read("valid-name_with.dots")).isNotNull();
    }

    // -- atomic write --

    @Test
    void noTmpFilesLeftAfterWrite() throws IOException {
        manager.write(new MemoryFile("test", "desc", MemoryType.USER, "body", Instant.now()));

        try (var stream = Files.list(manager.getMemoryDir())) {
            long tmpCount = stream.filter(p -> p.toString().endsWith(".tmp")).count();
            assertThat(tmpCount).isZero();
        }
    }
}
