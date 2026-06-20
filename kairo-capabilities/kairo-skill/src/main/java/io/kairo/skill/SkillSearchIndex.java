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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * TF-IDF based skill search index. Scores skills by weighted term frequency across name,
 * description, and trigger fields, then ranks by cosine similarity to the query.
 *
 * <p>Thread-safe: index is rebuilt atomically; search reads a snapshot.
 *
 * @since 0.11.0
 */
public class SkillSearchIndex {

    public static final double AUTO_LOAD_THRESHOLD = 0.30;
    public static final double DISPLAY_THRESHOLD = 0.10;

    private static final Pattern TOKEN_SPLITTER = Pattern.compile("[\\s\\p{Punct}]+");
    private static final double NAME_WEIGHT = 3.0;
    private static final double TRIGGER_WEIGHT = 2.0;
    private static final double DESCRIPTION_WEIGHT = 1.0;

    private static final Set<String> STOP_WORDS =
            Set.of(
                    "a", "an", "the", "is", "it", "to", "in", "for", "of", "on", "and", "or", "be",
                    "are", "was", "were", "been", "being", "have", "has", "had", "do", "does",
                    "did", "will", "would", "shall", "should", "may", "might", "must", "can",
                    "could", "this", "that", "these", "those", "with", "from", "by", "at", "as",
                    "but", "not", "no", "if", "when", "then", "so", "than", "too", "very", "just",
                    "about", "up", "out", "into", "over", "after");

    private volatile List<IndexEntry> entries = List.of();
    private volatile Map<String, Double> idfMap = Map.of();

    public record SearchResult(String name, String description, double score) {}

    public void buildIndex(List<SkillDefinition> skills) {
        List<IndexEntry> newEntries = new ArrayList<>(skills.size());
        Map<String, Integer> docFreq = new HashMap<>();
        int totalDocs = skills.size();

        for (SkillDefinition skill : skills) {
            Map<String, Double> tfVector = computeWeightedTf(skill);
            for (String term : tfVector.keySet()) {
                docFreq.merge(term, 1, Integer::sum);
            }
            newEntries.add(new IndexEntry(skill.name(), skill.description(), tfVector));
        }

        Map<String, Double> newIdf = new HashMap<>();
        for (var e : docFreq.entrySet()) {
            newIdf.put(e.getKey(), Math.log((double) (totalDocs + 1) / (e.getValue() + 1)) + 1.0);
        }

        this.idfMap = Map.copyOf(newIdf);
        this.entries = List.copyOf(newEntries);
    }

    public List<SearchResult> search(String query, int limit) {
        if (query == null || query.isBlank() || entries.isEmpty()) {
            return List.of();
        }

        Map<String, Double> queryTf = computeQueryTf(query);
        Map<String, Double> queryTfIdf = applyIdf(queryTf);

        List<SearchResult> results = new ArrayList<>();
        String queryLower = query.toLowerCase(Locale.ROOT);

        for (IndexEntry entry : entries) {
            Map<String, Double> docTfIdf = applyIdf(entry.tfVector);
            double score = cosineSimilarity(queryTfIdf, docTfIdf);

            if (entry.name != null && entry.name.toLowerCase(Locale.ROOT).contains(queryLower)) {
                score = Math.max(score, 0.75);
            }

            if (score >= DISPLAY_THRESHOLD) {
                results.add(new SearchResult(entry.name, entry.description, score));
            }
        }

        results.sort(Comparator.comparingDouble(SearchResult::score).reversed());
        return results.size() > limit ? results.subList(0, limit) : results;
    }

    public int size() {
        return entries.size();
    }

    private Map<String, Double> computeWeightedTf(SkillDefinition skill) {
        Map<String, Double> result = new HashMap<>();
        addWeightedTerms(result, skill.name(), NAME_WEIGHT);
        addWeightedTerms(result, skill.description(), DESCRIPTION_WEIGHT);
        if (skill.triggerConditions() != null) {
            for (String trigger : skill.triggerConditions()) {
                addWeightedTerms(result, trigger, TRIGGER_WEIGHT);
            }
        }
        return result;
    }

    private Map<String, Double> computeQueryTf(String query) {
        Map<String, Double> result = new HashMap<>();
        for (String token : tokenize(query)) {
            result.merge(token, 1.0, Double::sum);
        }
        if (!result.isEmpty()) {
            double max =
                    result.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
            result.replaceAll((k, v) -> v / max);
        }
        return result;
    }

    private void addWeightedTerms(Map<String, Double> target, String text, double weight) {
        if (text == null || text.isBlank()) return;
        Map<String, Integer> freq = new HashMap<>();
        for (String token : tokenize(text)) {
            freq.merge(token, 1, Integer::sum);
        }
        if (freq.isEmpty()) return;
        int max = freq.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        for (var e : freq.entrySet()) {
            double normalizedTf = (double) e.getValue() / max;
            double weighted = normalizedTf * weight;
            target.merge(e.getKey(), weighted, Math::max);
        }
    }

    private Map<String, Double> applyIdf(Map<String, Double> tf) {
        Map<String, Double> result = new HashMap<>();
        Map<String, Double> idf = this.idfMap;
        for (var e : tf.entrySet()) {
            double idfVal = idf.getOrDefault(e.getKey(), 1.0);
            result.put(e.getKey(), e.getValue() * idfVal);
        }
        return result;
    }

    private static double cosineSimilarity(Map<String, Double> a, Map<String, Double> b) {
        Set<String> allTerms = new HashSet<>(a.keySet());
        allTerms.retainAll(b.keySet());
        if (allTerms.isEmpty()) return 0.0;

        double dotProduct = 0.0;
        for (String term : allTerms) {
            dotProduct += a.get(term) * b.get(term);
        }
        double normA = Math.sqrt(a.values().stream().mapToDouble(v -> v * v).sum());
        double normB = Math.sqrt(b.values().stream().mapToDouble(v -> v * v).sum());
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dotProduct / (normA * normB);
    }

    private static String[] tokenize(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> filtered = new ArrayList<>();

        // CJK bigram segmentation for Chinese/Japanese/Korean characters
        StringBuilder cjkBuffer = new StringBuilder();
        StringBuilder latinBuffer = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (isCjk(c)) {
                flushLatin(latinBuffer, filtered);
                cjkBuffer.append(c);
            } else {
                flushCjkBigrams(cjkBuffer, filtered);
                if (Character.isLetterOrDigit(c)) {
                    latinBuffer.append(c);
                } else {
                    flushLatin(latinBuffer, filtered);
                }
            }
        }
        flushCjkBigrams(cjkBuffer, filtered);
        flushLatin(latinBuffer, filtered);

        return filtered.toArray(String[]::new);
    }

    private static boolean isCjk(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES;
    }

    private static void flushCjkBigrams(StringBuilder buf, List<String> out) {
        if (buf.length() == 0) return;
        String s = buf.toString();
        // Unigrams for single chars
        for (int i = 0; i < s.length(); i++) {
            out.add(String.valueOf(s.charAt(i)));
        }
        // Bigrams for better matching
        for (int i = 0; i < s.length() - 1; i++) {
            out.add(s.substring(i, i + 2));
        }
        buf.setLength(0);
    }

    private static void flushLatin(StringBuilder buf, List<String> out) {
        if (buf.length() < 2) {
            buf.setLength(0);
            return;
        }
        String word = buf.toString();
        buf.setLength(0);
        if (!STOP_WORDS.contains(word)) {
            out.add(stem(word));
        }
    }

    private static String stem(String word) {
        if (word.endsWith("tion") || word.endsWith("ment") || word.endsWith("ness")) {
            return word.substring(0, word.length() - 4);
        }
        if (word.endsWith("ing") && word.length() > 5) {
            return word.substring(0, word.length() - 3);
        }
        if (word.endsWith("er") && word.length() > 4) {
            return word.substring(0, word.length() - 2);
        }
        if (word.endsWith("ed") && word.length() > 4) {
            return word.substring(0, word.length() - 2);
        }
        if (word.endsWith("ly") && word.length() > 4) {
            return word.substring(0, word.length() - 2);
        }
        if (word.endsWith("es") && word.length() > 4) {
            return word.substring(0, word.length() - 2);
        }
        if (word.endsWith("s") && !word.endsWith("ss") && word.length() > 3) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }

    private record IndexEntry(String name, String description, Map<String, Double> tfVector) {}
}
