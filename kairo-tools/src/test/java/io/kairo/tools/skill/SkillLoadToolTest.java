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
package io.kairo.tools.skill;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.kairo.api.skill.SkillCategory;
import io.kairo.api.skill.SkillDefinition;
import io.kairo.api.skill.SkillRegistry;
import io.kairo.api.tool.ToolResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SkillLoadToolTest {

    private SkillRegistry registry;
    private SkillLoadTool tool;

    @BeforeEach
    void setUp() {
        registry = mock(SkillRegistry.class);
        tool = new SkillLoadTool(registry);
    }

    @Test
    void loadExistingSkill() {
        SkillDefinition skill =
                new SkillDefinition(
                        "commit",
                        "1.0",
                        "Git commit helper",
                        "Follow conventional commits...",
                        List.of("/commit"),
                        SkillCategory.CODE);
        when(registry.get("commit")).thenReturn(Optional.of(skill));

        ToolResult result = tool.execute(Map.of("name", "commit"));
        assertFalse(result.isError());
        assertTrue(result.content().contains("commit"));
        assertTrue(result.content().contains("v1.0"));
        assertTrue(result.content().contains("Follow conventional commits"));
    }

    @Test
    void loadNonExistentSkill() {
        when(registry.get("unknown")).thenReturn(Optional.empty());
        when(registry.list()).thenReturn(List.of());

        ToolResult result = tool.execute(Map.of("name", "unknown"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("Skill not found"));
    }

    @Test
    void loadSkillWithSuggestions() {
        // The suggestion filter is: n.contains(skillName) || skillName.contains(n)
        // "commit" contains "com" → true, so searching for "com" suggests "commit"
        when(registry.get("com")).thenReturn(Optional.empty());
        SkillDefinition similar =
                new SkillDefinition(
                        "commit", "1.0", "desc", "instructions", List.of(), SkillCategory.CODE);
        when(registry.list()).thenReturn(List.of(similar));

        ToolResult result = tool.execute(Map.of("name", "com"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("Did you mean"));
        assertTrue(result.content().contains("commit"));
    }

    @Test
    void loadSkillWithBlankInstructions() {
        SkillDefinition skill =
                new SkillDefinition(
                        "empty-skill", "1.0", "desc", "", List.of(), SkillCategory.GENERAL);
        when(registry.get("empty-skill")).thenReturn(Optional.of(skill));

        ToolResult result = tool.execute(Map.of("name", "empty-skill"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("no instructions"));
    }

    @Test
    void missingNameParameter() {
        ToolResult result = tool.execute(Map.of());
        assertTrue(result.isError());
        assertTrue(result.content().contains("'name' is required"));
    }

    @Test
    void blankNameParameter() {
        ToolResult result = tool.execute(Map.of("name", "  "));
        assertTrue(result.isError());
    }

    @Test
    void loadSkillWithAllowedToolsIncludesMetadata() {
        SkillDefinition skill =
                new SkillDefinition(
                        "restricted", "1.0", "desc", "Do restricted things",
                        List.of(), SkillCategory.CODE, null, null, null, 0,
                        List.of("Read", "Grep"));
        when(registry.get("restricted")).thenReturn(Optional.of(skill));

        ToolResult result = tool.execute(Map.of("name", "restricted"));
        assertFalse(result.isError());
        assertTrue(result.metadata().containsKey("allowedTools"));
        assertEquals(List.of("Read", "Grep"), result.metadata().get("allowedTools"));
    }

    @Test
    void loadSkillWithAllowedToolsAppendsRestrictionNotice() {
        SkillDefinition skill =
                new SkillDefinition(
                        "restricted", "1.0", "desc", "Do restricted things",
                        List.of(), SkillCategory.CODE, null, null, null, 0,
                        List.of("Read", "Grep"));
        when(registry.get("restricted")).thenReturn(Optional.of(skill));

        ToolResult result = tool.execute(Map.of("name", "restricted"));
        assertFalse(result.isError());
        assertTrue(result.content().contains("TOOL RESTRICTION"));
        assertTrue(result.content().contains("Read"));
        assertTrue(result.content().contains("Grep"));
    }

    @Test
    void loadSkillWithoutAllowedToolsNoRestriction() {
        SkillDefinition skill =
                new SkillDefinition(
                        "normal", "1.0", "desc", "Do normal things",
                        List.of(), SkillCategory.CODE);
        when(registry.get("normal")).thenReturn(Optional.of(skill));

        ToolResult result = tool.execute(Map.of("name", "normal"));
        assertFalse(result.isError());
        assertFalse(result.content().contains("TOOL RESTRICTION"));
        assertFalse(result.metadata().containsKey("allowedTools"));
    }
}
