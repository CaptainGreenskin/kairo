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

import io.kairo.api.exception.ToolException;
import io.kairo.api.skill.SkillDefinition;
import io.kairo.api.skill.SkillRegistry;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.core.skill.SkillChangeHistory;
import io.kairo.core.skill.SkillMarkdownParser;
import io.kairo.core.tool.ToolHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Create, edit, or delete skills with automatic history tracking.
 *
 * <p>New skills are written to the highest-priority writable search path (last non-classpath
 * directory), ensuring user custom directories take precedence over framework built-in locations.
 */
@Tool(
        name = "skill_manage",
        description = "Create, edit, or delete skills. Requires SYSTEM_CHANGE permission.",
        category = ToolCategory.SKILL,
        sideEffect = ToolSideEffect.SYSTEM_CHANGE)
public class SkillManageTool implements ToolHandler {

    @ToolParam(description = "Operation: create, edit, delete", required = true)
    private String operation;

    @ToolParam(description = "Skill name", required = true)
    private String name;

    @ToolParam(description = "Skill content in markdown format (required for create/edit)")
    private String content;

    private final SkillRegistry registry;
    private final SkillChangeHistory changeHistory;
    private final SkillMarkdownParser parser;
    private final List<String> searchPaths;
    private final boolean readonly;

    /**
     * Create a SkillManageTool with all dependencies.
     *
     * @param registry the skill registry
     * @param changeHistory the change history recorder
     * @param searchPaths configured search paths (lowest priority first)
     * @param readonly whether skill management is disabled
     */
    public SkillManageTool(
            SkillRegistry registry,
            SkillChangeHistory changeHistory,
            List<String> searchPaths,
            boolean readonly) {
        this.registry = registry;
        this.changeHistory = changeHistory;
        this.parser = new SkillMarkdownParser();
        this.searchPaths = searchPaths;
        this.readonly = readonly;
    }

    @Override
    public ToolResult execute(Map<String, Object> input) {
        if (readonly) {
            return error("Skill management is disabled in readonly mode");
        }

        String op = (String) input.get("operation");
        String skillName = (String) input.get("name");
        String skillContent = (String) input.get("content");

        if (op == null || op.isBlank()) {
            return error("Parameter 'operation' is required");
        }
        if (skillName == null || skillName.isBlank()) {
            return error("Parameter 'name' is required");
        }

        return switch (op.toLowerCase()) {
            case "create" -> doCreate(skillName, skillContent);
            case "edit" -> doEdit(skillName, skillContent);
            case "delete" -> doDelete(skillName);
            default ->
                    error("Unknown operation: " + op + ". Valid operations: create, edit, delete");
        };
    }

    private ToolResult doCreate(String skillName, String content) {
        if (content == null || content.isBlank()) {
            return error("Parameter 'content' is required for create operation");
        }

        if (registry.get(skillName).isPresent()) {
            return error("Skill '" + skillName + "' already exists. Use 'edit' to update it.");
        }

        // Validate content by parsing
        SkillDefinition skill;
        try {
            skill = parser.parse(content);
        } catch (IllegalArgumentException e) {
            return error("Invalid skill content: " + e.getMessage());
        }

        Path writablePath = resolveWritablePath();
        Path skillFile = writablePath.resolve(skillName + ".md");

        // Record history before write (empty old content for create)
        String agentName = resolveAgentName();
        changeHistory.recordChange(skillName, "create", agentName, "");

        try {
            String serialized = parser.serialize(skill);
            Files.writeString(skillFile, serialized, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return error("Failed to write skill file: " + e.getMessage());
        }

        registry.register(skill);

        return success("Skill '" + skillName + "' created at " + skillFile);
    }

    private ToolResult doEdit(String skillName, String content) {
        if (content == null || content.isBlank()) {
            return error("Parameter 'content' is required for edit operation");
        }

        var existing = registry.get(skillName).orElse(null);
        if (existing == null) {
            return error("Skill '" + skillName + "' does not exist. Use 'create' to add it.");
        }

        // Validate new content by parsing
        SkillDefinition updated;
        try {
            updated = parser.parse(content);
        } catch (IllegalArgumentException e) {
            return error("Invalid skill content: " + e.getMessage());
        }

        // Record history with old content
        String agentName = resolveAgentName();
        String oldContent = existing.hasInstructions() ? parser.serialize(existing) : "";
        changeHistory.recordChange(skillName, "edit", agentName, oldContent);

        // Write to writable path
        Path writablePath = resolveWritablePath();
        Path skillFile = writablePath.resolve(skillName + ".md");

        try {
            String serialized = parser.serialize(updated);
            Files.writeString(skillFile, serialized, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return error("Failed to write skill file: " + e.getMessage());
        }

        // Re-register (update) in registry
        registry.register(updated);

        return success("Skill '" + skillName + "' updated at " + skillFile);
    }

    private ToolResult doDelete(String skillName) {
        var existing = registry.get(skillName).orElse(null);
        if (existing == null) {
            return error("Skill '" + skillName + "' does not exist.");
        }

        // Record history with old content
        String agentName = resolveAgentName();
        String oldContent = existing.hasInstructions() ? parser.serialize(existing) : "";
        changeHistory.recordChange(skillName, "delete", agentName, oldContent);

        // Delete file from writable path
        Path writablePath = resolveWritablePath();
        Path skillFile = writablePath.resolve(skillName + ".md");
        try {
            Files.deleteIfExists(skillFile);
        } catch (IOException e) {
            return error("Failed to delete skill file: " + e.getMessage());
        }

        registry.unregister(skillName);

        return success("Skill '" + skillName + "' deleted.");
    }

    /**
     * Select the highest-priority writable path (reversed iteration). New skills land in user
     * custom directory, not framework built-in.
     */
    Path resolveWritablePath() {
        // Java 17 baseline: avoid List#reversed() (JDK 21). Walk indices in reverse instead.
        java.util.List<String> reversed = new java.util.ArrayList<>(searchPaths);
        java.util.Collections.reverse(reversed);
        return reversed.stream()
                .filter(p -> !p.startsWith("classpath:"))
                .map(
                        p -> {
                            if (p.startsWith("~/")) {
                                return Path.of(System.getProperty("user.home"))
                                        .resolve(p.substring(2));
                            }
                            return Path.of(p);
                        })
                .filter(Files::isDirectory)
                .findFirst()
                .orElseThrow(() -> new ToolException("No writable skill directory configured"));
    }

    private String resolveAgentName() {
        return "unknown";
    }

    private ToolResult error(String msg) {
        return new ToolResult("skill_manage", msg, true, Map.of());
    }

    private ToolResult success(String msg) {
        return new ToolResult("skill_manage", msg, false, Map.of());
    }
}
