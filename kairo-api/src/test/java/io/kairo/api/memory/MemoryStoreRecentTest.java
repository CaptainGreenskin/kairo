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

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Tests for the {@link MemoryStore#recent(String, int)} default methods. */
class MemoryStoreRecentTest {

    /** A minimal MemoryStore that captures the MemoryQuery passed to search(). */
    static class CapturingMemoryStore implements MemoryStore {
        final List<MemoryQuery> capturedQueries = new ArrayList<>();

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
            return Flux.empty();
        }

        @Override
        public Mono<Void> delete(String id) {
            return Mono.empty();
        }

        @Override
        public Flux<MemoryEntry> list(MemoryScope scope) {
            return Flux.empty();
        }

        @Override
        public Flux<MemoryEntry> search(MemoryQuery query) {
            capturedQueries.add(query);
            return Flux.empty();
        }
    }

    @Test
    void recentDelegatesToSearchWithCorrectQuery() {
        CapturingMemoryStore store = new CapturingMemoryStore();

        store.recent("agent-42", 10).collectList().block();

        assertEquals(1, store.capturedQueries.size());
        MemoryQuery captured = store.capturedQueries.get(0);
        assertEquals("agent-42", captured.agentId());
        assertEquals(10, captured.limit());
    }

    @Test
    void recentDefaultLimitIs20() {
        CapturingMemoryStore store = new CapturingMemoryStore();

        store.recent("agent-99").collectList().block();

        assertEquals(1, store.capturedQueries.size());
        MemoryQuery captured = store.capturedQueries.get(0);
        assertEquals("agent-99", captured.agentId());
        assertEquals(20, captured.limit());
    }

    @Test
    void recentSortsByTimestampDescending() {
        MemoryEntry oldest =
                new MemoryEntry(
                        "1",
                        "agent-1",
                        "old",
                        null,
                        MemoryScope.AGENT,
                        0.5,
                        null,
                        Set.of(),
                        Instant.parse("2025-01-01T00:00:00Z"),
                        Map.of());
        MemoryEntry newest =
                new MemoryEntry(
                        "2",
                        "agent-1",
                        "new",
                        null,
                        MemoryScope.AGENT,
                        0.5,
                        null,
                        Set.of(),
                        Instant.parse("2025-01-03T00:00:00Z"),
                        Map.of());
        MemoryEntry middle =
                new MemoryEntry(
                        "3",
                        "agent-1",
                        "mid",
                        null,
                        MemoryScope.AGENT,
                        0.5,
                        null,
                        Set.of(),
                        Instant.parse("2025-01-02T00:00:00Z"),
                        Map.of());

        MemoryStore store =
                new CapturingMemoryStore() {
                    @Override
                    public Flux<MemoryEntry> search(MemoryQuery query) {
                        return Flux.just(oldest, newest, middle);
                    }
                };

        List<MemoryEntry> entries = store.recent("agent-1", 3).collectList().block();
        assertEquals(List.of("2", "3", "1"), entries.stream().map(MemoryEntry::id).toList());
    }
}
