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
package io.kairo.core.skill;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.skill.SkillDefinition;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

@DisplayName("Skill Search Path Loading")
class SkillSearchPathTest {

    @TempDir Path tempDir;

    private DefaultSkillRegistry registry;
    private SkillLoader loader;

    @BeforeEach
    void setUp() {
        registry = new DefaultSkillRegistry();
        loader = new SkillLoader(registry);
    }

    private void writeSkillFile(Path dir, String filename, String name, String description)
            throws IOException {
        Files.createDirectories(dir);
        String content =
                """
                ---
                name: %s
                version: 1.0.0
                category: GENERAL
                triggers:
                  - "trigger %s"
                ---
                # %s

                %s
                """
                        .formatted(name, name, name, description);
        Files.writeString(dir.resolve(filename + ".md"), content, StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("Multi-path loading")
    class MultiPathLoading {

        @Test
        @DisplayName("loads skills from multiple directories")
        void loadsFromMultipleDirectories() throws IOException {
            Path dir1 = tempDir.resolve("path1");
            Path dir2 = tempDir.resolve("path2");
            writeSkillFile(dir1, "skill-a", "skill-a", "From path1");
            writeSkillFile(dir2, "skill-b", "skill-b", "From path2");

            List<SkillDefinition> skills =
                    loader.loadFromSearchPaths(List.of(dir1.toString(), dir2.toString()))
                            .collectList()
                            .block();

            assertNotNull(skills);
            assertEquals(2, skills.size());
            Map<String, SkillDefinition> byName =
                    skills.stream()
                            .collect(Collectors.toMap(SkillDefinition::name, Function.identity()));
            assertTrue(byName.containsKey("skill-a"));
            assertTrue(byName.containsKey("skill-b"));
        }
    }

    @Nested
    @DisplayName("Priority override (last-wins)")
    class PriorityOverride {

        @Test
        @DisplayName("skill from later path overrides same-name skill from earlier path")
        void lastPathWins() throws IOException {
            Path lowPriority = tempDir.resolve("low");
            Path highPriority = tempDir.resolve("high");
            writeSkillFile(lowPriority, "shared", "shared-skill", "Low priority version");
            writeSkillFile(highPriority, "shared", "shared-skill", "High priority version");

            List<SkillDefinition> skills =
                    loader.loadFromSearchPaths(
                                    List.of(lowPriority.toString(), highPriority.toString()))
                            .collectList()
                            .block();

            assertNotNull(skills);
            assertEquals(1, skills.size());
            assertEquals("shared-skill", skills.get(0).name());
            // The registry should have the high-priority version
            var registered = registry.get("shared-skill");
            assertTrue(registered.isPresent());
        }
    }

    @Nested
    @DisplayName("classpath: resolution")
    class ClasspathResolution {

        @Test
        @DisplayName("resolves classpath: prefix to a valid path")
        void resolvesClasspath() {
            Path resolved = loader.resolveSearchPath("classpath:skills");
            assertNotNull(resolved, "classpath:skills should resolve to test resources");
            assertTrue(Files.isDirectory(resolved));
        }

        @Test
        @DisplayName("returns null for non-existent classpath resource")
        void returnsNullForMissingClasspath() {
            Path resolved = loader.resolveSearchPath("classpath:nonexistent-dir-xyz");
            assertNull(resolved);
        }

        @Test
        @DisplayName("loads skills from classpath: path")
        void loadsFromClasspath() {
            List<SkillDefinition> skills =
                    loader.loadFromSearchPaths(List.of("classpath:skills")).collectList().block();

            assertNotNull(skills);
            assertFalse(skills.isEmpty(), "Should load at least one skill from classpath:skills");
            assertTrue(
                    skills.stream().anyMatch(s -> s.name().equals("classpath-skill")),
                    "Should load the classpath-skill from test resources");
        }
    }

    @Nested
    @DisplayName("~/ resolution")
    class TildeResolution {

        @Test
        @DisplayName("resolves ~/ prefix to user home directory")
        void resolvesTilde() {
            // user.home always exists
            Path resolved = loader.resolveSearchPath("~/");
            // The home dir itself exists, so should resolve
            assertNotNull(resolved);
            assertEquals(Path.of(System.getProperty("user.home")), resolved);
        }

        @Test
        @DisplayName("returns null for non-existent ~/ subdirectory")
        void returnsNullForMissingTildePath() {
            Path resolved = loader.resolveSearchPath("~/nonexistent-kairo-test-dir-xyz-123456");
            assertNull(resolved);
        }
    }

    @Nested
    @DisplayName("Missing path tolerance")
    class MissingPathTolerance {

        @Test
        @DisplayName("non-existent paths are silently skipped")
        void skipsNonExistentPaths() throws IOException {
            Path validDir = tempDir.resolve("valid");
            writeSkillFile(validDir, "real-skill", "real-skill", "This exists");

            List<SkillDefinition> skills =
                    loader.loadFromSearchPaths(
                                    List.of(
                                            "/nonexistent/path/1",
                                            validDir.toString(),
                                            "/nonexistent/path/2"))
                            .collectList()
                            .block();

            assertNotNull(skills);
            assertEquals(1, skills.size());
            assertEquals("real-skill", skills.get(0).name());
        }

        @Test
        @DisplayName("all non-existent paths returns empty")
        void allNonExistentReturnsEmpty() {
            List<SkillDefinition> skills =
                    loader.loadFromSearchPaths(List.of("/nonexistent/a", "/nonexistent/b"))
                            .collectList()
                            .block();

            assertNotNull(skills);
            assertTrue(skills.isEmpty());
        }
    }

    @Nested
    @DisplayName("Empty search paths")
    class EmptySearchPaths {

        @Test
        @DisplayName("null search paths returns empty Flux")
        void nullReturnsEmpty() {
            StepVerifier.create(loader.loadFromSearchPaths(null)).verifyComplete();
        }

        @Test
        @DisplayName("empty list returns empty Flux")
        void emptyListReturnsEmpty() {
            StepVerifier.create(loader.loadFromSearchPaths(List.of())).verifyComplete();
        }
    }

    @Nested
    @DisplayName("resolveSearchPath")
    class ResolveSearchPath {

        @Test
        @DisplayName("plain path that exists is resolved")
        void plainExistingPath() {
            Path resolved = loader.resolveSearchPath(tempDir.toString());
            assertNotNull(resolved);
            assertEquals(tempDir, resolved);
        }

        @Test
        @DisplayName("plain path that does not exist returns null")
        void plainNonExistentPath() {
            Path resolved = loader.resolveSearchPath("/this/does/not/exist/at/all");
            assertNull(resolved);
        }
    }
}
