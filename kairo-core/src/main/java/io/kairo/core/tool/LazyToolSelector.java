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
package io.kairo.core.tool;

import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Selects a subset of tools from a {@link ToolRegistry} that are most relevant to a given user
 * prompt. Baseline tools (FILE_AND_CODE, EXECUTION categories) are always included.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * LazyToolSelector selector = new LazyToolSelector(registry);
 * List<ToolDefinition> relevant = selector.select("Fix the auth bug in login.java", 15);
 * }</pre>
 */
public final class LazyToolSelector {

    public static final int DEFAULT_MAX_TOOLS = 20;
    public static final double MIN_RELEVANCE_THRESHOLD = 0.05;

    private static final Set<ToolCategory> BASELINE_CATEGORIES =
            Set.of(ToolCategory.FILE_AND_CODE, ToolCategory.EXECUTION);

    private final ToolRegistry registry;

    public LazyToolSelector(ToolRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    /**
     * Select the most relevant tools for the given prompt, always including baseline tools.
     *
     * @param userPrompt the user's message or goal
     * @param maxTools maximum number of tools to return (baseline tools don't count toward this
     *     limit)
     * @return selected tools: baseline first, then top-K by relevance
     */
    public List<ToolDefinition> select(String userPrompt, int maxTools) {
        List<ToolDefinition> allTools = registry.getAll();
        if (allTools.isEmpty()) {
            return List.of();
        }

        if (userPrompt == null || userPrompt.isBlank()) {
            return List.copyOf(allTools);
        }

        List<ToolDefinition> baseline = new ArrayList<>();
        List<ToolDefinition> candidates = new ArrayList<>();

        for (ToolDefinition tool : allTools) {
            if (BASELINE_CATEGORIES.contains(tool.category())) {
                baseline.add(tool);
            } else {
                candidates.add(tool);
            }
        }

        List<ScoredTool> scored = new ArrayList<>(candidates.size());
        for (ToolDefinition tool : candidates) {
            double score = ToolRelevanceScorer.score(tool, userPrompt, allTools);
            scored.add(new ScoredTool(tool, score));
        }

        scored.sort(Comparator.comparingDouble(ScoredTool::score).reversed());

        Set<String> selected = new LinkedHashSet<>();
        List<ToolDefinition> result = new ArrayList<>();

        for (ToolDefinition tool : baseline) {
            if (selected.add(tool.name())) {
                result.add(tool);
            }
        }

        int added = 0;
        for (ScoredTool st : scored) {
            if (added >= maxTools) {
                break;
            }
            if (st.score() < MIN_RELEVANCE_THRESHOLD) {
                break;
            }
            if (selected.add(st.tool().name())) {
                result.add(st.tool());
                added++;
            }
        }

        return List.copyOf(result);
    }

    /**
     * Select with default max tools.
     *
     * @param userPrompt the user's message
     * @return selected tools
     */
    public List<ToolDefinition> select(String userPrompt) {
        return select(userPrompt, DEFAULT_MAX_TOOLS);
    }

    record ScoredTool(ToolDefinition tool, double score) {}
}
