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

import io.kairo.api.context.CacheScope;
import io.kairo.api.context.SystemPromptSegment;
import io.kairo.api.model.ModelCapability;
import io.kairo.api.model.ToolVerbosity;
import io.kairo.api.skill.SkillDefinition;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builder for constructing modular system prompts with static/dynamic boundary separation for
 * Anthropic prompt caching.
 *
 * <p>Sections added before {@link #dynamicBoundary()} are treated as static (cacheable). Sections
 * added after the boundary, or via {@link #featureGate}, are treated as dynamic.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * SystemPromptResult result = SystemPromptBuilder.create()
 *     .section("identity", "You are a helpful coding assistant.")
 *     .addToolOverview(toolRegistry)
 *     .dynamicBoundary()
 *     .addContext("Working directory: /home/user/project")
 *     .featureGate("multiAgent", hasTeam, multiAgentInstructions)
 *     .buildResult();
 * }</pre>
 *
 * <p>For backward compatibility, {@link #build()} still returns a plain {@link String}.
 */
public class SystemPromptBuilder {

    private final List<PromptSection> sections = new ArrayList<>();
    private int dynamicBoundaryIndex = -1; // -1 means no boundary set
    private ModelCapability modelCapability;

    /**
     * A named section of the system prompt.
     *
     * @param name the section name (used as a heading)
     * @param content the section body text
     * @param isDynamic whether this section is after the dynamic boundary
     */
    public record PromptSection(
            String name, String content, boolean isDynamic, CacheScope cacheScope) {
        /** Backward-compat constructor. */
        public PromptSection(String name, String content, boolean isDynamic) {
            this(name, content, isDynamic, isDynamic ? CacheScope.NONE : CacheScope.GLOBAL);
        }
    }

    // Legacy fields for backward-compatible base()/addContext()/addToolOverview() API
    private String basePrompt;
    private final List<String> legacyToolDescriptions = new ArrayList<>();
    private final List<String> legacyContextSections = new ArrayList<>();

    private SystemPromptBuilder() {}

    /** Create a new builder. */
    public static SystemPromptBuilder create() {
        return new SystemPromptBuilder();
    }

    /**
     * Set the model capability for verbosity-aware prompt generation.
     *
     * <p>When set, the builder adjusts content based on the model's {@link ToolVerbosity}:
     *
     * <ul>
     *   <li>{@code CONCISE} — skips example sections and shortens instructions
     *   <li>{@code STANDARD} — current behavior (no change)
     *   <li>{@code VERBOSE} — keeps everything, adds worked examples
     * </ul>
     *
     * @param capability the model capability
     * @return this builder
     */
    public SystemPromptBuilder forModel(ModelCapability capability) {
        this.modelCapability = capability;
        return this;
    }

    // ---- New section-based API ----

    /**
     * Add a named section. Sections added before {@link #dynamicBoundary()} are static/cacheable.
     *
     * <p>If a model capability has been set via {@link #forModel(ModelCapability)}, sections named
     * "examples" or containing only examples will be skipped for CONCISE models.
     *
     * @param name the section name
     * @param content the section content
     * @return this builder
     */
    public SystemPromptBuilder section(String name, String content) {
        if (content != null && !content.isBlank()) {
            // Skip example sections for CONCISE models
            if (!shouldIncludeSection(name, content)) {
                return this;
            }
            boolean isDynamic =
                    dynamicBoundaryIndex >= 0 && sections.size() >= dynamicBoundaryIndex;
            CacheScope scope = isDynamic ? CacheScope.NONE : CacheScope.GLOBAL;
            sections.add(new PromptSection(name, content, isDynamic, scope));
        }
        return this;
    }

    /**
     * Add a named section with an explicit {@link CacheScope}.
     *
     * @param name the section name
     * @param content the section content
     * @param scope the cache scope for this section
     * @return this builder
     */
    public SystemPromptBuilder section(String name, String content, CacheScope scope) {
        if (content != null && !content.isBlank()) {
            if (!shouldIncludeSection(name, content)) {
                return this;
            }
            boolean isDynamic = scope == CacheScope.NONE;
            sections.add(new PromptSection(name, content, isDynamic, scope));
        }
        return this;
    }

    /**
     * Mark the boundary between static (cacheable) and dynamic sections. All sections added after
     * this call are treated as dynamic.
     *
     * @return this builder
     */
    public SystemPromptBuilder dynamicBoundary() {
        this.dynamicBoundaryIndex = sections.size();
        return this;
    }

    /**
     * Conditionally add a section gated by a feature flag. Feature-gated sections are always
     * treated as dynamic regardless of boundary position.
     *
     * @param feature the feature name
     * @param enabled whether the feature is enabled
     * @param instructions the instructions to include when enabled
     * @return this builder
     */
    public SystemPromptBuilder featureGate(String feature, boolean enabled, String instructions) {
        if (enabled && instructions != null && !instructions.isBlank()) {
            sections.add(
                    new PromptSection(
                            "feature:" + feature, instructions, true, CacheScope.SESSION));
        }
        return this;
    }

    /**
     * Add a skill overview section formatted within the given character budget.
     *
     * <p>Uses {@link PromptBudgetFormatter} to format the skill listing with three-level
     * degradation. The section is added with {@link CacheScope#SESSION}.
     *
     * @param skills the skill definitions to include
     * @param budgetChars the maximum character count for the formatted output
     * @return this builder
     */
    public SystemPromptBuilder addSkillOverview(List<SkillDefinition> skills, int budgetChars) {
        String formatted = PromptBudgetFormatter.formatSkills(skills, budgetChars);
        if (formatted != null && !formatted.isBlank()) {
            return section("skills", formatted, CacheScope.SESSION);
        }
        return this;
    }

    /**
     * Add a skill overview section formatted within the {@link PromptBudgetFormatter#DEFAULT_BUDGET
     * default budget}.
     *
     * @param skills the skill definitions to include
     * @return this builder
     * @see #addSkillOverview(List, int)
     */
    public SystemPromptBuilder addSkillOverview(List<SkillDefinition> skills) {
        return addSkillOverview(skills, PromptBudgetFormatter.DEFAULT_BUDGET);
    }

    // ---- Legacy API (backward compatible) ----

    /**
     * Set the base system prompt (legacy API).
     *
     * @param prompt the base prompt text
     * @return this builder
     */
    public SystemPromptBuilder base(String prompt) {
        this.basePrompt = prompt;
        return this;
    }

    /**
     * Add tool overview descriptions from a {@link ToolRegistry}, grouped by {@link ToolCategory}.
     *
     * <p>When using the new section-based API, adds a "tools" section. When using the legacy API,
     * appends to the legacy tool descriptions list.
     *
     * @param registry the tool registry to read from
     * @return this builder
     */
    public SystemPromptBuilder addToolOverview(ToolRegistry registry) {
        if (registry == null) {
            return this;
        }
        String overview = buildToolOverviewText(registry);
        if (overview != null && !overview.isBlank()) {
            if (!sections.isEmpty() || dynamicBoundaryIndex >= 0) {
                // New API mode: add as a section
                return section("tools", overview);
            } else {
                // Legacy mode
                legacyToolDescriptions.add(overview);
            }
        }
        return this;
    }

    /**
     * Add a custom context section.
     *
     * <p>When using the new section-based API, adds a "context" section. When using the legacy API,
     * appends to the legacy context list.
     *
     * @param contextText the context text
     * @return this builder
     */
    public SystemPromptBuilder addContext(String contextText) {
        if (contextText != null && !contextText.isBlank()) {
            if (!sections.isEmpty() || dynamicBoundaryIndex >= 0) {
                // New API mode
                return section("context", contextText);
            } else {
                // Legacy mode
                legacyContextSections.add(contextText);
            }
        }
        return this;
    }

    /**
     * Build the complete system prompt as a plain string (backward-compatible).
     *
     * @return the assembled system prompt
     */
    public String build() {
        if (!sections.isEmpty() || dynamicBoundaryIndex >= 0) {
            return buildResult().fullPrompt();
        }
        // Legacy build path
        return buildLegacy();
    }

    /**
     * Build the system prompt with static/dynamic separation for prompt caching.
     *
     * @return the {@link SystemPromptResult} with static prefix, dynamic suffix, and full prompt
     */
    public SystemPromptResult buildResult() {
        // If only legacy API was used, convert to sections first
        if (sections.isEmpty() && dynamicBoundaryIndex < 0) {
            convertLegacyToSections();
        }

        StringBuilder staticPrefix = new StringBuilder();
        StringBuilder dynamicSuffix = new StringBuilder();
        StringBuilder fullPrompt = new StringBuilder();

        for (int i = 0; i < sections.size(); i++) {
            PromptSection s = sections.get(i);
            String formatted = "# " + capitalize(s.name()) + "\n\n" + s.content() + "\n\n";
            fullPrompt.append(formatted);

            if (s.isDynamic()) {
                dynamicSuffix.append(formatted);
            } else if (dynamicBoundaryIndex < 0 || i < dynamicBoundaryIndex) {
                staticPrefix.append(formatted);
            } else {
                dynamicSuffix.append(formatted);
            }
        }

        List<SystemPromptSegment> segments = buildSegments();

        return new SystemPromptResult(
                staticPrefix.toString().trim(),
                dynamicSuffix.toString().trim(),
                fullPrompt.toString().trim(),
                segments);
    }

    /**
     * Build the system prompt as a list of cache-scoped segments. Each segment maps to an
     * independent text block in the model API.
     *
     * @return an immutable list of {@link SystemPromptSegment}
     */
    public List<SystemPromptSegment> buildSegments() {
        if (sections.isEmpty() && dynamicBoundaryIndex < 0) {
            convertLegacyToSections();
        }

        List<SystemPromptSegment> segments = new ArrayList<>();
        for (PromptSection s : sections) {
            String formatted = "# " + capitalize(s.name()) + "\n\n" + s.content();
            segments.add(new SystemPromptSegment(s.name(), formatted, s.cacheScope()));
        }
        return List.copyOf(segments);
    }

    /** Get the list of prompt sections (for testing/introspection). */
    public List<PromptSection> sections() {
        return List.copyOf(sections);
    }

    // ---- Internal helpers ----

    /** Build the tool overview text from a registry, grouped by category. */
    private String buildToolOverviewText(ToolRegistry registry) {
        List<ToolDefinition> allTools = registry.getAll();
        if (allTools.isEmpty()) {
            return null;
        }

        Map<ToolCategory, List<ToolDefinition>> grouped =
                allTools.stream().collect(Collectors.groupingBy(ToolDefinition::category));

        StringBuilder sb = new StringBuilder();
        sb.append("## Available Tools\n\n");

        for (Map.Entry<ToolCategory, List<ToolDefinition>> entry : grouped.entrySet()) {
            sb.append("### ").append(entry.getKey().name()).append("\n");
            for (ToolDefinition tool : entry.getValue()) {
                sb.append("- **")
                        .append(tool.name())
                        .append("**: ")
                        .append(tool.description())
                        .append("\n");
            }
            sb.append("\n");
        }

        return sb.toString().trim();
    }

    /** Convert legacy builder state into sections for unified processing. */
    private void convertLegacyToSections() {
        if (basePrompt != null && !basePrompt.isBlank()) {
            sections.add(new PromptSection("system", basePrompt, false));
        }
        for (String toolDesc : legacyToolDescriptions) {
            sections.add(new PromptSection("tools", toolDesc, false));
        }
        for (String ctx : legacyContextSections) {
            sections.add(new PromptSection("context", ctx, false));
        }
    }

    /** Legacy build path for full backward compatibility. */
    private String buildLegacy() {
        StringBuilder sb = new StringBuilder();

        if (basePrompt != null && !basePrompt.isBlank()) {
            sb.append(basePrompt);
        }

        if (!legacyToolDescriptions.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(String.join("\n\n", legacyToolDescriptions));
        }

        if (!legacyContextSections.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append("# Context\n\n");
            sb.append(String.join("\n\n", legacyContextSections));
        }

        return sb.toString();
    }

    /** Capitalize the first letter of a string. */
    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---- Model-aware helpers ----

    /** Whether the current model's verbosity level includes example sections. */
    private boolean shouldIncludeExamples() {
        return modelCapability == null || modelCapability.toolVerbosity() != ToolVerbosity.CONCISE;
    }

    /**
     * Whether a section should be included based on the model capability. Sections named "examples"
     * are skipped for CONCISE models.
     */
    private boolean shouldIncludeSection(String name, String content) {
        if (modelCapability == null) {
            return true;
        }
        ToolVerbosity verbosity = modelCapability.toolVerbosity();
        if (verbosity == ToolVerbosity.CONCISE) {
            // Skip example sections
            if ("examples".equalsIgnoreCase(name) || "worked-examples".equalsIgnoreCase(name)) {
                return false;
            }
        }
        return true;
    }
}
