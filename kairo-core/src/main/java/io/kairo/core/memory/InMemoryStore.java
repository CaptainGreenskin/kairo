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
import io.kairo.api.memory.MemoryScope;
import io.kairo.api.memory.MemoryStore;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * In-memory implementation of {@link MemoryStore}.
 *
 * <p>Stores entries in a thread-safe {@link ConcurrentHashMap}. Suitable for session-scoped or
 * testing use where persistence is not needed.
 *
 * <p>Following the "Facts First" principle, entries are stored verbatim — no auto-summarization is
 * applied.
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
        if (entry.content().toLowerCase().contains(lowerQuery)) {
            return true;
        }
        // Match against tags
        return entry.tags().stream().anyMatch(tag -> tag.toLowerCase().contains(lowerQuery));
    }
}
