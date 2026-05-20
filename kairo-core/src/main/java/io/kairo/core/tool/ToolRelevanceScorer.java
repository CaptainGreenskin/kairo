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

import io.kairo.api.tool.ToolDefinition;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scores {@link ToolDefinition} relevance against a user prompt using TF-IDF-style term overlap.
 *
 * <p>The scorer tokenizes both the tool's searchable text (name + description + usageGuidance) and
 * the user prompt, then computes a weighted overlap score. IDF weighting penalizes common terms
 * that appear in many tools (like "tool", "use", "the") and boosts rare, discriminating terms.
 *
 * <p>Score range: {@code [0.0, 1.0]}.
 */
public final class ToolRelevanceScorer {

    private ToolRelevanceScorer() {}

    /**
     * Score a single tool against a query.
     *
     * @param tool the tool to score
     * @param query the user prompt or query
     * @param allTools all tools (used for IDF computation)
     * @return relevance score in [0.0, 1.0]
     */
    public static double score(ToolDefinition tool, String query, List<ToolDefinition> allTools) {
        Set<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) {
            return 0.0;
        }

        Map<String, Double> idfWeights = computeIdf(queryTerms, allTools);
        Set<String> toolTerms = tokenize(buildSearchText(tool));

        double weightedMatches = 0.0;
        double totalWeight = 0.0;
        for (String term : queryTerms) {
            double weight = idfWeights.getOrDefault(term, 1.0);
            totalWeight += weight;
            if (toolTerms.contains(term)) {
                weightedMatches += weight;
            }
        }

        return totalWeight > 0.0 ? weightedMatches / totalWeight : 0.0;
    }

    /**
     * Score a single tool using simple term overlap (no IDF). Faster for single-tool scoring.
     *
     * @param tool the tool to score
     * @param query the user prompt
     * @return relevance score in [0.0, 1.0]
     */
    public static double scoreSimple(ToolDefinition tool, String query) {
        Set<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) {
            return 0.0;
        }

        Set<String> toolTerms = tokenize(buildSearchText(tool));
        long matches = queryTerms.stream().filter(toolTerms::contains).count();
        return (double) matches / queryTerms.size();
    }

    static Map<String, Double> computeIdf(Set<String> queryTerms, List<ToolDefinition> allTools) {
        int n = allTools.size();
        if (n == 0) {
            return Map.of();
        }

        Map<String, Integer> docFrequency = new HashMap<>();
        for (ToolDefinition tool : allTools) {
            Set<String> toolTerms = tokenize(buildSearchText(tool));
            for (String queryTerm : queryTerms) {
                if (toolTerms.contains(queryTerm)) {
                    docFrequency.merge(queryTerm, 1, Integer::sum);
                }
            }
        }

        Map<String, Double> idf = new HashMap<>();
        for (String term : queryTerms) {
            int df = docFrequency.getOrDefault(term, 0);
            idf.put(term, Math.log((double) (n + 1) / (df + 1)) + 1.0);
        }
        return idf;
    }

    static String buildSearchText(ToolDefinition tool) {
        StringBuilder sb = new StringBuilder();
        sb.append(tool.name().replace('_', ' ')).append(' ');
        sb.append(tool.description()).append(' ');
        if (tool.usageGuidance() != null && !tool.usageGuidance().isBlank()) {
            sb.append(tool.usageGuidance());
        }
        return sb.toString();
    }

    static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        String[] parts = text.toLowerCase().split("[\\s\\p{Punct}]+");
        Set<String> result = new HashSet<>(Arrays.asList(parts));
        result.remove("");
        return result;
    }
}
