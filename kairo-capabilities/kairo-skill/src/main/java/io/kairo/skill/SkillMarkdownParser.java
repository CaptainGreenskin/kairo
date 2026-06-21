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
package io.kairo.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.kairo.api.skill.SkillCategory;
import io.kairo.api.skill.SkillDefinition;
import io.kairo.api.skill.SkillMetadata;
import io.kairo.api.skill.SkillVisibility;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(SkillMarkdownParser.class);
    private static final String FRONT_MATTER_DELIMITER = "---";
    private static final Pattern PARAM_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    /**
     * Substitutes {{key}} placeholders in skill content with provided arguments. Unmatched
     * placeholders are replaced with empty string and logged at DEBUG level.
     *
     * @param content the skill content with placeholders
     * @param args the argument map
     * @return content with placeholders substituted
     */
    public static String substituteParameters(String content, Map<String, String> args) {
        if (content == null) return null;
        if (args == null || args.isEmpty()) return content;
        return PARAM_PATTERN
                .matcher(content)
                .replaceAll(
                        match -> {
                            String key = match.group(1).trim();
                            if (!args.containsKey(key)) {
                                log.debug(
                                        "Unmatched skill parameter '{}', substituting empty", key);
                                return "";
                            }
                            return Matcher.quoteReplacement(args.get(key));
                        });
    }

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    /**
     * Characters that require quoting in YAML values (mirrors Claude Code's YAML_SPECIAL_CHARS).
     */
    private static final java.util.regex.Pattern YAML_SPECIAL =
            java.util.regex.Pattern.compile("[{}\\[\\]*&#!|>%@`]|: ");

    /**
     * Lenient YAML front-matter parser following Claude Code's two-pass strategy:
     *
     * <ol>
     *   <li>Try strict YAML parse.
     *   <li>On failure, auto-quote values containing YAML special characters, then retry.
     * </ol>
     *
     * <p>This handles bare {@code :} in description fields, glob patterns with {@code {}[]}, and
     * other characters that trip SnakeYAML without resorting to lossy line-by-line extraction.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseYamlLenient(String yamlBlock) {
        try {
            return yamlMapper.readValue(yamlBlock, Map.class);
        } catch (IOException firstError) {
            // Second pass: auto-quote problematic values and retry
            String quoted = quoteProblematicValues(yamlBlock);
            try {
                return yamlMapper.readValue(quoted, Map.class);
            } catch (IOException retryError) {
                throw new IllegalArgumentException(
                        "Invalid YAML front-matter: " + retryError.getMessage(), retryError);
            }
        }
    }

    /**
     * Pre-processes YAML text to wrap values containing special characters in double quotes. Only
     * handles simple {@code key: value} lines (not indented list items or block scalars).
     */
    private static String quoteProblematicValues(String yamlText) {
        StringBuilder result = new StringBuilder();
        for (String line : yamlText.split("\n")) {
            java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile("^([a-zA-Z_-]+):\\s+(.+)$").matcher(line);
            if (m.matches()) {
                String key = m.group(1);
                String value = m.group(2);
                // Skip already-quoted values
                if ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'"))) {
                    result.append(line);
                } else if (YAML_SPECIAL.matcher(value).find()) {
                    // Escape existing double quotes and backslashes, then wrap
                    String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
                    result.append(key).append(": \"").append(escaped).append("\"");
                } else {
                    result.append(line);
                }
            } else {
                result.append(line);
            }
            result.append('\n');
        }
        return result.toString();
    }

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

        Map<String, Object> frontMatter = parseYamlLenient(yamlBlock);

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
    }

    /**
     * Parse a Markdown string with YAML front-matter into a {@link SkillMetadata}, which includes
     * visibility and model-invocation control.
     *
     * <p>Recognized front-matter fields beyond the base {@link SkillDefinition}:
     *
     * <ul>
     *   <li>{@code visibility: VISIBLE|USER_ONLY|HIDDEN}
     *   <li>{@code disable_model_invocation: true|false}
     * </ul>
     *
     * @param markdown the raw Markdown content
     * @return the parsed skill metadata
     * @throws IllegalArgumentException if the format is invalid
     */
    public SkillMetadata parseWithMetadata(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            throw new IllegalArgumentException("Markdown content is empty");
        }

        String trimmed = markdown.strip();
        if (!trimmed.startsWith(FRONT_MATTER_DELIMITER)) {
            throw new IllegalArgumentException("Missing YAML front-matter delimiter '---'");
        }

        int secondDelimiter =
                trimmed.indexOf(FRONT_MATTER_DELIMITER, FRONT_MATTER_DELIMITER.length());
        if (secondDelimiter < 0) {
            throw new IllegalArgumentException("Missing closing YAML front-matter delimiter '---'");
        }

        String yamlBlock =
                trimmed.substring(FRONT_MATTER_DELIMITER.length(), secondDelimiter).strip();

        Map<String, Object> frontMatter = parseYamlLenient(yamlBlock);
        SkillVisibility visibility = parseVisibility(frontMatter);
        boolean disableModelInvocation =
                getBooleanOrDefault(frontMatter, "disable_model_invocation", false);
        SkillDefinition definition = parse(markdown);
        return new SkillMetadata(definition, visibility, disableModelInvocation);
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

    private SkillVisibility parseVisibility(Map<String, Object> frontMatter) {
        String value = getStringOrDefault(frontMatter, "visibility", null);
        if (value == null) {
            return SkillVisibility.VISIBLE;
        }
        try {
            return SkillVisibility.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown visibility '{}', defaulting to VISIBLE", value);
            return SkillVisibility.VISIBLE;
        }
    }

    private boolean getBooleanOrDefault(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
