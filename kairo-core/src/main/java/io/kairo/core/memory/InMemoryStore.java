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
import io.kairo.api.memory.MemoryQuery;
import io.kairo.api.memory.MemoryScope;
import io.kairo.api.memory.MemoryStore;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * In-memory implementation of {@link MemoryStore}.
 *
 * <p>Stores entries in a thread-safe {@link ConcurrentHashMap}. Suitable for session-scoped or
 * testing use where persistence is not needed.
 */
public class InMemoryStore implements MemoryStore {

    private final ConcurrentHashMap<String, MemoryEntry> entries = new ConcurrentHashMap<>();

    @Override
    public Mono<MemoryEntry> save(MemoryEntry entry) {
        entries.put(entry.id(), entry);
        return Mono.just(entry);
    }

    @Override
    public Mono<MemoryEntry> get(String id) {
        return Mono.justOrEmpty(entries.get(id));
    }

    @Override
    public Flux<MemoryEntry> search(String query, MemoryScope scope) {
        String lowerQuery = query.toLowerCase();
        return Flux.fromIterable(entries.values())
                .filter(e -> e.scope() == scope)
                .filter(e -> matchesQuery(e, lowerQuery));
    }

    @Override
    public Flux<MemoryEntry> search(String query, MemoryScope scope, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return search(query, scope);
        }
        String lowerQuery = query.toLowerCase();
        return Flux.fromIterable(entries.values())
                .filter(
                        e ->
                                e.scope() == scope
                                        && matchesQuery(e, lowerQuery)
                                        && e.tags() != null
                                        && e.tags().containsAll(tags));
    }

    /**
     * Search memory entries using a structured {@link MemoryQuery}.
     *
     * <p>Supports filtering by agentId, keyword (case-insensitive contains), tags (containsAll),
     * importance threshold, date range, and limit. When queryVector is present and entries have
     * non-null embeddings, results are ranked by cosine similarity.
     *
     * <p>@Experimental: Cosine similarity search is experimental and may change.
     */
    @Override
    public Flux<MemoryEntry> search(MemoryQuery query) {
        Stream<MemoryEntry> stream = entries.values().stream();

        // Filter by agentId
        if (query.agentId() != null) {
            stream = stream.filter(e -> query.agentId().equals(e.agentId()));
        }

        // Filter by keyword (case-insensitive contains on content)
        if (query.keyword() != null && !query.keyword().isBlank()) {
            String lowerKeyword = query.keyword().toLowerCase();
            stream = stream.filter(e -> matchesQuery(e, lowerKeyword));
        }

        // Filter by tags (AND semantics)
        if (query.tags() != null && !query.tags().isEmpty()) {
            stream = stream.filter(e -> e.tags() != null && e.tags().containsAll(query.tags()));
        }

        // Filter by importance threshold
        if (query.minImportance() > 0.0) {
            stream = stream.filter(e -> e.importance() >= query.minImportance());
        }

        // Filter by date range
        if (query.from() != null) {
            stream = stream.filter(e -> !e.timestamp().isBefore(query.from()));
        }
        if (query.to() != null) {
            stream = stream.filter(e -> !e.timestamp().isAfter(query.to()));
        }

        List<MemoryEntry> filtered = stream.toList();

        // @Experimental: Cosine similarity ranking when queryVector is present
        if (query.queryVector() != null && query.queryVector().length > 0) {
            List<MemoryEntry> ranked =
                    filtered.stream()
                            .filter(e -> e.embedding() != null && e.embedding().length > 0)
                            .sorted(
                                    Comparator.comparingDouble(
                                                    (MemoryEntry e) ->
                                                            cosineSimilarity(
                                                                    query.queryVector(),
                                                                    e.embedding()))
                                            .reversed())
                            .limit(query.limit())
                            .toList();
            return Flux.fromIterable(ranked);
        }

        // Default: sort by timestamp descending, apply limit
        List<MemoryEntry> result =
                filtered.stream()
                        .sorted(Comparator.comparing(MemoryEntry::timestamp).reversed())
                        .limit(query.limit())
                        .toList();
        return Flux.fromIterable(result);
    }

    @Override
    public Mono<Void> delete(String id) {
        entries.remove(id);
        return Mono.empty();
    }

    @Override
    public Flux<MemoryEntry> list(MemoryScope scope) {
        return Flux.fromIterable(entries.values())
                .filter(e -> e.scope() == scope)
                .sort(Comparator.comparing(MemoryEntry::timestamp).reversed());
    }

    /**
     * Get the number of entries currently stored.
     *
     * @return the entry count
     */
    public int size() {
        return entries.size();
    }

    /** Clear all stored entries. */
    public void clear() {
        entries.clear();
    }

    private boolean matchesQuery(MemoryEntry entry, String lowerQuery) {
        // Match against content
        if (entry.content() != null && entry.content().toLowerCase().contains(lowerQuery)) {
            return true;
        }
        // Match against rawContent (pre-compaction original text)
        if (entry.rawContent() != null && entry.rawContent().toLowerCase().contains(lowerQuery)) {
            return true;
        }
        // Match against tags
        return entry.tags().stream().anyMatch(tag -> tag.toLowerCase().contains(lowerQuery));
    }

    /** Compute cosine similarity between two vectors. @Experimental */
    static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0.0 ? 0.0 : dot / denom;
    }
}
