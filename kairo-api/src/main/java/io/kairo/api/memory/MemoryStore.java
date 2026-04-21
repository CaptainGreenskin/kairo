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
package io.kairo.api.memory;

import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Persistent storage for agent memory entries.
 *
 * <p>Provides CRUD and search operations over {@link MemoryEntry} objects, scoped by {@link
 * MemoryScope}. Implementations may back this with in-memory maps, databases, or vector stores
 * depending on the deployment environment.
 *
 * <p>All operations return reactive types ({@link Mono}/{@link Flux}) to support non-blocking I/O
 * in the agent pipeline.
 *
 * <p><strong>Thread safety:</strong> Implementations must be safe for concurrent use from multiple
 * agents or agent iterations.
 *
 * @see MemoryEntry
 * @see MemoryScope
 */
public interface MemoryStore {

    /**
     * Save a memory entry. Creates or updates based on the entry ID.
     *
     * @param entry the entry to save
     * @return a Mono emitting the saved entry
     */
    Mono<MemoryEntry> save(MemoryEntry entry);

    /**
     * Get a memory entry by ID.
     *
     * @param id the entry ID
     * @return a Mono emitting the entry, or empty if not found
     */
    Mono<MemoryEntry> get(String id);

    /**
     * Search memory entries by query text within a scope.
     *
     * @param query the search query
     * @param scope the scope to search within
     * @return a Flux of matching entries
     */
    Flux<MemoryEntry> search(String query, MemoryScope scope);

    /**
     * Delete a memory entry by ID.
     *
     * @param id the entry ID
     * @return a Mono completing when deleted
     */
    Mono<Void> delete(String id);

    /**
     * List all memory entries in a given scope.
     *
     * @param scope the scope to list
     * @return a Flux of entries
     */
    Flux<MemoryEntry> list(MemoryScope scope);

    /**
     * Search memory entries by query text within a scope, filtered by tags. Only entries containing
     * ALL specified tags are returned (AND semantics).
     *
     * <p><strong>Performance note:</strong> The default implementation applies post-filtering on
     * the 2-arg search results. Implementations SHOULD override this method for efficiency,
     * especially for large datasets where server-side tag filtering reduces data transfer.
     *
     * @param query the search query
     * @param scope the scope to search within
     * @param tags required tags (all must be present); null or empty falls back to unfiltered
     *     search
     * @return a Flux of matching entries
     */
    default Flux<MemoryEntry> search(String query, MemoryScope scope, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return search(query, scope);
        }
        return search(query, scope)
                .filter(entry -> entry.tags() != null && entry.tags().containsAll(tags));
    }

    /**
     * Search memory entries using a structured {@link MemoryQuery}.
     *
     * <p>The default implementation returns an empty Flux. Implementations should override this
     * method to provide rich query support including vector similarity search, time range
     * filtering, and importance thresholds.
     *
     * @param query the structured query
     * @return a Flux of matching entries
     */
    default Flux<MemoryEntry> search(MemoryQuery query) {
        if (query == null) {
            return Flux.empty();
        }
        String normalizedKeyword = normalizeKeyword(query.keyword());
        return Flux.fromArray(MemoryScope.values())
                .concatMap(this::list)
                .distinct(MemoryEntry::id)
                .filter(
                        entry ->
                                query.agentId() == null
                                        || (entry.agentId() != null
                                                && query.agentId().equals(entry.agentId())))
                .filter(entry -> query.tags().isEmpty() || entry.tags().containsAll(query.tags()))
                .filter(entry -> entry.importance() >= query.minImportance())
                .filter(
                        entry ->
                                query.from() == null
                                        || (entry.timestamp() != null
                                                && !entry.timestamp().isBefore(query.from())))
                .filter(
                        entry ->
                                query.to() == null
                                        || (entry.timestamp() != null
                                                && !entry.timestamp().isAfter(query.to())))
                .filter(
                        entry ->
                                normalizedKeyword == null
                                        || (entry.content() != null
                                                && entry.content()
                                                        .toLowerCase()
                                                        .contains(normalizedKeyword))
                                        || (entry.rawContent() != null
                                                && entry.rawContent()
                                                        .toLowerCase()
                                                        .contains(normalizedKeyword)))
                .filter(
                        entry ->
                                query.namespace() == null
                                        || query.namespace().equals(resolveNamespace(entry)))
                .take(query.limit());
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase();
    }

    private static String resolveNamespace(MemoryEntry entry) {
        if (entry == null || entry.metadata() == null) {
            return null;
        }
        Object raw = entry.metadata().get("namespace");
        if (raw instanceof String text) {
            String trimmed = text.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        return null;
    }

    /**
     * Get the most recent memory entries for an agent, ordered by timestamp descending. Convenience
     * method for the common "show me recent memories" pattern.
     *
     * @param agentId the agent identifier
     * @param limit maximum number of entries to return
     * @return a Flux of entries ordered by timestamp descending (most recent first)
     */
    default Flux<MemoryEntry> recent(String agentId, int limit) {
        return search(MemoryQuery.builder().agentId(agentId).limit(limit).build())
                .sort(
                        (a, b) -> {
                            if (a.timestamp() == null && b.timestamp() == null) {
                                return 0;
                            }
                            if (a.timestamp() == null) {
                                return 1;
                            }
                            if (b.timestamp() == null) {
                                return -1;
                            }
                            return b.timestamp().compareTo(a.timestamp());
                        })
                .take(limit);
    }

    /** Get the 20 most recent memory entries for an agent. */
    default Flux<MemoryEntry> recent(String agentId) {
        return recent(agentId, 20);
    }
}
