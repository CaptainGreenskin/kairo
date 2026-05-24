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
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillLoadToolTest {

    private static final ToolContext CTX = new ToolContext("a", "s", Map.of());

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

        ToolResult result = tool.execute(Map.of("name", "commit"), CTX).block();
        assertFalse(result.isError());
        assertTrue(result.content().contains("commit"));
        assertTrue(result.content().contains("v1.0"));
        assertTrue(result.content().contains("Follow conventional commits"));
    }

    @Test
    void loadNonExistentSkill() {
        when(registry.get("unknown")).thenReturn(Optional.empty());
        when(registry.list()).thenReturn(List.of());

        ToolResult result = tool.execute(Map.of("name", "unknown"), CTX).block();
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

        ToolResult result = tool.execute(Map.of("name", "com"), CTX).block();
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

        ToolResult result = tool.execute(Map.of("name", "empty-skill"), CTX).block();
        assertTrue(result.isError());
        assertTrue(result.content().contains("no instructions"));
    }

    @Test
    void missingNameParameter() {
        ToolResult result = tool.execute(Map.of(), CTX).block();
        assertTrue(result.isError());
        assertTrue(result.content().contains("'name' is required"));
    }

    @Test
    void blankNameParameter() {
        ToolResult result = tool.execute(Map.of("name", "  "), CTX).block();
        assertTrue(result.isError());
    }

    @Test
    void loadSkillWithAllowedToolsIncludesMetadata() {
        SkillDefinition skill =
                new SkillDefinition(
                        "restricted",
                        "1.0",
                        "desc",
                        "Do restricted things",
                        List.of(),
                        SkillCategory.CODE,
                        null,
                        null,
                        null,
                        0,
                        List.of("Read", "Grep"));
        when(registry.get("restricted")).thenReturn(Optional.of(skill));

        ToolResult result = tool.execute(Map.of("name", "restricted"), CTX).block();
        assertFalse(result.isError());
        assertTrue(result.metadata().containsKey("allowedTools"));
        assertEquals(List.of("Read", "Grep"), result.metadata().get("allowedTools"));
    }

    @Test
    void loadSkillWithAllowedToolsAppendsRestrictionNotice() {
        SkillDefinition skill =
                new SkillDefinition(
                        "restricted",
                        "1.0",
                        "desc",
                        "Do restricted things",
                        List.of(),
                        SkillCategory.CODE,
                        null,
                        null,
                        null,
                        0,
                        List.of("Read", "Grep"));
        when(registry.get("restricted")).thenReturn(Optional.of(skill));

        ToolResult result = tool.execute(Map.of("name", "restricted"), CTX).block();
        assertFalse(result.isError());
        assertTrue(result.content().contains("TOOL RESTRICTION"));
        assertTrue(result.content().contains("Read"));
        assertTrue(result.content().contains("Grep"));
    }

    @Test
    void loadSkillWithoutAllowedToolsNoRestriction() {
        SkillDefinition skill =
                new SkillDefinition(
                        "normal", "1.0", "desc", "Do normal things", List.of(), SkillCategory.CODE);
        when(registry.get("normal")).thenReturn(Optional.of(skill));

        ToolResult result = tool.execute(Map.of("name", "normal"), CTX).block();
        assertFalse(result.isError());
        assertFalse(result.content().contains("TOOL RESTRICTION"));
        assertFalse(result.metadata().containsKey("allowedTools"));
    }

    @Test
    void loadBundleSkill_listsResources(@TempDir Path bundleDir) throws IOException {
        // Create bundle structure
        Files.writeString(bundleDir.resolve("SKILL.md"), "# Bundle Skill");
        Files.writeString(bundleDir.resolve("template.txt"), "template content");
        Files.writeString(bundleDir.resolve("config.json"), "{}");

        SkillDefinition bundleSkill =
                new SkillDefinition(
                        "my-bundle",
                        "1.0",
                        "A bundle skill",
                        "Bundle instructions with ${SKILL_DIR}",
                        List.of(),
                        SkillCategory.CODE,
                        null,
                        null,
                        null,
                        0,
                        null,
                        bundleDir);
        when(registry.get("my-bundle")).thenReturn(Optional.of(bundleSkill));

        ToolResult result = tool.execute(Map.of("name", "my-bundle"), CTX).block();
        assertFalse(result.isError());
        assertTrue(result.content().contains("Available resources"));
        assertTrue(result.content().contains("template.txt"));
        assertTrue(result.content().contains("config.json"));
        // ${SKILL_DIR} should be substituted with actual path
        assertTrue(result.content().contains(bundleDir.toAbsolutePath().toString()));
        assertFalse(result.content().contains("${SKILL_DIR}"));
    }

    @Test
    void loadSkillWithNullInstructions_returnsError() {
        SkillDefinition skill =
                new SkillDefinition(
                        "null-instr", "1.0", "desc", null, List.of(), SkillCategory.GENERAL);
        when(registry.get("null-instr")).thenReturn(Optional.of(skill));

        ToolResult result = tool.execute(Map.of("name", "null-instr"), CTX).block();
        assertTrue(result.isError());
        assertTrue(result.content().contains("no instructions"));
    }
}
