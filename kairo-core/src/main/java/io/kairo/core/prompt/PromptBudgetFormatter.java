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

import io.kairo.api.skill.SkillDefinition;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Formats skill listings within a character budget using three-level degradation.
 *
 * <ul>
 *   <li>Level 1: Full descriptions (up to 200 chars per skill)
 *   <li>Level 2: Truncated descriptions (50 chars per skill)
 *   <li>Level 3: Name + category only (minimal)
 * </ul>
 *
 * <p>Skills are sorted by matchScore descending before formatting.
 */
public final class PromptBudgetFormatter {

    /** Default character budget for skill listings. */
    public static final int DEFAULT_BUDGET = 5000;

    private static final int TRUNCATED_DESC_LENGTH = 50;
    private static final String HEADER = "## Available Skills\n";

    private PromptBudgetFormatter() {
        // utility class
    }

    /**
     * Format the given skills within the specified character budget.
     *
     * <p>Attempts Level 1 (full), then Level 2 (truncated), then Level 3 (minimal).
     *
     * @param skills the skill definitions to format
     * @param budgetChars the maximum character count allowed
     * @return formatted skill listing, or empty string if skills is null/empty
     */
    public static String formatSkills(List<SkillDefinition> skills, int budgetChars) {
        if (skills == null || skills.isEmpty()) {
            return "";
        }

        List<SkillDefinition> sorted = skills.stream()
                .sorted(Comparator.comparingInt(SkillDefinition::matchScore).reversed())
                .collect(Collectors.toList());

        // Level 1: Full descriptions
        String full = formatFull(sorted);
        if (full.length() <= budgetChars) {
            return full;
        }

        // Level 2: Truncated descriptions (50 chars)
        String truncated = formatTruncated(sorted, TRUNCATED_DESC_LENGTH);
        if (truncated.length() <= budgetChars) {
            return truncated;
        }

        // Level 3: Name + category only
        return formatMinimal(sorted);
    }

    private static String formatFull(List<SkillDefinition> skills) {
        StringBuilder sb = new StringBuilder(HEADER);
        Map<String, List<SkillDefinition>> grouped = groupByCategory(skills);
        for (Map.Entry<String, List<SkillDefinition>> entry : grouped.entrySet()) {
            sb.append("\n### ").append(entry.getKey()).append('\n');
            for (SkillDefinition skill : entry.getValue()) {
                sb.append("- **").append(skill.name()).append("**");
                if (skill.version() != null && !skill.version().isBlank()) {
                    sb.append(" (v").append(skill.version()).append(')');
                }
                String desc = skill.description();
                if (desc != null && !desc.isBlank()) {
                    sb.append(": ").append(desc);
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static String formatTruncated(List<SkillDefinition> skills, int maxDescLength) {
        StringBuilder sb = new StringBuilder(HEADER);
        Map<String, List<SkillDefinition>> grouped = groupByCategory(skills);
        for (Map.Entry<String, List<SkillDefinition>> entry : grouped.entrySet()) {
            sb.append("\n### ").append(entry.getKey()).append('\n');
            for (SkillDefinition skill : entry.getValue()) {
                sb.append("- **").append(skill.name()).append("**");
                if (skill.version() != null && !skill.version().isBlank()) {
                    sb.append(" (v").append(skill.version()).append(')');
                }
                String desc = skill.description();
                if (desc != null && !desc.isBlank()) {
                    sb.append(": ").append(truncateDescription(desc, maxDescLength));
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static String formatMinimal(List<SkillDefinition> skills) {
        StringBuilder sb = new StringBuilder(HEADER);
        Map<String, List<SkillDefinition>> grouped = groupByCategory(skills);
        for (Map.Entry<String, List<SkillDefinition>> entry : grouped.entrySet()) {
            sb.append("\n### ").append(entry.getKey()).append('\n');
            for (SkillDefinition skill : entry.getValue()) {
                sb.append("- **").append(skill.name()).append("** [")
                        .append(skill.category().name()).append("]\n");
            }
        }
        return sb.toString();
    }

    /**
     * Groups skills by category name, sorted alphabetically by category. Within each category,
     * skills retain their original order (already sorted by matchScore descending).
     */
    private static Map<String, List<SkillDefinition>> groupByCategory(
            List<SkillDefinition> skills) {
        Map<String, List<SkillDefinition>> grouped = new TreeMap<>();
        for (SkillDefinition skill : skills) {
            String categoryName = skill.category() != null ? skill.category().name() : "GENERAL";
            grouped.computeIfAbsent(categoryName, k -> new java.util.ArrayList<>()).add(skill);
        }
        return grouped;
    }

    private static String truncateDescription(String desc, int maxLen) {
        if (desc.length() <= maxLen) {
            return desc;
        }
        return desc.substring(0, maxLen) + "...";
    }
}
