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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

class MemoryPromptContributorTest {

    @TempDir Path tempDir;

    private Path memoryDir;
    private MemoryPromptContributor contributor;

    @BeforeEach
    void setUp() {
        memoryDir = tempDir.resolve("memory");
        contributor = new MemoryPromptContributor(memoryDir);
    }

    @Test
    void sectionName() {
        assertThat(contributor.sectionName()).isEqualTo("memory");
    }

    @Test
    void emptyWhenNoDirectory() {
        StepVerifier.create(contributor.content()).verifyComplete();
    }

    @Test
    void emptyWhenNoIndex() throws IOException {
        Files.createDirectories(memoryDir);
        StepVerifier.create(contributor.content()).verifyComplete();
    }

    @Test
    void emptyWhenIndexIsBlank() throws IOException {
        Files.createDirectories(memoryDir);
        Files.writeString(memoryDir.resolve("MEMORY.md"), "   \n  ");
        StepVerifier.create(contributor.content()).verifyComplete();
    }

    @Test
    void returnsFormattedContent() throws IOException {
        Files.createDirectories(memoryDir);
        String indexContent = "- [Test](test.md) — A test memory\n";
        Files.writeString(memoryDir.resolve("MEMORY.md"), indexContent);

        StepVerifier.create(contributor.content())
                .assertNext(
                        content -> {
                            assertThat(content).startsWith("# Persistent Memories");
                            assertThat(content).contains("test.md");
                            assertThat(content).contains("A test memory");
                        })
                .verifyComplete();
    }

    @Test
    void worksWithMemoryDirectoryManager() {
        MemoryDirectoryManager manager = new MemoryDirectoryManager(memoryDir);
        manager.write(
                new MemoryFile(
                        "integration-test",
                        "Tests must hit real DB",
                        MemoryType.FEEDBACK,
                        "Body content.",
                        Instant.now()));

        StepVerifier.create(contributor.content())
                .assertNext(
                        content -> {
                            assertThat(content).contains("# Persistent Memories");
                            assertThat(content).contains("integration-test.md");
                            assertThat(content).contains("Tests must hit real DB");
                        })
                .verifyComplete();
    }

    @Test
    void concurrentCallsSafe() {
        MemoryDirectoryManager manager = new MemoryDirectoryManager(memoryDir);
        manager.write(
                new MemoryFile(
                        "concurrent", "Concurrent test", MemoryType.USER, "body", Instant.now()));

        StepVerifier.create(contributor.content().repeat(9)).expectNextCount(10).verifyComplete();
    }
}
