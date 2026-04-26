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

import io.kairo.api.skill.SkillCategory;
import io.kairo.api.skill.SkillDefinition;
import io.kairo.api.skill.SkillRegistry;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolHandler;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.prompt.PromptBudgetFormatter;
import java.util.List;
import java.util.Map;

/**
 * Lists available skills grouped by category.
 *
 * <p>This tool only shows skill names and short descriptions (progressive disclosure). Full
 * instructions are loaded separately via {@link SkillLoadTool}.
 */
@Tool(
        name = "skill_list",
        description =
                "List available skills. Shows categories and skill names with descriptions. "
                        + "Use skill_load to activate a specific skill.",
        category = ToolCategory.SKILL)
public class SkillListTool implements ToolHandler {

    @ToolParam(
            description =
                    "Optional category filter (CODE, DEVOPS, DATA, TESTING, DOCUMENTATION, GENERAL)")
    private String category;

    @ToolParam(description = "Optional character budget for output (default: 5000)")
    private Integer budget;

    private final SkillRegistry registry;

    public SkillListTool(SkillRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ToolResult execute(Map<String, Object> input) {
        String categoryFilter = (String) input.get("category");

        int budgetChars = PromptBudgetFormatter.DEFAULT_BUDGET;
        Object budgetRaw = input.get("budget");
        if (budgetRaw != null) {
            if (budgetRaw instanceof Number num) {
                budgetChars = num.intValue();
            } else {
                try {
                    budgetChars = Integer.parseInt(budgetRaw.toString());
                } catch (NumberFormatException ignored) {
                    // keep default
                }
            }
        }

        List<SkillDefinition> skills;
        if (categoryFilter != null && !categoryFilter.isBlank()) {
            try {
                SkillCategory cat = SkillCategory.valueOf(categoryFilter.toUpperCase());
                skills = registry.listByCategory(cat);
            } catch (IllegalArgumentException e) {
                return error(
                        "Unknown category: "
                                + categoryFilter
                                + ". Valid categories: CODE, DEVOPS, DATA, TESTING, DOCUMENTATION, GENERAL");
            }
        } else {
            skills = registry.list();
        }

        if (skills.isEmpty()) {
            return new ToolResult("skill_list", "No skills registered.", false, Map.of("count", 0));
        }

        String output = PromptBudgetFormatter.formatSkills(skills, budgetChars);

        return new ToolResult("skill_list", output, false, Map.of("count", skills.size()));
    }

    private ToolResult error(String msg) {
        return new ToolResult("skill_list", msg, true, Map.of());
    }
}
