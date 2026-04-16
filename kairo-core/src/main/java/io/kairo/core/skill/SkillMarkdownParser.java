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
package io.kairo.core.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.kairo.api.skill.SkillCategory;
import io.kairo.api.skill.SkillDefinition;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Parses and serializes skill definitions in Markdown format with YAML front-matter.
 *
 * <p>Expected format:
 *
 * <pre>
 * ---
 * name: code-review
 * version: 1.0.0
 * category: CODE
 * triggers:
 *   - "review code"
 *   - "/review"
 * ---
 * # Code Review Skill
 *
 * ## Instructions
 * When performing a code review ...
 * </pre>
 */
public class SkillMarkdownParser {

    private static final String FRONT_MATTER_DELIMITER = "---";
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    /**
     * Parse a Markdown string with YAML front-matter into a {@link SkillDefinition}.
     *
     * @param markdown the raw Markdown content
     * @return the parsed skill definition
     * @throws IllegalArgumentException if the format is invalid
     */
    public SkillDefinition parse(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            throw new IllegalArgumentException("Markdown content is empty");
        }

        String trimmed = markdown.strip();
        if (!trimmed.startsWith(FRONT_MATTER_DELIMITER)) {
            throw new IllegalArgumentException("Missing YAML front-matter delimiter '---'");
        }

        // Find the closing delimiter
        int secondDelimiter =
                trimmed.indexOf(FRONT_MATTER_DELIMITER, FRONT_MATTER_DELIMITER.length());
        if (secondDelimiter < 0) {
            throw new IllegalArgumentException("Missing closing YAML front-matter delimiter '---'");
        }

        String yamlBlock =
                trimmed.substring(FRONT_MATTER_DELIMITER.length(), secondDelimiter).strip();
        String body = trimmed.substring(secondDelimiter + FRONT_MATTER_DELIMITER.length()).strip();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> frontMatter = yamlMapper.readValue(yamlBlock, Map.class);

            String name = requireString(frontMatter, "name");
            String version = getStringOrDefault(frontMatter, "version", "1.0.0");
            String category = getStringOrDefault(frontMatter, "category", "GENERAL");
            List<String> triggers = extractTriggers(frontMatter);
            List<String> pathPatterns = extractStringList(frontMatter, "path_patterns");
            List<String> requiredTools = extractStringList(frontMatter, "required_tools");
            String platform = getStringOrDefault(frontMatter, "platform", null);
            int matchScore = getIntOrDefault(frontMatter, "match_score", 0);
            List<String> allowedTools = extractStringList(frontMatter, "allowed_tools");

            // Extract description from the first paragraph of the body (after any heading)
            String description = extractDescription(body);

            return new SkillDefinition(
                    name,
                    version,
                    description,
                    body,
                    triggers,
                    parseCategory(category),
                    pathPatterns.isEmpty() ? null : pathPatterns,
                    requiredTools.isEmpty() ? null : requiredTools,
                    platform,
                    matchScore,
                    allowedTools.isEmpty() ? null : allowedTools);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid YAML front-matter: " + e.getMessage(), e);
        }
    }

    /**
     * Parse only the metadata (front-matter) without loading the full instructions body. Used for
     * progressive disclosure — listing skills without their full content.
     *
     * @param markdown the raw Markdown content
     * @return a skill definition with empty instructions
     */
    public SkillDefinition parseMetadataOnly(String markdown) {
        SkillDefinition full = parse(markdown);
        return full.metadataOnly();
    }

    /**
     * Serialize a {@link SkillDefinition} into Markdown format with YAML front-matter.
     *
     * @param skill the skill definition to serialize
     * @return the Markdown string
     */
    public String serialize(SkillDefinition skill) {
        StringBuilder sb = new StringBuilder();
        sb.append(FRONT_MATTER_DELIMITER).append('\n');
        sb.append("name: ").append(skill.name()).append('\n');
        sb.append("version: ").append(skill.version()).append('\n');
        sb.append("category: ").append(skill.category().name()).append('\n');

        if (skill.triggerConditions() != null && !skill.triggerConditions().isEmpty()) {
            sb.append("triggers:\n");
            for (String trigger : skill.triggerConditions()) {
                sb.append("  - \"").append(trigger).append("\"\n");
            }
        }

        if (skill.pathPatterns() != null && !skill.pathPatterns().isEmpty()) {
            sb.append("path_patterns:\n");
            for (String pattern : skill.pathPatterns()) {
                sb.append("  - \"").append(pattern).append("\"\n");
            }
        }

        if (skill.requiredTools() != null && !skill.requiredTools().isEmpty()) {
            sb.append("required_tools:\n");
            for (String tool : skill.requiredTools()) {
                sb.append("  - \"").append(tool).append("\"\n");
            }
        }

        if (skill.platform() != null) {
            sb.append("platform: ").append(skill.platform()).append('\n');
        }

        if (skill.matchScore() != 0) {
            sb.append("match_score: ").append(skill.matchScore()).append('\n');
        }

        if (skill.allowedTools() != null && !skill.allowedTools().isEmpty()) {
            sb.append("allowed_tools:\n");
            for (String tool : skill.allowedTools()) {
                sb.append("  - \"").append(tool).append("\"\n");
            }
        }

        sb.append(FRONT_MATTER_DELIMITER).append('\n');

        if (skill.instructions() != null && !skill.instructions().isBlank()) {
            sb.append(skill.instructions()).append('\n');
        }

        return sb.toString();
    }

    private String requireString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return value.toString();
    }

    private String getStringOrDefault(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractTriggers(Map<String, Object> frontMatter) {
        return extractStringList(frontMatter, "triggers");
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Map<String, Object> frontMatter, String key) {
        Object value = frontMatter.get(key);
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(item.toString());
            }
            return Collections.unmodifiableList(result);
        }
        return List.of(value.toString());
    }

    private int getIntOrDefault(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String extractDescription(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        // Skip heading lines (starting with #), then take the first non-blank paragraph
        String[] lines = body.split("\n");
        StringBuilder desc = new StringBuilder();
        boolean foundContent = false;

        for (String line : lines) {
            String trimmedLine = line.strip();
            if (trimmedLine.startsWith("#")) {
                // If we already found content, stop at the next heading
                if (foundContent) {
                    break;
                }
                continue;
            }
            if (trimmedLine.isEmpty()) {
                if (foundContent) {
                    break; // end of first paragraph
                }
                continue;
            }
            foundContent = true;
            if (!desc.isEmpty()) {
                desc.append(' ');
            }
            desc.append(trimmedLine);
        }

        String result = desc.toString();
        // Truncate long descriptions
        if (result.length() > 200) {
            return result.substring(0, 197) + "...";
        }
        return result;
    }

    private SkillCategory parseCategory(String category) {
        try {
            return SkillCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SkillCategory.GENERAL;
        }
    }
}
