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

import io.kairo.api.skill.SkillDefinition;
import io.kairo.api.skill.SkillRegistry;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.skill.SkillLoader;
import io.kairo.core.skill.SkillMarkdownParser;
import io.kairo.core.tool.ToolHandler;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads and activates a skill by name.
 *
 * <p>This tool implements progressive disclosure: the skill's full instructions are only returned
 * when explicitly requested via this tool, rather than being injected into the system prompt
 * upfront.
 */
@Tool(
        name = "skill_load",
        description =
                "Load and activate a skill by name. Skills provide specialized instructions for specific tasks. "
                        + "Use skill_list first to see available skills.",
        category = ToolCategory.SKILL)
public class SkillLoadTool implements ToolHandler {

    @ToolParam(description = "Name of the skill to load", required = true)
    private String name;

    @ToolParam(description = "Optional arguments for parameter substitution")
    private Map<String, String> args;

    private final SkillRegistry registry;
    private final SkillLoader skillLoader;

    /** Create with a registry only (no progressive-disclosure reload support). */
    public SkillLoadTool(SkillRegistry registry) {
        this(registry, null);
    }

    /**
     * Create with both registry and loader. The loader enables on-demand full-content reload for
     * skills that were loaded metadata-only.
     */
    public SkillLoadTool(SkillRegistry registry, SkillLoader skillLoader) {
        this.registry = registry;
        this.skillLoader = skillLoader;
    }

    @Override
    public ToolResult execute(Map<String, Object> input) {
        String skillName = (String) input.get("name");
        if (skillName == null || skillName.isBlank()) {
            return error("Parameter 'name' is required");
        }

        // Try to load full content via SkillLoader (progressive disclosure reload),
        // falling back to direct registry lookup.
        SkillDefinition skill = null;
        if (skillLoader != null) {
            skill = skillLoader.getFullContent(skillName);
        }
        if (skill == null) {
            skill = registry.get(skillName).orElse(null);
        }
        if (skill == null) {
            // Try to suggest similar skills
            String suggestions =
                    registry.list().stream()
                            .map(SkillDefinition::name)
                            .filter(n -> n.contains(skillName) || skillName.contains(n))
                            .reduce((a, b) -> a + ", " + b)
                            .map(s -> " Did you mean: " + s + "?")
                            .orElse("");

            return error("Skill not found: " + skillName + "." + suggestions);
        }

        String instructions = skill.instructions();
        if (instructions == null || instructions.isBlank()) {
            return error("Skill '" + skillName + "' has no instructions content");
        }

        // Parameter substitution — order matters:
        // 1. ${SKILL_DIR} first (so arg values can reference resolved paths)
        if (skill.isBundle() && skill.bundleRoot() != null) {
            instructions =
                    instructions.replace(
                            "${SKILL_DIR}", skill.bundleRoot().toAbsolutePath().toString());
        }
        // 2. {{arg}} substitution second
        @SuppressWarnings("unchecked")
        Map<String, String> argMap =
                (input.get("args") instanceof Map<?, ?> m) ? (Map<String, String>) m : null;
        if (argMap != null && !argMap.isEmpty()) {
            instructions = SkillMarkdownParser.substituteParameters(instructions, argMap);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Skill loaded: ")
                .append(skill.name())
                .append(" (v")
                .append(skill.version())
                .append(")\n")
                .append("Category: ")
                .append(skill.category().name())
                .append("\n\n")
                .append(instructions);

        if (skill.hasToolRestrictions()) {
            sb.append(
                            "\n\n---\n\u26A0\uFE0F TOOL RESTRICTION: While this skill is active, you may ONLY use these tools: ")
                    .append(String.join(", ", skill.allowedTools()))
                    .append(". All other tools will be blocked.\n");
        }

        if (skill.isBundle()) {
            var resources = skill.listResources();
            if (!resources.isEmpty()) {
                sb.append("\n\nAvailable resources:\n");
                resources.forEach(r -> sb.append("- ").append(r).append("\n"));
            }
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("skill", skill.name());
        metadata.put("version", skill.version());
        metadata.put("category", skill.category().name());
        if (skill.hasToolRestrictions()) {
            metadata.put("allowedTools", skill.allowedTools());
        }

        return new ToolResult("skill_load", sb.toString(), false, metadata);
    }

    private ToolResult error(String msg) {
        return new ToolResult("skill_load", msg, true, Map.of());
    }
}
