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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class MemoryStoreStructuredFallbackTest {

    static class LegacyOnlyStore implements MemoryStore {
        private final List<MemoryEntry> entries;

        LegacyOnlyStore(List<MemoryEntry> entries) {
            this.entries = entries;
        }

        @Override
        public Mono<MemoryEntry> save(MemoryEntry entry) {
            return Mono.just(entry);
        }

        @Override
        public Mono<MemoryEntry> get(String id) {
            return Mono.empty();
        }

        @Override
        public Flux<MemoryEntry> search(String query, MemoryScope scope) {
            String q = query == null ? "" : query.toLowerCase();
            return Flux.fromIterable(entries)
                    .filter(e -> e.scope() == scope)
                    .filter(e -> e.content() != null && e.content().toLowerCase().contains(q));
        }

        @Override
        public Mono<Void> delete(String id) {
            return Mono.empty();
        }

        @Override
        public Flux<MemoryEntry> list(MemoryScope scope) {
            return Flux.fromIterable(entries).filter(e -> e.scope() == scope);
        }
    }

    @Test
    void structuredSearchFallsBackToLegacyMethods() {
        Instant now = Instant.now();
        MemoryEntry keep =
                new MemoryEntry(
                        "1",
                        "agent-1",
                        "java memory",
                        null,
                        MemoryScope.AGENT,
                        0.8,
                        null,
                        Set.of("lang", "java"),
                        now,
                        Map.of());
        MemoryEntry wrongTag =
                new MemoryEntry(
                        "2",
                        "agent-1",
                        "java no tag",
                        null,
                        MemoryScope.AGENT,
                        0.9,
                        null,
                        Set.of("lang"),
                        now,
                        Map.of());
        MemoryEntry wrongAgent =
                new MemoryEntry(
                        "3",
                        "agent-2",
                        "java memory",
                        null,
                        MemoryScope.AGENT,
                        0.9,
                        null,
                        Set.of("lang", "java"),
                        now,
                        Map.of());

        MemoryStore store = new LegacyOnlyStore(List.of(keep, wrongTag, wrongAgent));

        List<MemoryEntry> result =
                store.search(
                                MemoryQuery.builder()
                                        .agentId("agent-1")
                                        .keyword("java")
                                        .tags(Set.of("java"))
                                        .minImportance(0.7)
                                        .limit(10)
                                        .build())
                        .collectList()
                        .block();

        assertEquals(1, result.size());
        assertEquals("1", result.get(0).id());
    }
}
