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
package io.kairo.skill;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.skill.SkillCategory;
import io.kairo.api.skill.SkillDefinition;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

@DisplayName("Skill Bundle Support")
class SkillBundleTest {

    @TempDir Path tempDir;

    private DefaultSkillRegistry registry;
    private SkillLoader loader;

    @BeforeEach
    void setUp() {
        registry = new DefaultSkillRegistry();
        loader = new SkillLoader(registry);
    }

    @Nested
    @DisplayName("SkillDefinition bundleRoot")
    class SkillDefinitionBundleRootTest {

        @Test
        @DisplayName("11-param constructor sets bundleRoot to null")
        void elevenParamConstructorSetsNullBundleRoot() {
            var skill =
                    new SkillDefinition(
                            "test",
                            "1.0",
                            "desc",
                            "instructions",
                            List.of("/test"),
                            SkillCategory.CODE,
                            null,
                            null,
                            null,
                            0,
                            null);
            assertNull(skill.bundleRoot());
            assertFalse(skill.isBundle());
        }

        @Test
        @DisplayName("6-param constructor sets bundleRoot to null")
        void sixParamConstructorSetsNullBundleRoot() {
            var skill =
                    new SkillDefinition(
                            "test", "1.0", "desc", "instructions", List.of(), SkillCategory.CODE);
            assertNull(skill.bundleRoot());
            assertFalse(skill.isBundle());
        }

        @Test
        @DisplayName("12-param constructor with non-null bundleRoot is a bundle")
        void twelveParmConstructorWithBundleRoot() {
            var skill =
                    new SkillDefinition(
                            "test",
                            "1.0",
                            "desc",
                            "instructions",
                            List.of(),
                            SkillCategory.CODE,
                            null,
                            null,
                            null,
                            0,
                            null,
                            tempDir);
            assertNotNull(skill.bundleRoot());
            assertTrue(skill.isBundle());
            assertEquals(tempDir, skill.bundleRoot());
        }

        @Test
        @DisplayName("resolveResource throws for non-bundle skill")
        void resolveResourceThrowsForNonBundle() {
            var skill =
                    new SkillDefinition(
                            "test", "1.0", "desc", "instructions", List.of(), SkillCategory.CODE);
            assertThrows(IllegalStateException.class, () -> skill.resolveResource("script.sh"));
        }

        @Test
        @DisplayName("resolveResource works for bundle skill")
        void resolveResourceWorksForBundle() {
            var skill =
                    new SkillDefinition(
                            "test",
                            "1.0",
                            "desc",
                            "instructions",
                            List.of(),
                            SkillCategory.CODE,
                            null,
                            null,
                            null,
                            0,
                            null,
                            tempDir);
            Path resolved = skill.resolveResource("scripts/run.sh");
            assertEquals(tempDir.resolve("scripts/run.sh"), resolved);
        }

        @Test
        @DisplayName("listResources returns empty for non-bundle")
        void listResourcesEmptyForNonBundle() {
            var skill =
                    new SkillDefinition(
                            "test", "1.0", "desc", "instructions", List.of(), SkillCategory.CODE);
            assertEquals(List.of(), skill.listResources());
        }

        @Test
        @DisplayName("listResources returns relative paths excluding SKILL.md")
        void listResourcesReturnsCorrectPaths() throws IOException {
            // Create bundle structure
            Files.writeString(tempDir.resolve("SKILL.md"), "---\nname: test\n---\n# Test");
            Path scriptsDir = Files.createDirectory(tempDir.resolve("scripts"));
            Files.writeString(scriptsDir.resolve("run.sh"), "#!/bin/bash");
            Files.writeString(tempDir.resolve("template.txt"), "hello");

            var skill =
                    new SkillDefinition(
                            "test",
                            "1.0",
                            "desc",
                            "instructions",
                            List.of(),
                            SkillCategory.CODE,
                            null,
                            null,
                            null,
                            0,
                            null,
                            tempDir);

            List<String> resources = skill.listResources();
            assertFalse(resources.contains("SKILL.md"));
            assertTrue(resources.contains("template.txt"));
            assertTrue(resources.contains("scripts"));
            assertTrue(resources.contains("scripts/run.sh"));
        }

        @Test
        @DisplayName("metadataOnly preserves bundleRoot")
        void metadataOnlyPreservesBundleRoot() {
            var skill =
                    new SkillDefinition(
                            "test",
                            "1.0",
                            "desc",
                            "instructions",
                            List.of(),
                            SkillCategory.CODE,
                            null,
                            null,
                            null,
                            0,
                            null,
                            tempDir);
            var metadata = skill.metadataOnly();
            assertEquals(tempDir, metadata.bundleRoot());
            assertTrue(metadata.isBundle());
            assertNull(metadata.instructions());
        }
    }

    @Nested
    @DisplayName("SkillLoader bundle detection")
    class SkillLoaderBundleDetectionTest {

        @Test
        @DisplayName("detects bundle directory with SKILL.md")
        void detectsBundleDirectory() throws IOException {
            // Create a bundle directory
            Path bundleDir = Files.createDirectory(tempDir.resolve("code-review"));
            Files.writeString(
                    bundleDir.resolve("SKILL.md"),
                    "---\nname: code-review\nversion: \"1.0\"\ncategory: CODE\n"
                            + "description: Code review helper\ntriggers:\n  - /review\n---\n"
                            + "# Code Review\n\nReview the code carefully.",
                    StandardCharsets.UTF_8);
            Files.createDirectory(bundleDir.resolve("templates"));
            Files.writeString(bundleDir.resolve("templates/checklist.md"), "- [ ] Check naming");

            StepVerifier.create(loader.loadFromDirectory(tempDir))
                    .assertNext(
                            skill -> {
                                assertEquals("code-review", skill.name());
                                assertTrue(skill.isBundle());
                                assertEquals(bundleDir, skill.bundleRoot());
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("single-file skills have null bundleRoot")
        void singleFileSkillsHaveNullBundleRoot() throws IOException {
            Files.writeString(
                    tempDir.resolve("commit.md"),
                    "---\nname: commit\nversion: \"1.0\"\ncategory: CODE\n"
                            + "description: Commit helper\ntriggers:\n  - /commit\n---\n"
                            + "# Commit\n\nUse conventional commits.",
                    StandardCharsets.UTF_8);

            StepVerifier.create(loader.loadFromDirectory(tempDir))
                    .assertNext(
                            skill -> {
                                assertEquals("commit", skill.name());
                                assertFalse(skill.isBundle());
                                assertNull(skill.bundleRoot());
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("mixed: single-file and bundle loaded together")
        void mixedSingleFileAndBundle() throws IOException {
            // Single-file skill
            Files.writeString(
                    tempDir.resolve("commit.md"),
                    "---\nname: commit\nversion: \"1.0\"\ncategory: CODE\n"
                            + "description: Commit helper\ntriggers:\n  - /commit\n---\n"
                            + "# Commit\n\nUse conventional commits.",
                    StandardCharsets.UTF_8);

            // Bundle skill
            Path bundleDir = Files.createDirectory(tempDir.resolve("security-audit"));
            Files.writeString(
                    bundleDir.resolve("SKILL.md"),
                    "---\nname: security-audit\nversion: \"1.0\"\ncategory: CODE\n"
                            + "description: Security audit\ntriggers:\n  - /audit\n---\n"
                            + "# Security Audit\n\nAudit the code.",
                    StandardCharsets.UTF_8);

            StepVerifier.create(loader.loadFromDirectory(tempDir))
                    .expectNextCount(2)
                    .verifyComplete();

            // Verify both are registered
            assertTrue(registry.get("commit").isPresent());
            assertTrue(registry.get("security-audit").isPresent());
            assertFalse(registry.get("commit").get().isBundle());
            assertTrue(registry.get("security-audit").get().isBundle());
        }

        @Test
        @DisplayName("directory without SKILL.md is ignored")
        void directoryWithoutSkillMdIsIgnored() throws IOException {
            // Not a bundle - no SKILL.md
            Path dir = Files.createDirectory(tempDir.resolve("random-dir"));
            Files.writeString(dir.resolve("README.md"), "# Not a skill");

            StepVerifier.create(loader.loadFromDirectory(tempDir)).verifyComplete();
        }

        @Test
        @DisplayName("getFullContent reloads bundle skill correctly")
        void getFullContentReloadsBundleSkill() throws IOException {
            Path bundleDir = Files.createDirectory(tempDir.resolve("code-review"));
            Files.writeString(
                    bundleDir.resolve("SKILL.md"),
                    "---\nname: code-review\nversion: \"1.0\"\ncategory: CODE\n"
                            + "description: Code review helper\ntriggers:\n  - /review\n---\n"
                            + "# Code Review\n\nReview the code carefully.",
                    StandardCharsets.UTF_8);

            // Load metadata first
            loader.loadFromDirectory(tempDir).blockLast();

            // Reload full content
            SkillDefinition full = loader.getFullContent("code-review");
            assertNotNull(full);
            assertTrue(full.isBundle());
            assertTrue(full.hasInstructions());
            assertTrue(full.instructions().contains("Review the code carefully"));
        }
    }
}
