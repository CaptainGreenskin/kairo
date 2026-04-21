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

    @Test
    void structuredSearchMatchesRawContentFallback() {
        Instant now = Instant.now();
        MemoryEntry entry =
                new MemoryEntry(
                        "raw-1",
                        "agent-raw",
                        "summary only",
                        "contains hidden needle",
                        MemoryScope.AGENT,
                        0.8,
                        null,
                        Set.of("raw"),
                        now,
                        Map.of());

        MemoryStore store = new LegacyOnlyStore(List.of(entry));

        List<MemoryEntry> result =
                store.search(
                                MemoryQuery.builder()
                                        .agentId("agent-raw")
                                        .keyword("needle")
                                        .limit(5)
                                        .build())
                        .collectList()
                        .block();

        assertEquals(1, result.size());
        assertEquals("raw-1", result.get(0).id());
    }

    @Test
    void structuredSearchAppliesTimeWindowAndImportance() {
        Instant now = Instant.now();
        MemoryEntry tooOld =
                new MemoryEntry(
                        "old",
                        "agent-time",
                        "java old",
                        null,
                        MemoryScope.AGENT,
                        0.9,
                        null,
                        Set.of("time"),
                        now.minusSeconds(3600),
                        Map.of());
        MemoryEntry tooLowImportance =
                new MemoryEntry(
                        "low",
                        "agent-time",
                        "java low",
                        null,
                        MemoryScope.AGENT,
                        0.2,
                        null,
                        Set.of("time"),
                        now.minusSeconds(30),
                        Map.of());
        MemoryEntry keep =
                new MemoryEntry(
                        "keep",
                        "agent-time",
                        "java keep",
                        null,
                        MemoryScope.AGENT,
                        0.8,
                        null,
                        Set.of("time"),
                        now.minusSeconds(30),
                        Map.of());

        MemoryStore store = new LegacyOnlyStore(List.of(tooOld, tooLowImportance, keep));

        List<MemoryEntry> result =
                store.search(
                                MemoryQuery.builder()
                                        .agentId("agent-time")
                                        .keyword("java")
                                        .from(now.minusSeconds(60))
                                        .to(now)
                                        .minImportance(0.7)
                                        .limit(10)
                                        .build())
                        .collectList()
                        .block();

        assertEquals(1, result.size());
        assertEquals("keep", result.get(0).id());
    }

    @Test
    void structuredSearchDeduplicatesSameIdAcrossScopes() {
        Instant now = Instant.now();
        MemoryEntry agent =
                new MemoryEntry(
                        "same-id",
                        "agent-dupe",
                        "java in agent",
                        null,
                        MemoryScope.AGENT,
                        0.7,
                        null,
                        Set.of("dupe"),
                        now,
                        Map.of());
        MemoryEntry shared =
                new MemoryEntry(
                        "same-id",
                        "agent-dupe",
                        "java in shared",
                        null,
                        MemoryScope.GLOBAL,
                        0.8,
                        null,
                        Set.of("dupe"),
                        now,
                        Map.of());

        MemoryStore store = new LegacyOnlyStore(List.of(agent, shared));

        List<MemoryEntry> result =
                store.search(
                                MemoryQuery.builder()
                                        .agentId("agent-dupe")
                                        .keyword("java")
                                        .limit(10)
                                        .build())
                        .collectList()
                        .block();

        assertEquals(1, result.size());
        assertEquals("same-id", result.get(0).id());
    }

    @Test
    void structuredSearchKeywordTrimmedBeforeMatching() {
        Instant now = Instant.now();
        MemoryEntry entry =
                new MemoryEntry(
                        "trim-1",
                        "agent-trim",
                        "memory about java",
                        null,
                        MemoryScope.AGENT,
                        0.9,
                        null,
                        Set.of("trim"),
                        now,
                        Map.of());

        MemoryStore store = new LegacyOnlyStore(List.of(entry));

        List<MemoryEntry> result =
                store.search(
                                MemoryQuery.builder()
                                        .agentId("agent-trim")
                                        .keyword("  java  ")
                                        .limit(10)
                                        .build())
                        .collectList()
                        .block();

        assertEquals(1, result.size());
        assertEquals("trim-1", result.get(0).id());
    }

    @Test
    void structuredSearchRespectsLimitAfterFiltering() {
        Instant now = Instant.now();
        MemoryEntry first =
                new MemoryEntry(
                        "l1",
                        "agent-limit",
                        "java one",
                        null,
                        MemoryScope.AGENT,
                        0.8,
                        null,
                        Set.of("limit"),
                        now.minusSeconds(3),
                        Map.of());
        MemoryEntry second =
                new MemoryEntry(
                        "l2",
                        "agent-limit",
                        "java two",
                        null,
                        MemoryScope.AGENT,
                        0.8,
                        null,
                        Set.of("limit"),
                        now.minusSeconds(2),
                        Map.of());
        MemoryEntry third =
                new MemoryEntry(
                        "l3",
                        "agent-limit",
                        "java three",
                        null,
                        MemoryScope.AGENT,
                        0.8,
                        null,
                        Set.of("limit"),
                        now.minusSeconds(1),
                        Map.of());

        MemoryStore store = new LegacyOnlyStore(List.of(first, second, third));

        List<MemoryEntry> result =
                store.search(
                                MemoryQuery.builder()
                                        .agentId("agent-limit")
                                        .keyword("java")
                                        .limit(2)
                                        .build())
                        .collectList()
                        .block();

        assertEquals(2, result.size());
    }

    @Test
    void structuredSearchFiltersByNamespace() {
        Instant now = Instant.now();
        MemoryEntry keep =
                new MemoryEntry(
                        "ns-1",
                        "agent-ns",
                        "java in alpha namespace",
                        null,
                        MemoryScope.AGENT,
                        0.8,
                        null,
                        Set.of("ns"),
                        now,
                        Map.of("namespace", "alpha"));
        MemoryEntry filtered =
                new MemoryEntry(
                        "ns-2",
                        "agent-ns",
                        "java in beta namespace",
                        null,
                        MemoryScope.AGENT,
                        0.8,
                        null,
                        Set.of("ns"),
                        now,
                        Map.of("namespace", "beta"));

        MemoryStore store = new LegacyOnlyStore(List.of(keep, filtered));

        List<MemoryEntry> result =
                store.search(
                                MemoryQuery.builder()
                                        .agentId("agent-ns")
                                        .keyword("java")
                                        .namespace("alpha")
                                        .limit(10)
                                        .build())
                        .collectList()
                        .block();

        assertEquals(1, result.size());
        assertEquals("ns-1", result.get(0).id());
    }
}
