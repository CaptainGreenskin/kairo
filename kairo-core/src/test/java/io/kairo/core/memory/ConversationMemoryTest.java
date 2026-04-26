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

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.memory.MemoryEntry;
import io.kairo.api.memory.MemoryQuery;
import io.kairo.api.memory.MemoryScope;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ConversationMemoryTest {

    private InMemoryStore store;
    private ConversationMemory memory;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        memory = new ConversationMemory(store, "test-agent");
    }

    @Test
    @DisplayName("remember stores entry with correct tag")
    void testRememberStoresEntry() {
        StepVerifier.create(memory.remember("user-name", "Alice")).verifyComplete();

        assertEquals(1, store.size());
    }

    @Test
    @DisplayName("recall retrieves stored value")
    void testRecallRetrievesValue() {
        memory.remember("preference", "dark mode").block();

        StepVerifier.create(memory.recall("preference")).expectNext("dark mode").verifyComplete();
    }

    @Test
    @DisplayName("recall returns empty for non-existent key")
    void testRecallNonExistentKey() {
        StepVerifier.create(memory.recall("nonexistent")).verifyComplete();
    }

    @Test
    @DisplayName("forget removes entry")
    void testForgetRemovesEntry() {
        memory.remember("temp-data", "some value").block();
        assertEquals(1, store.size());

        StepVerifier.create(memory.forget("temp-data")).verifyComplete();

        assertEquals(0, store.size());

        StepVerifier.create(memory.recall("temp-data")).verifyComplete();
    }

    @Test
    @DisplayName("remember multiple keys and recall individually")
    void testMultipleKeysRecall() {
        memory.remember("key1", "value1").block();
        memory.remember("key2", "value2").block();

        StepVerifier.create(memory.recall("key1")).expectNext("value1").verifyComplete();
        StepVerifier.create(memory.recall("key2")).expectNext("value2").verifyComplete();
    }

    @Test
    @DisplayName("different agents have isolated memories")
    void testAgentIsolation() {
        ConversationMemory agentA = new ConversationMemory(store, "agent-A");
        ConversationMemory agentB = new ConversationMemory(store, "agent-B");

        agentA.remember("secret", "A's secret").block();
        agentB.remember("secret", "B's secret").block();

        StepVerifier.create(agentA.recall("secret")).expectNext("A's secret").verifyComplete();
        StepVerifier.create(agentB.recall("secret")).expectNext("B's secret").verifyComplete();
    }

    // --- New tests for recallFull / recallAllFull / rawContent pipeline ---

    @Test
    @DisplayName("recallFull returns MemoryEntry with rawContent")
    void testRecallFullReturnsEntryWithRawContent() {
        // Simulate a compaction entry with rawContent populated
        MemoryEntry entry =
                new MemoryEntry(
                        UUID.randomUUID().toString(),
                        "test-agent",
                        "compacted summary",
                        "original long conversation text before compaction",
                        MemoryScope.SESSION,
                        0.6,
                        null,
                        Set.of("compaction-summary"),
                        Instant.now(),
                        null);
        store.save(entry).block();

        StepVerifier.create(memory.recallFull("compaction-summary"))
                .assertNext(
                        e -> {
                            assertEquals("compacted summary", e.content());
                            assertEquals(
                                    "original long conversation text before compaction",
                                    e.rawContent());
                            assertTrue(e.tags().contains("compaction-summary"));
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("recallFull returns empty Mono when key not found")
    void testRecallFullReturnsEmptyWhenNotFound() {
        StepVerifier.create(memory.recallFull("nonexistent-key")).verifyComplete();
    }

    @Test
    @DisplayName("recallAllFull returns entries ordered by recency")
    void testRecallAllFullOrderedByRecency() {
        Instant older = Instant.now().minusSeconds(60);
        Instant newer = Instant.now();

        MemoryEntry olderEntry =
                new MemoryEntry(
                        UUID.randomUUID().toString(),
                        "test-agent",
                        "older content",
                        null,
                        MemoryScope.AGENT,
                        0.5,
                        null,
                        Set.of("tag1"),
                        older,
                        null);
        MemoryEntry newerEntry =
                new MemoryEntry(
                        UUID.randomUUID().toString(),
                        "test-agent",
                        "newer content",
                        null,
                        MemoryScope.AGENT,
                        0.5,
                        null,
                        Set.of("tag2"),
                        newer,
                        null);

        store.save(olderEntry).block();
        store.save(newerEntry).block();

        StepVerifier.create(memory.recallAllFull(10))
                .assertNext(e -> assertEquals("newer content", e.content()))
                .assertNext(e -> assertEquals("older content", e.content()))
                .verifyComplete();
    }

    @Test
    @DisplayName("recallAllFull respects limit")
    void testRecallAllFullRespectsLimit() {
        for (int i = 0; i < 5; i++) {
            MemoryEntry entry =
                    new MemoryEntry(
                            UUID.randomUUID().toString(),
                            "test-agent",
                            "content-" + i,
                            null,
                            MemoryScope.AGENT,
                            0.5,
                            null,
                            Set.of("bulk"),
                            Instant.now().plusSeconds(i),
                            null);
            store.save(entry).block();
        }

        StepVerifier.create(memory.recallAllFull(3).collectList())
                .assertNext(list -> assertEquals(3, list.size()))
                .verifyComplete();
    }

    @Test
    @DisplayName("InMemoryStore keyword search matches rawContent")
    void testInMemoryStoreKeywordSearchMatchesRawContent() {
        MemoryEntry entry =
                new MemoryEntry(
                        UUID.randomUUID().toString(),
                        "test-agent",
                        "summary only",
                        "the original conversation had special-keyword-xyz in it",
                        MemoryScope.SESSION,
                        0.5,
                        null,
                        Set.of("test"),
                        Instant.now(),
                        null);
        store.save(entry).block();

        // Search by keyword that only exists in rawContent
        MemoryQuery query =
                MemoryQuery.builder()
                        .agentId("test-agent")
                        .keyword("special-keyword-xyz")
                        .limit(10)
                        .build();

        StepVerifier.create(store.search(query))
                .assertNext(
                        e -> {
                            assertEquals("summary only", e.content());
                            assertTrue(e.rawContent().contains("special-keyword-xyz"));
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Integration: compaction saves then recallFull retrieves with rawContent")
    void testCompactionSaveThenRecallFull() {
        // Simulate what CompactionTrigger.storeCompactionMemory does
        String rawContent =
                "[user] Hello, tell me about Java streams\n[assistant] Java streams provide...";
        String summary = "Discussion about Java streams API";

        MemoryEntry compactionEntry =
                new MemoryEntry(
                        UUID.randomUUID().toString(),
                        "test-agent",
                        summary,
                        rawContent,
                        MemoryScope.SESSION,
                        0.6,
                        null,
                        Set.of("compaction-summary"),
                        Instant.now(),
                        null);
        store.save(compactionEntry).block();

        // Retrieve via recallFull
        StepVerifier.create(memory.recallFull("compaction-summary"))
                .assertNext(
                        e -> {
                            assertEquals(summary, e.content());
                            assertEquals(rawContent, e.rawContent());
                            assertNotNull(e.rawContent());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName(
            "Integration: remember then compact then recallFull shows both summary and original")
    void testRememberCompactRecallFullPipeline() {
        // Step 1: remember something (simulating pre-compaction state)
        memory.remember("topic", "original detailed content about machine learning").block();

        // Step 2: simulate compaction — save a new entry with rawContent
        String rawContent = "original detailed content about machine learning";
        String compactedSummary = "ML discussion summary";

        MemoryEntry compacted =
                new MemoryEntry(
                        UUID.randomUUID().toString(),
                        "test-agent",
                        compactedSummary,
                        rawContent,
                        MemoryScope.SESSION,
                        0.6,
                        null,
                        Set.of("compaction-summary"),
                        Instant.now(),
                        null);
        store.save(compacted).block();

        // Step 3: recallFull should return the compacted entry with both fields
        StepVerifier.create(memory.recallFull("compaction-summary"))
                .assertNext(
                        e -> {
                            assertEquals(compactedSummary, e.content());
                            assertEquals(rawContent, e.rawContent());
                        })
                .verifyComplete();

        // Step 4: recallAllFull should include both original and compacted entries
        StepVerifier.create(memory.recallAllFull(10).collectList())
                .assertNext(
                        list -> {
                            assertEquals(2, list.size());
                            // At least one entry should have rawContent
                            assertTrue(list.stream().anyMatch(e -> e.rawContent() != null));
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("InMemoryStore keyword search does not match rawContent when rawContent is null")
    void testKeywordSearchWithNullRawContent() {
        MemoryEntry entry =
                new MemoryEntry(
                        UUID.randomUUID().toString(),
                        "test-agent",
                        "some content",
                        null,
                        MemoryScope.SESSION,
                        0.5,
                        null,
                        Set.of("test"),
                        Instant.now(),
                        null);
        store.save(entry).block();

        // Search by keyword not in content — should return empty
        MemoryQuery query =
                MemoryQuery.builder()
                        .agentId("test-agent")
                        .keyword("nonexistent-term")
                        .limit(10)
                        .build();

        StepVerifier.create(store.search(query)).verifyComplete();
    }
}
