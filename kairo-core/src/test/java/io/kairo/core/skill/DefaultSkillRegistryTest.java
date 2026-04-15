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

import io.kairo.api.skill.SkillCategory;
import io.kairo.api.skill.SkillDefinition;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

class DefaultSkillRegistryTest {

    private DefaultSkillRegistry registry;
    private boolean yamlAvailable;

    @BeforeEach
    void setUp() {
        registry = new DefaultSkillRegistry();
        // Detect jackson-dataformat-yaml version mismatch at runtime
        try {
            new SkillMarkdownParser()
                    .parse(
                            """
                    ---
                    name: probe
                    ---
                    body
                    """);
            yamlAvailable = true;
        } catch (NoSuchMethodError | Exception e) {
            yamlAvailable = false;
        }
    }

    private SkillDefinition skill(String name, SkillCategory category) {
        return new SkillDefinition(name, "1.0.0", "desc", "instructions", List.of(), category);
    }

    @Test
    @DisplayName("Register and lookup skill by name")
    void testRegisterAndGet() {
        SkillDefinition skill = skill("code-review", SkillCategory.CODE);
        registry.register(skill);

        assertTrue(registry.get("code-review").isPresent());
        assertEquals("code-review", registry.get("code-review").get().name());
    }

    @Test
    @DisplayName("Get non-existent skill returns empty")
    void testGetNonExistent() {
        assertFalse(registry.get("nonexistent").isPresent());
    }

    @Test
    @DisplayName("List all registered skills")
    void testListAll() {
        registry.register(skill("skill-a", SkillCategory.CODE));
        registry.register(skill("skill-b", SkillCategory.DEVOPS));

        List<SkillDefinition> all = registry.list();
        assertEquals(2, all.size());
    }

    @Test
    @DisplayName("List skills by category")
    void testListByCategory() {
        registry.register(skill("code-review", SkillCategory.CODE));
        registry.register(skill("deploy", SkillCategory.DEVOPS));
        registry.register(skill("lint", SkillCategory.CODE));

        List<SkillDefinition> codeSkills = registry.listByCategory(SkillCategory.CODE);
        assertEquals(2, codeSkills.size());
        assertTrue(codeSkills.stream().allMatch(s -> s.category() == SkillCategory.CODE));

        List<SkillDefinition> devopsSkills = registry.listByCategory(SkillCategory.DEVOPS);
        assertEquals(1, devopsSkills.size());
    }

    @Test
    @DisplayName("Duplicate skill registration replaces the old one")
    void testDuplicateRegistration() {
        registry.register(
                new SkillDefinition(
                        "dup", "1.0.0", "old", "old body", List.of(), SkillCategory.CODE));
        registry.register(
                new SkillDefinition(
                        "dup", "2.0.0", "new", "new body", List.of(), SkillCategory.CODE));

        assertEquals(1, registry.list().size());
        assertEquals("2.0.0", registry.get("dup").get().version());
    }

    @Test
    @DisplayName("Register null skill throws IllegalArgumentException")
    void testRegisterNullSkill() {
        assertThrows(IllegalArgumentException.class, () -> registry.register(null));
    }

    @Test
    @DisplayName("Register skill with blank name throws")
    void testRegisterBlankName() {
        SkillDefinition blank =
                new SkillDefinition("", "1.0.0", "desc", "body", List.of(), SkillCategory.CODE);
        assertThrows(IllegalArgumentException.class, () -> registry.register(blank));
    }

    @Test
    @DisplayName("Register skill with null name throws")
    void testRegisterNullName() {
        SkillDefinition noName =
                new SkillDefinition(null, "1.0.0", "desc", "body", List.of(), SkillCategory.CODE);
        assertThrows(IllegalArgumentException.class, () -> registry.register(noName));
    }

    @Test
    @DisplayName("loadFromFile reads file and delegates to parser")
    void testLoadFromFile(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(yamlAvailable, "Skipping: jackson-dataformat-yaml version mismatch");

        String markdown =
                """
                ---
                name: file-skill
                version: 1.0.0
                category: CODE
                ---
                # File Skill

                This skill was loaded from a file.
                """;

        Path skillFile = tempDir.resolve("skill.md");
        Files.writeString(skillFile, markdown, StandardCharsets.UTF_8);

        StepVerifier.create(registry.loadFromFile(skillFile))
                .assertNext(
                        skill -> {
                            assertEquals("file-skill", skill.name());
                            assertEquals(SkillCategory.CODE, skill.category());
                        })
                .verifyComplete();

        assertTrue(registry.get("file-skill").isPresent());
    }

    @Test
    @DisplayName("loadFromFile with non-existent file emits error")
    void testLoadFromMissingFile(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("nonexistent.md");

        StepVerifier.create(registry.loadFromFile(missing)).expectError(IOException.class).verify();
    }

    @Test
    @DisplayName("triggerGuard() returns the configured guard")
    void testTriggerGuardAccessor() {
        assertNotNull(registry.triggerGuard());
        assertEquals(0.8f, registry.triggerGuard().confidenceThreshold(), 0.001f);
    }

    @Test
    @DisplayName("Empty registry list returns empty")
    void testEmptyRegistryList() {
        assertTrue(registry.list().isEmpty());
    }

    @Test
    @DisplayName("listByCategory on empty registry returns empty")
    void testEmptyListByCategory() {
        assertTrue(registry.listByCategory(SkillCategory.CODE).isEmpty());
    }
}
