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
package io.kairo.core.prompt;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.skill.SkillCategory;
import io.kairo.api.skill.SkillDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromptBudgetFormatterTest {

    @Test
    void emptySkillsReturnsEmpty() {
        assertEquals("", PromptBudgetFormatter.formatSkills(null, 5000));
        assertEquals("", PromptBudgetFormatter.formatSkills(List.of(), 5000));
    }

    @Test
    void singleSkillWithinBudget() {
        SkillDefinition skill =
                new SkillDefinition(
                        "commit",
                        "1.0.0",
                        "Git commit helper",
                        null,
                        List.of("trigger"),
                        SkillCategory.CODE);

        String result = PromptBudgetFormatter.formatSkills(List.of(skill), 10000);

        assertTrue(result.contains("## Available Skills"));
        assertTrue(result.contains("### CODE"));
        assertTrue(result.contains("- **commit** (v1.0.0): Git commit helper"));
    }

    @Test
    void multipleSkillsWithinBudget() {
        List<SkillDefinition> skills =
                List.of(
                        new SkillDefinition(
                                "commit",
                                "1.0.0",
                                "Git commit helper",
                                null,
                                List.of(),
                                SkillCategory.CODE),
                        new SkillDefinition(
                                "deploy",
                                "2.0.0",
                                "Deploy assistant",
                                null,
                                List.of(),
                                SkillCategory.DEVOPS));

        String result = PromptBudgetFormatter.formatSkills(skills, 10000);

        // Level 1: full descriptions
        assertTrue(result.contains("- **commit** (v1.0.0): Git commit helper"));
        assertTrue(result.contains("- **deploy** (v2.0.0): Deploy assistant"));
    }

    @Test
    void budgetExceededFallsToTruncated() {
        // Create skills with long descriptions that exceed budget at Level 1
        List<SkillDefinition> skills =
                List.of(
                        new SkillDefinition(
                                "skill-a",
                                "1.0.0",
                                "A".repeat(200),
                                null,
                                List.of(),
                                SkillCategory.CODE),
                        new SkillDefinition(
                                "skill-b",
                                "1.0.0",
                                "B".repeat(200),
                                null,
                                List.of(),
                                SkillCategory.CODE),
                        new SkillDefinition(
                                "skill-c",
                                "1.0.0",
                                "C".repeat(200),
                                null,
                                List.of(),
                                SkillCategory.DEVOPS));

        // Budget too small for full but large enough for truncated (50-char descs)
        String full = PromptBudgetFormatter.formatSkills(skills, 10000);
        int truncatedBudget = full.length() - 100; // just under full size

        String result = PromptBudgetFormatter.formatSkills(skills, truncatedBudget);

        // Should fall to Level 2: truncated at 50 chars with "..."
        assertTrue(result.contains("..."));
        // Descriptions should be truncated, not the full 200 chars
        assertFalse(result.contains("A".repeat(200)));
    }

    @Test
    void budgetExceededFallsToMinimal() {
        List<SkillDefinition> skills =
                List.of(
                        new SkillDefinition(
                                "skill-a",
                                "1.0.0",
                                "A".repeat(200),
                                null,
                                List.of(),
                                SkillCategory.CODE),
                        new SkillDefinition(
                                "skill-b",
                                "1.0.0",
                                "B".repeat(200),
                                null,
                                List.of(),
                                SkillCategory.DEVOPS));

        // Very tight budget forces Level 3
        String result = PromptBudgetFormatter.formatSkills(skills, 10);

        // Level 3: name + category only, no description
        assertTrue(result.contains("- **skill-a** [CODE]"));
        assertTrue(result.contains("- **skill-b** [DEVOPS]"));
        assertFalse(result.contains("A".repeat(50)));
    }

    @Test
    void matchScoreSorting() {
        SkillDefinition high =
                new SkillDefinition(
                        "high",
                        "1.0.0",
                        "desc",
                        null,
                        List.of(),
                        SkillCategory.CODE,
                        null,
                        null,
                        null,
                        100);
        SkillDefinition low =
                new SkillDefinition(
                        "low",
                        "1.0.0",
                        "desc",
                        null,
                        List.of(),
                        SkillCategory.CODE,
                        null,
                        null,
                        null,
                        10);

        String result = PromptBudgetFormatter.formatSkills(List.of(low, high), 10000);

        int highIdx = result.indexOf("**high**");
        int lowIdx = result.indexOf("**low**");
        assertTrue(highIdx >= 0, "'high' should appear in output");
        assertTrue(lowIdx >= 0, "'low' should appear in output");
        assertTrue(highIdx < lowIdx, "'high' (score=100) should appear before 'low' (score=10)");
    }

    @Test
    void categoriesGroupedAlphabetically() {
        List<SkillDefinition> skills =
                List.of(
                        new SkillDefinition(
                                "z-skill",
                                "1.0.0",
                                "desc z",
                                null,
                                List.of(),
                                SkillCategory.TESTING),
                        new SkillDefinition(
                                "a-skill", "1.0.0", "desc a", null, List.of(), SkillCategory.CODE));

        String result = PromptBudgetFormatter.formatSkills(skills, 10000);

        int codeIdx = result.indexOf("### CODE");
        int testIdx = result.indexOf("### TESTING");
        assertTrue(codeIdx >= 0);
        assertTrue(testIdx >= 0);
        assertTrue(codeIdx < testIdx, "CODE should appear before TESTING (alphabetical)");
    }

    @Test
    void nullDescriptionHandled() {
        SkillDefinition skill =
                new SkillDefinition("no-desc", "1.0.0", null, null, List.of(), SkillCategory.CODE);

        String result = PromptBudgetFormatter.formatSkills(List.of(skill), 10000);

        assertTrue(result.contains("**no-desc**"));
        // Should not crash, and no trailing ": " without description
        assertFalse(result.contains("**no-desc** (v1.0.0): "));
    }

    @Test
    void defaultBudgetConstant() {
        assertEquals(5000, PromptBudgetFormatter.DEFAULT_BUDGET);
    }
}
