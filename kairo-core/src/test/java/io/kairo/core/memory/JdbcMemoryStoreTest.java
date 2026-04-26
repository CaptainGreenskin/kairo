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
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcMemoryStoreTest {

    private JdbcDataSource dataSource;
    private JdbcMemoryStore store;

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:test_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        store = new JdbcMemoryStore(dataSource);
    }

    @Test
    void schemaAutoCreation() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            ResultSet rs =
                    conn.getMetaData()
                            .getTables(null, null, "KAIRO_MEMORY", new String[] {"TABLE"});
            assertThat(rs.next()).isTrue();
        }
    }

    @Test
    void storeAndRetrieveById() {
        MemoryEntry entry =
                new MemoryEntry(
                        "id-1",
                        "agent-1",
                        "Hello world",
                        "raw hello",
                        MemoryScope.AGENT,
                        0.8,
                        null,
                        Set.of("greeting"),
                        Instant.now().truncatedTo(ChronoUnit.MILLIS),
                        Map.of("key", "value"));

        store.save(entry).block();
        MemoryEntry retrieved = store.get("id-1").block();

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.id()).isEqualTo("id-1");
        assertThat(retrieved.agentId()).isEqualTo("agent-1");
        assertThat(retrieved.content()).isEqualTo("Hello world");
        assertThat(retrieved.rawContent()).isEqualTo("raw hello");
        assertThat(retrieved.scope()).isEqualTo(MemoryScope.AGENT);
        assertThat(retrieved.importance()).isEqualTo(0.8);
        assertThat(retrieved.tags()).contains("greeting");
        assertThat(retrieved.metadata()).containsEntry("key", "value");
    }

    @Test
    void searchByKeyword() {
        store.save(entry("id-1", "agent-1", "The quick brown fox", Set.of())).block();
        store.save(entry("id-2", "agent-1", "A lazy dog", Set.of())).block();

        List<MemoryEntry> results =
                store.search(MemoryQuery.builder().keyword("fox").build()).collectList().block();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("id-1");
    }

    @Test
    void searchByTags() {
        store.save(entry("id-1", "agent-1", "content-1", Set.of("java", "spring"))).block();
        store.save(entry("id-2", "agent-1", "content-2", Set.of("python"))).block();

        List<MemoryEntry> results =
                store.search(MemoryQuery.builder().tags(Set.of("java")).build())
                        .collectList()
                        .block();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("id-1");
    }

    @Test
    void searchByImportanceThreshold() {
        store.save(entryWithImportance("id-1", 0.3)).block();
        store.save(entryWithImportance("id-2", 0.7)).block();
        store.save(entryWithImportance("id-3", 0.9)).block();

        List<MemoryEntry> results =
                store.search(MemoryQuery.builder().minImportance(0.6).build())
                        .collectList()
                        .block();

        assertThat(results).hasSize(2);
        assertThat(results).extracting(MemoryEntry::id).containsExactlyInAnyOrder("id-2", "id-3");
    }

    @Test
    void searchByDateRange() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        store.save(entryWithTimestamp("id-1", twoDaysAgo)).block();
        store.save(entryWithTimestamp("id-2", yesterday)).block();
        store.save(entryWithTimestamp("id-3", now)).block();

        List<MemoryEntry> results =
                store.search(
                                MemoryQuery.builder()
                                        .from(yesterday.minus(1, ChronoUnit.HOURS))
                                        .to(now.plus(1, ChronoUnit.HOURS))
                                        .build())
                        .collectList()
                        .block();

        assertThat(results).hasSize(2);
        assertThat(results).extracting(MemoryEntry::id).containsExactlyInAnyOrder("id-2", "id-3");
    }

    @Test
    void searchWithCombinedFilters() {
        store.save(
                        new MemoryEntry(
                                "id-1",
                                "agent-1",
                                "Java memory management",
                                null,
                                MemoryScope.AGENT,
                                0.9,
                                null,
                                Set.of("java", "memory"),
                                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                                null))
                .block();
        store.save(
                        new MemoryEntry(
                                "id-2",
                                "agent-1",
                                "Java concurrency basics",
                                null,
                                MemoryScope.AGENT,
                                0.3,
                                null,
                                Set.of("java", "concurrency"),
                                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                                null))
                .block();
        store.save(
                        new MemoryEntry(
                                "id-3",
                                "agent-1",
                                "Python decorators",
                                null,
                                MemoryScope.AGENT,
                                0.8,
                                null,
                                Set.of("python"),
                                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                                null))
                .block();

        List<MemoryEntry> results =
                store.search(
                                MemoryQuery.builder()
                                        .keyword("Java")
                                        .tags(Set.of("java"))
                                        .minImportance(0.5)
                                        .build())
                        .collectList()
                        .block();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("id-1");
    }

    @Test
    void searchWithLimit() {
        for (int i = 0; i < 10; i++) {
            store.save(entry("id-" + i, "agent-1", "content " + i, Set.of())).block();
        }

        List<MemoryEntry> results =
                store.search(MemoryQuery.builder().limit(3).build()).collectList().block();

        assertThat(results).hasSize(3);
    }

    @Test
    void deleteEntry() {
        store.save(entry("id-1", "agent-1", "to be deleted", Set.of())).block();
        assertThat(store.get("id-1").block()).isNotNull();

        store.delete("id-1").block();
        assertThat(store.get("id-1").block()).isNull();
    }

    @Test
    void upsertExistingEntry() {
        store.save(entry("id-1", "agent-1", "original content", Set.of("v1"))).block();

        store.save(entry("id-1", "agent-1", "updated content", Set.of("v2"))).block();

        MemoryEntry retrieved = store.get("id-1").block();
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.content()).isEqualTo("updated content");
        assertThat(retrieved.tags()).contains("v2");
        assertThat(retrieved.tags()).doesNotContain("v1");
    }

    @Test
    void searchWithNullAgentIdReturnsAllAgents() {
        store.save(entry("id-1", "agent-1", "content from agent 1", Set.of())).block();
        store.save(entry("id-2", "agent-2", "content from agent 2", Set.of())).block();
        store.save(entry("id-3", null, "content with no agent", Set.of())).block();

        List<MemoryEntry> results =
                store.search(MemoryQuery.builder().build()).collectList().block();

        assertThat(results).hasSize(3);
    }

    @Test
    void searchFiltersByAgentId() {
        store.save(entry("id-1", "agent-1", "content from agent 1", Set.of())).block();
        store.save(entry("id-2", "agent-2", "content from agent 2", Set.of())).block();

        List<MemoryEntry> results =
                store.search(MemoryQuery.builder().agentId("agent-1").build())
                        .collectList()
                        .block();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).agentId()).isEqualTo("agent-1");
    }

    @Test
    void embeddingSerializationRoundTrip() {
        float[] embedding = {0.1f, 0.2f, 0.3f, -0.5f, 1.0f};
        MemoryEntry entry =
                new MemoryEntry(
                        "id-embed",
                        "agent-1",
                        "embedding test",
                        null,
                        MemoryScope.AGENT,
                        0.5,
                        embedding,
                        Set.of(),
                        Instant.now().truncatedTo(ChronoUnit.MILLIS),
                        null);

        store.save(entry).block();
        MemoryEntry retrieved = store.get("id-embed").block();

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.embedding()).isNotNull();
        assertThat(retrieved.embedding()).hasSize(5);
        assertThat(retrieved.embedding()[0]).isEqualTo(0.1f);
        assertThat(retrieved.embedding()[1]).isEqualTo(0.2f);
        assertThat(retrieved.embedding()[2]).isEqualTo(0.3f);
        assertThat(retrieved.embedding()[3]).isEqualTo(-0.5f);
        assertThat(retrieved.embedding()[4]).isEqualTo(1.0f);
    }

    @Test
    void getReturnsEmptyForNonExistentId() {
        MemoryEntry result = store.get("non-existent").block();
        assertThat(result).isNull();
    }

    @Test
    void searchWithLikeWildcardsInKeyword() {
        store.save(entry("id-1", "agent-1", "normal text", Set.of())).block();
        store.save(entry("id-2", "agent-1", "100% complete", Set.of())).block();

        // Searching for "%" should NOT match everything — wildcards should be escaped
        List<MemoryEntry> results =
                store.search(MemoryQuery.builder().keyword("%").build()).collectList().block();

        // Only the entry containing literal "%" should match
        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("id-2");
    }

    @Test
    void searchWithUnderscoreWildcardInKeyword() {
        store.save(entry("id-1", "agent-1", "abc", Set.of())).block();
        store.save(entry("id-2", "agent-1", "a_c special", Set.of())).block();

        // "_" in SQL LIKE matches any single char; should be escaped to match literal "_"
        List<MemoryEntry> results =
                store.search(MemoryQuery.builder().keyword("a_c").build()).collectList().block();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("id-2");
    }

    @Test
    void searchWithTagContainingSpecialChars() {
        store.save(entry("id-1", "agent-1", "content-1", Set.of("c++"))).block();
        store.save(entry("id-2", "agent-1", "content-2", Set.of("node.js"))).block();

        List<MemoryEntry> results =
                store.search(MemoryQuery.builder().tags(Set.of("c++")).build())
                        .collectList()
                        .block();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("id-1");
    }

    @Test
    void tagsDoNotFalseMatch() {
        store.save(entry("id-1", "agent-1", "content-1", Set.of("java"))).block();
        store.save(entry("id-2", "agent-1", "content-2", Set.of("javascript"))).block();

        // Search for tag "java" — should NOT match "javascript"
        List<MemoryEntry> results =
                store.search(MemoryQuery.builder().tags(Set.of("java")).build())
                        .collectList()
                        .block();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("id-1");
    }

    // --- Helper methods ---

    private MemoryEntry entry(String id, String agentId, String content, Set<String> tags) {
        return new MemoryEntry(
                id,
                agentId,
                content,
                null,
                MemoryScope.AGENT,
                0.5,
                null,
                tags,
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                null);
    }

    private MemoryEntry entryWithImportance(String id, double importance) {
        return new MemoryEntry(
                id,
                "agent-1",
                "content",
                null,
                MemoryScope.AGENT,
                importance,
                null,
                Set.of(),
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                null);
    }

    private MemoryEntry entryWithTimestamp(String id, Instant timestamp) {
        return new MemoryEntry(
                id,
                "agent-1",
                "content",
                null,
                MemoryScope.AGENT,
                0.5,
                null,
                Set.of(),
                timestamp,
                null);
    }
}
