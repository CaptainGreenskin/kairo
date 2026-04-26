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
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConditionalActivationTest {

    private final DefaultTriggerGuard guard = new DefaultTriggerGuard();

    // --- SkillDefinition new fields ---

    @Test
    void backwardCompatConstructor_setsDefaults() {
        SkillDefinition skill =
                new SkillDefinition("test", "1.0", "desc", "body", List.of(), SkillCategory.CODE);
        assertNull(skill.pathPatterns());
        assertNull(skill.requiredTools());
        assertNull(skill.platform());
        assertEquals(0, skill.matchScore());
        assertFalse(skill.isConditional());
    }

    @Test
    void fullConstructor_setsAllFields() {
        SkillDefinition skill =
                new SkillDefinition(
                        "test",
                        "1.0",
                        "desc",
                        "body",
                        List.of(),
                        SkillCategory.CODE,
                        List.of("src/**/*.java"),
                        List.of("bash"),
                        "macos",
                        10);
        assertEquals(List.of("src/**/*.java"), skill.pathPatterns());
        assertEquals(List.of("bash"), skill.requiredTools());
        assertEquals("macos", skill.platform());
        assertEquals(10, skill.matchScore());
        assertTrue(skill.isConditional());
    }

    @Test
    void hasInstructions_nullReturnsFalse() {
        SkillDefinition skill =
                new SkillDefinition("test", "1.0", "desc", null, List.of(), SkillCategory.CODE);
        assertFalse(skill.hasInstructions());
    }

    @Test
    void hasInstructions_emptyReturnsFalse() {
        SkillDefinition skill =
                new SkillDefinition("test", "1.0", "desc", "", List.of(), SkillCategory.CODE);
        assertFalse(skill.hasInstructions());
    }

    @Test
    void hasInstructions_withContentReturnsTrue() {
        SkillDefinition skill =
                new SkillDefinition(
                        "test", "1.0", "desc", "do stuff", List.of(), SkillCategory.CODE);
        assertTrue(skill.hasInstructions());
    }

    @Test
    void metadataOnly_stripsInstructions() {
        SkillDefinition full =
                new SkillDefinition(
                        "test",
                        "1.0",
                        "desc",
                        "full body",
                        List.of("trigger"),
                        SkillCategory.CODE,
                        List.of("*.java"),
                        List.of("bash"),
                        "linux",
                        5);
        SkillDefinition meta = full.metadataOnly();
        assertNull(meta.instructions());
        assertFalse(meta.hasInstructions());
        // All other fields preserved
        assertEquals("test", meta.name());
        assertEquals(List.of("*.java"), meta.pathPatterns());
        assertEquals(List.of("bash"), meta.requiredTools());
        assertEquals("linux", meta.platform());
        assertEquals(5, meta.matchScore());
    }

    // --- Conditional activation via DefaultTriggerGuard.meetsConditions ---

    @Test
    void meetsConditions_noConditions_returnsTrue() {
        SkillDefinition skill =
                new SkillDefinition("test", "1.0", "desc", "body", List.of(), SkillCategory.CODE);
        assertTrue(guard.meetsConditions(skill, null, null));
    }

    @Test
    void meetsConditions_requiredToolsPresent_returnsTrue() {
        SkillDefinition skill =
                new SkillDefinition(
                        "test",
                        "1.0",
                        "desc",
                        "body",
                        List.of(),
                        SkillCategory.CODE,
                        null,
                        List.of("bash", "read"),
                        null,
                        0);
        assertTrue(guard.meetsConditions(skill, null, Set.of("bash", "read", "write")));
    }

    @Test
    void meetsConditions_requiredToolsMissing_returnsFalse() {
        SkillDefinition skill =
                new SkillDefinition(
                        "test",
                        "1.0",
                        "desc",
                        "body",
                        List.of(),
                        SkillCategory.CODE,
                        null,
                        List.of("bash", "docker"),
                        null,
                        0);
        assertFalse(guard.meetsConditions(skill, null, Set.of("bash", "read")));
    }

    @Test
    void meetsConditions_pathPatternMatches_returnsTrue() {
        SkillDefinition skill =
                new SkillDefinition(
                        "test",
                        "1.0",
                        "desc",
                        "body",
                        List.of(),
                        SkillCategory.CODE,
                        List.of("src/**/*.java"),
                        null,
                        null,
                        0);
        assertTrue(guard.meetsConditions(skill, "src/main/Foo.java", null));
    }

    @Test
    void meetsConditions_pathPatternNoMatch_returnsFalse() {
        SkillDefinition skill =
                new SkillDefinition(
                        "test",
                        "1.0",
                        "desc",
                        "body",
                        List.of(),
                        SkillCategory.CODE,
                        List.of("src/**/*.java"),
                        null,
                        null,
                        0);
        assertFalse(guard.meetsConditions(skill, "docs/README.md", null));
    }

    @Test
    void meetsConditions_pathPatternNoActiveFile_returnsTrue() {
        // If no file is being operated on, path condition is not checked
        SkillDefinition skill =
                new SkillDefinition(
                        "test",
                        "1.0",
                        "desc",
                        "body",
                        List.of(),
                        SkillCategory.CODE,
                        List.of("src/**/*.java"),
                        null,
                        null,
                        0);
        assertTrue(guard.meetsConditions(skill, null, null));
    }

    @Test
    void meetsConditions_platformMismatch_returnsFalse() {
        // This test checks that a non-matching platform returns false.
        // We can't control os.name in a unit test, so we test with a platform
        // that definitely doesn't match the current OS.
        String currentOs = System.getProperty("os.name", "").toLowerCase();
        String wrongPlatform;
        if (currentOs.contains("mac")) {
            wrongPlatform = "linux";
        } else if (currentOs.contains("linux")) {
            wrongPlatform = "windows";
        } else {
            wrongPlatform = "linux";
        }

        SkillDefinition skill =
                new SkillDefinition(
                        "test",
                        "1.0",
                        "desc",
                        "body",
                        List.of(),
                        SkillCategory.CODE,
                        null,
                        null,
                        wrongPlatform,
                        0);
        assertFalse(guard.meetsConditions(skill, null, null));
    }

    @Test
    void meetsConditions_nullSkill_returnsTrue() {
        assertTrue(guard.meetsConditions(null, null, null));
    }
}
