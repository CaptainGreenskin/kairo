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
package io.kairo.api.skill;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkillApiTest {

    @Test
    void skillDefinitionFields() {
        SkillDefinition skill =
                new SkillDefinition(
                        "code-review",
                        "1.0.0",
                        "Reviews code changes",
                        "You are a code reviewer...",
                        List.of("/review", "review this"),
                        SkillCategory.CODE);

        assertEquals("code-review", skill.name());
        assertEquals("1.0.0", skill.version());
        assertEquals("Reviews code changes", skill.description());
        assertEquals("You are a code reviewer...", skill.instructions());
        assertEquals(List.of("/review", "review this"), skill.triggerConditions());
        assertEquals(SkillCategory.CODE, skill.category());
    }

    @Test
    void allowedToolsField() {
        SkillDefinition skill =
                new SkillDefinition(
                        "name",
                        "1.0.0",
                        "desc",
                        "instructions",
                        List.of(),
                        SkillCategory.CODE,
                        null,
                        null,
                        null,
                        0,
                        List.of("Read", "Grep"));

        assertEquals(List.of("Read", "Grep"), skill.allowedTools());
    }

    @Test
    void hasToolRestrictionsTrue() {
        SkillDefinition skill =
                new SkillDefinition(
                        "name",
                        "1.0.0",
                        "desc",
                        "instructions",
                        List.of(),
                        SkillCategory.CODE,
                        null,
                        null,
                        null,
                        0,
                        List.of("Read", "Grep"));

        assertTrue(skill.hasToolRestrictions());
    }

    @Test
    void hasToolRestrictionsFalseWhenNull() {
        SkillDefinition skill =
                new SkillDefinition(
                        "name",
                        "1.0.0",
                        "desc",
                        "instructions",
                        List.of(),
                        SkillCategory.CODE,
                        null,
                        null,
                        null,
                        0,
                        null);

        assertFalse(skill.hasToolRestrictions());
    }

    @Test
    void hasToolRestrictionsFalseWhenEmpty() {
        SkillDefinition skill =
                new SkillDefinition(
                        "name",
                        "1.0.0",
                        "desc",
                        "instructions",
                        List.of(),
                        SkillCategory.CODE,
                        null,
                        null,
                        null,
                        0,
                        Collections.emptyList());

        assertFalse(skill.hasToolRestrictions());
    }

    @Test
    void backwardCompatConstructorSetsNullAllowedTools() {
        SkillDefinition skill =
                new SkillDefinition(
                        "name",
                        "1.0.0",
                        "desc",
                        "instructions",
                        List.of("trigger"),
                        SkillCategory.CODE);

        assertNull(skill.allowedTools());
        assertFalse(skill.hasToolRestrictions());
    }

    @Test
    void metadataOnlyPreservesAllowedTools() {
        SkillDefinition skill =
                new SkillDefinition(
                        "name",
                        "1.0.0",
                        "desc",
                        "instructions",
                        List.of(),
                        SkillCategory.CODE,
                        null,
                        null,
                        null,
                        0,
                        List.of("Read", "Grep"));

        SkillDefinition metadata = skill.metadataOnly();
        assertEquals(List.of("Read", "Grep"), metadata.allowedTools());
        assertTrue(metadata.hasToolRestrictions());
        assertNull(metadata.instructions());
    }

    @Test
    void skillCategoryValues() {
        SkillCategory[] values = SkillCategory.values();
        assertEquals(6, values.length);
        assertNotNull(SkillCategory.valueOf("CODE"));
        assertNotNull(SkillCategory.valueOf("DEVOPS"));
        assertNotNull(SkillCategory.valueOf("DATA"));
        assertNotNull(SkillCategory.valueOf("TESTING"));
        assertNotNull(SkillCategory.valueOf("DOCUMENTATION"));
        assertNotNull(SkillCategory.valueOf("GENERAL"));
    }
}
