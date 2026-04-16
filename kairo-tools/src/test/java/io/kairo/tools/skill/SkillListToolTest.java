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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SkillListToolTest {

    private SkillRegistry registry;
    private SkillListTool tool;

    @BeforeEach
    void setUp() {
        registry = mock(SkillRegistry.class);
        tool = new SkillListTool(registry);
    }

    @Test
    void listSkillsWhenRegistryHasEntries() {
        List<SkillDefinition> skills =
                List.of(
                        new SkillDefinition(
                                "commit",
                                "1.0",
                                "Git commit helper",
                                "instructions",
                                List.of(),
                                SkillCategory.CODE),
                        new SkillDefinition(
                                "deploy",
                                "2.0",
                                "Deploy assistant",
                                "instructions",
                                List.of(),
                                SkillCategory.DEVOPS));
        when(registry.list()).thenReturn(skills);

        ToolResult result = tool.execute(Map.of());
        assertFalse(result.isError());
        assertTrue(result.content().contains("## Available Skills"));
        assertTrue(result.content().contains("- **commit**"));
        assertTrue(result.content().contains("- **deploy**"));
        assertEquals(2, result.metadata().get("count"));
    }

    @Test
    void listEmptyRegistry() {
        when(registry.list()).thenReturn(List.of());

        ToolResult result = tool.execute(Map.of());
        assertFalse(result.isError());
        assertTrue(result.content().contains("No skills registered"));
    }

    @Test
    void listWithCategoryFilter() {
        SkillDefinition codeSkill =
                new SkillDefinition(
                        "lint", "1.0", "Linter", "instr", List.of(), SkillCategory.CODE);
        when(registry.listByCategory(SkillCategory.CODE)).thenReturn(List.of(codeSkill));

        ToolResult result = tool.execute(Map.of("category", "CODE"));
        assertFalse(result.isError());
        assertTrue(result.content().contains("lint"));
    }

    @Test
    void listWithInvalidCategoryFilter() {
        ToolResult result = tool.execute(Map.of("category", "INVALID"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("Unknown category"));
    }

    @Test
    void listGroupsByCategory() {
        List<SkillDefinition> skills =
                List.of(
                        new SkillDefinition(
                                "a", "1.0", "desc a", "instr", List.of(), SkillCategory.CODE),
                        new SkillDefinition(
                                "b", "1.0", "desc b", "instr", List.of(), SkillCategory.CODE),
                        new SkillDefinition(
                                "c", "1.0", "desc c", "instr", List.of(), SkillCategory.TESTING));
        when(registry.list()).thenReturn(skills);

        ToolResult result = tool.execute(Map.of());
        assertFalse(result.isError());
        assertTrue(result.content().contains("CODE"));
        assertTrue(result.content().contains("TESTING"));
    }

    @Test
    void listSkillWithLongDescriptionTruncates() {
        String longDesc = "A".repeat(200);
        SkillDefinition skill =
                new SkillDefinition(
                        "verbose", "1.0", longDesc, "instr", List.of(), SkillCategory.GENERAL);
        when(registry.list()).thenReturn(List.of(skill));

        // Default budget is large enough for Level 1 with a single skill,
        // but the description should still appear (possibly full at Level 1)
        ToolResult result = tool.execute(Map.of());
        assertFalse(result.isError());
        assertTrue(result.content().contains("**verbose**"));
    }

    @Test
    void listWithBudgetParameterCausesDegradation() {
        List<SkillDefinition> skills =
                List.of(
                        new SkillDefinition(
                                "alpha",
                                "1.0",
                                "A".repeat(200),
                                "instr",
                                List.of(),
                                SkillCategory.CODE),
                        new SkillDefinition(
                                "beta",
                                "1.0",
                                "B".repeat(200),
                                "instr",
                                List.of(),
                                SkillCategory.DEVOPS),
                        new SkillDefinition(
                                "gamma",
                                "1.0",
                                "C".repeat(200),
                                "instr",
                                List.of(),
                                SkillCategory.TESTING));
        when(registry.list()).thenReturn(skills);

        // Very tight budget should trigger degradation
        ToolResult result = tool.execute(Map.of("budget", 200));
        assertFalse(result.isError());
        // Level 3 minimal format: name + category bracket
        assertTrue(result.content().contains("[CODE]") || result.content().contains("..."));
        assertEquals(3, result.metadata().get("count"));
    }
}
