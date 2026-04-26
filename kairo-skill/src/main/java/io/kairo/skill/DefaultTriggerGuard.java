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

import io.kairo.api.skill.SkillDefinition;
import io.kairo.api.skill.TriggerGuard;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link TriggerGuard} with anti-pollution design.
 *
 * <p>Core principle: <strong>prefer false negatives over false positives</strong>. A skill is only
 * activated when there is high confidence that the user intended it.
 *
 * <p>Activation rules (in order):
 *
 * <ol>
 *   <li>Exact name match — user input contains the skill name as a whole word
 *   <li>Slash command — user input starts with {@code /skillname}
 *   <li>Trigger condition match — all keywords in a trigger must appear in the input
 * </ol>
 */
public class DefaultTriggerGuard implements TriggerGuard {

    /** Words to ignore when computing keyword matches. */
    private static final Set<String> STOP_WORDS =
            Set.of(
                    "a", "an", "the", "is", "are", "was", "were", "be", "been", "do", "does", "did",
                    "have", "has", "had", "will", "would", "can", "could", "should", "may", "might",
                    "shall", "i", "me", "my", "you", "your", "we", "our", "they", "their", "it",
                    "its", "this", "that", "these", "those", "to", "of", "in", "for", "on", "with",
                    "at", "by", "from", "and", "or", "but", "not", "if", "then", "so", "as",
                    "please", "help", "want", "need", "some");

    private final float defaultThreshold;

    /** Create a trigger guard with the default threshold of 0.8 (high bar). */
    public DefaultTriggerGuard() {
        this(0.8f);
    }

    /**
     * Create a trigger guard with a custom threshold.
     *
     * @param threshold the confidence threshold (0.0 to 1.0)
     */
    public DefaultTriggerGuard(float threshold) {
        if (threshold < 0.0f || threshold > 1.0f) {
            throw new IllegalArgumentException(
                    "Threshold must be between 0.0 and 1.0, got: " + threshold);
        }
        this.defaultThreshold = threshold;
    }

    @Override
    public boolean shouldActivate(String userInput, SkillDefinition skill) {
        if (userInput == null || userInput.isBlank() || skill == null) {
            return false;
        }

        String normalizedInput = userInput.strip().toLowerCase(Locale.ROOT);

        // 1. Slash command match: /skillname — always safe, explicit intent
        if (normalizedInput.startsWith("/" + skill.name().toLowerCase(Locale.ROOT))) {
            String rest = normalizedInput.substring(skill.name().length() + 1);
            // Must be end of input or followed by whitespace
            if (rest.isEmpty() || Character.isWhitespace(rest.charAt(0))) {
                return true;
            }
        }

        // 2. Exact name match: user input contains skill name as a whole word
        if (containsExactName(normalizedInput, skill.name())) {
            return true;
        }

        // 3. Trigger condition match: check triggerConditions list
        if (skill.triggerConditions() != null) {
            for (String trigger : skill.triggerConditions()) {
                if (trigger.startsWith("/")) {
                    // Slash trigger: match exactly
                    if (normalizedInput.startsWith(trigger.toLowerCase(Locale.ROOT))) {
                        String rest = normalizedInput.substring(trigger.length());
                        if (rest.isEmpty() || Character.isWhitespace(rest.charAt(0))) {
                            return true;
                        }
                    }
                } else if (matchesTrigger(normalizedInput, trigger)) {
                    return true;
                }
            }
        }

        // Default: do NOT activate — prefer false negatives over false positives
        return false;
    }

    @Override
    public float confidenceThreshold() {
        return defaultThreshold;
    }

    /** Whole-word match for the skill name. "review" must not match "preview". */
    private boolean containsExactName(String input, String name) {
        String lowerName = name.toLowerCase(Locale.ROOT);
        // Use word boundary regex for precise matching
        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(lowerName) + "\\b");
        return pattern.matcher(input).find();
    }

    /**
     * All meaningful keywords in the trigger must appear in the input. For example, trigger "review
     * code" requires both "review" AND "code" in the input.
     */
    private boolean matchesTrigger(String input, String trigger) {
        Set<String> triggerKeywords = extractKeywords(trigger);
        if (triggerKeywords.isEmpty()) {
            return false;
        }

        Set<String> inputKeywords = extractKeywords(input);

        // All trigger keywords must be present in the input
        return inputKeywords.containsAll(triggerKeywords);
    }

    /** Extract meaningful keywords from text, filtering out stop words. */
    private Set<String> extractKeywords(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[\\s,;.!?\"'()\\[\\]{}]+"))
                .filter(w -> !w.isBlank())
                .filter(w -> w.length() > 1)
                .filter(w -> !STOP_WORDS.contains(w))
                .collect(Collectors.toSet());
    }

    /**
     * Check whether a skill's conditional activation rules are satisfied.
     *
     * @param skill the skill to check
     * @param activeFilePath the file path currently being operated on (nullable)
     * @param availableTools the names of tools currently registered (nullable)
     * @return true if all conditions are met (or skill has no conditions)
     */
    public boolean meetsConditions(
            SkillDefinition skill, String activeFilePath, Collection<String> availableTools) {
        if (skill == null || !skill.isConditional()) {
            return true;
        }

        // Platform check
        if (skill.platform() != null) {
            String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String required = skill.platform().toLowerCase(Locale.ROOT);
            boolean platformMatch =
                    switch (required) {
                        case "macos" -> osName.contains("mac");
                        case "linux" -> osName.contains("linux");
                        case "windows" -> osName.contains("windows");
                        default -> true;
                    };
            if (!platformMatch) {
                return false;
            }
        }

        // Required tools check
        if (skill.requiredTools() != null
                && !skill.requiredTools().isEmpty()
                && availableTools != null) {
            for (String required : skill.requiredTools()) {
                if (!availableTools.contains(required)) {
                    return false;
                }
            }
        }

        // Path pattern check
        if (skill.pathPatterns() != null
                && !skill.pathPatterns().isEmpty()
                && activeFilePath != null) {
            boolean anyMatch = false;
            for (String pattern : skill.pathPatterns()) {
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
                if (matcher.matches(java.nio.file.Path.of(activeFilePath))) {
                    anyMatch = true;
                    break;
                }
            }
            if (!anyMatch) {
                return false;
            }
        }

        return true;
    }
}
