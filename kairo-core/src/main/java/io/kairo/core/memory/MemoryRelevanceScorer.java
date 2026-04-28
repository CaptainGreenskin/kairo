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
package io.kairo.core.memory;

import io.kairo.api.memory.MemoryEntry;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Scores {@link MemoryEntry} relevance using a weighted combination of term overlap and recency.
 *
 * <p>Formula: {@code score = 0.7 * termOverlap + 0.3 * recencyFactor}
 *
 * <ul>
 *   <li><b>termOverlap</b>: fraction of query terms present in the entry content (TF approximation)
 *   <li><b>recencyFactor</b>: exponential decay with 7-day half-life; entries without a timestamp
 *       score 0.5
 * </ul>
 */
public final class MemoryRelevanceScorer {

    private static final double HALF_LIFE_DAYS = 7.0;
    private static final double LN2 = Math.log(2.0);

    /** Weight of the term-overlap component in the final score. */
    public static final double TERM_WEIGHT = 0.7;

    /** Weight of the recency component in the final score. */
    public static final double RECENCY_WEIGHT = 0.3;

    private MemoryRelevanceScorer() {}

    /**
     * Score an entry against a query. Returns a value in {@code [0.0, 1.0]}.
     *
     * @param entry the memory entry to score
     * @param query the search query
     * @param now reference time for recency calculation (usually {@link Instant#now()})
     */
    public static double score(MemoryEntry entry, String query, Instant now) {
        double termOverlap = computeTermOverlap(entry, query);
        double recency = computeRecency(entry, now);
        return TERM_WEIGHT * termOverlap + RECENCY_WEIGHT * recency;
    }

    static double computeTermOverlap(MemoryEntry entry, String query) {
        Set<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) return 0.0;

        String text = buildSearchText(entry);
        Set<String> entryTerms = tokenize(text);

        long matches = queryTerms.stream().filter(entryTerms::contains).count();
        return (double) matches / queryTerms.size();
    }

    static double computeRecency(MemoryEntry entry, Instant now) {
        if (entry.timestamp() == null) return 0.5;
        long ageMillis = Duration.between(entry.timestamp(), now).toMillis();
        if (ageMillis < 0) return 1.0; // future-dated entries score max
        double ageDays = ageMillis / 86_400_000.0;
        return Math.exp(-LN2 * ageDays / HALF_LIFE_DAYS);
    }

    private static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Set.of();
        String[] parts = text.toLowerCase().split("[\\s\\p{Punct}]+");
        Set<String> result = new HashSet<>(Arrays.asList(parts));
        result.remove("");
        return result;
    }

    private static String buildSearchText(MemoryEntry entry) {
        StringBuilder sb = new StringBuilder();
        if (entry.content() != null) sb.append(entry.content()).append(' ');
        if (entry.rawContent() != null) sb.append(entry.rawContent()).append(' ');
        if (entry.tags() != null) {
            for (String tag : entry.tags()) sb.append(tag).append(' ');
        }
        return sb.toString();
    }
}
