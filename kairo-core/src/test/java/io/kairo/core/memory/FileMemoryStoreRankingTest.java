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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.memory.MemoryEntry;
import io.kairo.api.memory.MemoryQuery;
import io.kairo.api.memory.MemoryScope;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileMemoryStoreRankingTest {

    @TempDir Path tempDir;

    private MemoryEntry entry(
            String id, String content, double importance, float[] emb, String... tags) {
        return new MemoryEntry(
                id,
                "agent",
                content,
                null,
                MemoryScope.AGENT,
                importance,
                emb,
                Set.of(tags),
                Instant.now(),
                null);
    }

    @Test
    void lexicalRankingPrefersTermOverlapAndExcludesNonMatches() {
        FileMemoryStore store = new FileMemoryStore(tempDir);
        store.save(entry("1", "the cat sat on the mat", 0.5, null)).block();
        store.save(entry("2", "quantum physics and entanglement", 0.9, null)).block();
        store.save(entry("3", "the cat chased the cat up a tree", 0.5, null)).block();

        List<MemoryEntry> results =
                store.search(MemoryQuery.builder().keyword("cat").limit(10).build())
                        .collectList()
                        .block();

        // Only the cat entries match; the physics entry is excluded (zero term overlap, despite
        // higher importance and equal recency).
        assertThat(results).extracting(MemoryEntry::id).containsExactlyInAnyOrder("1", "3");
    }

    @Test
    void filtersByImportanceAndTags() {
        FileMemoryStore store = new FileMemoryStore(tempDir);
        store.save(entry("1", "alpha note about cats", 0.2, null, "low")).block();
        store.save(entry("2", "beta note about cats", 0.8, null, "high")).block();

        List<MemoryEntry> byImportance =
                store.search(
                                MemoryQuery.builder()
                                        .keyword("cats")
                                        .minImportance(0.5)
                                        .limit(10)
                                        .build())
                        .collectList()
                        .block();
        assertThat(byImportance).extracting(MemoryEntry::id).containsExactly("2");

        List<MemoryEntry> byTag =
                store.search(
                                MemoryQuery.builder()
                                        .keyword("cats")
                                        .tags(Set.of("low"))
                                        .limit(10)
                                        .build())
                        .collectList()
                        .block();
        assertThat(byTag).extracting(MemoryEntry::id).containsExactly("1");
    }

    @Test
    void vectorRankingByCosineSimilarity() {
        FileMemoryStore store = new FileMemoryStore(tempDir);
        store.save(entry("near", "x", 0.5, new float[] {1f, 0f, 0f})).block();
        store.save(entry("far", "y", 0.5, new float[] {0f, 1f, 0f})).block();

        List<MemoryEntry> results =
                store.search(
                                MemoryQuery.builder()
                                        .queryVector(new float[] {0.9f, 0.1f, 0f})
                                        .limit(10)
                                        .build())
                        .collectList()
                        .block();

        assertThat(results.get(0).id()).isEqualTo("near");
    }

    @Test
    void emptyKeywordReturnsRecentEntries() {
        FileMemoryStore store = new FileMemoryStore(tempDir);
        store.save(entry("1", "anything", 0.5, null)).block();
        store.save(entry("2", "something", 0.5, null)).block();

        List<MemoryEntry> results =
                store.search(MemoryQuery.builder().limit(10).build()).collectList().block();
        assertThat(results).hasSize(2);
    }
}
