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
import java.time.Instant;
import java.util.Comparator;
import java.util.concurrent.CopyOnWriteArrayList;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * In-memory {@link MemoryStore} backed by a {@link CopyOnWriteArrayList} (optimised for read-heavy
 * workloads).
 *
 * <p>Search results are ranked by {@link MemoryRelevanceScorer}: {@code score = 0.7 * termOverlap +
 * 0.3 * recencyFactor} using a 7-day half-life decay for recency.
 *
 * <p>Suitable for single-JVM deployments and testing. Production multi-node deployments should
 * replace this with a vector-store-backed implementation.
 */
public final class InMemoryMemoryStore implements MemoryStore {

    private final CopyOnWriteArrayList<MemoryEntry> entries = new CopyOnWriteArrayList<>();

    @Override
    public Mono<MemoryEntry> save(MemoryEntry entry) {
        entries.removeIf(e -> e.id().equals(entry.id()));
        entries.add(entry);
        return Mono.just(entry);
    }

    @Override
    public Mono<MemoryEntry> get(String id) {
        return Mono.justOrEmpty(
                entries.stream().filter(e -> e.id().equals(id)).findFirst().orElse(null));
    }

    @Override
    public Flux<MemoryEntry> search(String query, MemoryScope scope) {
        Instant now = Instant.now();
        return Flux.fromIterable(entries)
                .filter(e -> e.scope() == scope)
                .sort(relevanceOrder(query, now));
    }

    @Override
    public Mono<Void> delete(String id) {
        entries.removeIf(e -> e.id().equals(id));
        return Mono.empty();
    }

    @Override
    public Flux<MemoryEntry> list(MemoryScope scope) {
        return Flux.fromIterable(entries)
                .filter(e -> e.scope() == scope)
                .sort(
                        Comparator.comparing(
                                MemoryEntry::timestamp,
                                Comparator.nullsLast(Comparator.reverseOrder())));
    }

    /**
     * Remove all entries owned by the given agent.
     *
     * <p>This is a convenience extension beyond the core SPI for housekeeping.
     */
    public void clearAgent(String agentId) {
        entries.removeIf(e -> agentId.equals(e.agentId()));
    }

    /** Total number of entries currently held. */
    public int size() {
        return entries.size();
    }

    private static Comparator<MemoryEntry> relevanceOrder(String query, Instant now) {
        return Comparator.comparingDouble(
                        (MemoryEntry e) -> MemoryRelevanceScorer.score(e, query, now))
                .reversed();
    }
}
