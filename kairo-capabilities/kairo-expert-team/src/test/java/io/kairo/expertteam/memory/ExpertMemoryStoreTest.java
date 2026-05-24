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
package io.kairo.expertteam.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

class ExpertMemoryStoreTest {

    @TempDir Path tempDir;

    ExpertMemoryStore store;

    @BeforeEach
    void setUp() {
        store = new ExpertMemoryStore(tempDir);
    }

    @Test
    void recordAndRecallLessons() {
        var lessons =
                List.of(
                        new ExpertMemoryEntry(
                                "expert:coder",
                                "project-a",
                                "Use records for DTOs",
                                Instant.now(),
                                0.9),
                        new ExpertMemoryEntry(
                                "expert:coder",
                                "project-a",
                                "Prefer composition over inheritance",
                                Instant.now(),
                                0.7));

        StepVerifier.create(store.recordLessons("expert:coder", "project-a", lessons))
                .verifyComplete();

        StepVerifier.create(store.recall("expert:coder", "project-a", 10))
                .assertNext(
                        entry -> {
                            assertThat(entry.lesson()).isEqualTo("Use records for DTOs");
                            assertThat(entry.relevanceScore()).isEqualTo(0.9);
                        })
                .assertNext(
                        entry -> {
                            assertThat(entry.lesson())
                                    .isEqualTo("Prefer composition over inheritance");
                            assertThat(entry.relevanceScore()).isEqualTo(0.7);
                        })
                .verifyComplete();
    }

    @Test
    void recallRespectsTopNAndRelevanceOrdering() {
        var lessons =
                List.of(
                        new ExpertMemoryEntry(
                                "expert:coder", "ns", "Low relevance", Instant.now(), 0.2),
                        new ExpertMemoryEntry(
                                "expert:coder", "ns", "High relevance", Instant.now(), 0.95),
                        new ExpertMemoryEntry(
                                "expert:coder", "ns", "Medium relevance", Instant.now(), 0.6));

        StepVerifier.create(store.recordLessons("expert:coder", "ns", lessons)).verifyComplete();

        // Top 2: should get 0.95 and 0.6
        StepVerifier.create(store.recall("expert:coder", "ns", 2))
                .assertNext(entry -> assertThat(entry.relevanceScore()).isEqualTo(0.95))
                .assertNext(entry -> assertThat(entry.relevanceScore()).isEqualTo(0.6))
                .verifyComplete();
    }

    @Test
    void clearRemovesEntriesForNamespace() {
        var lessons =
                List.of(
                        new ExpertMemoryEntry(
                                "expert:coder", "to-clear", "Some lesson", Instant.now(), 0.8));

        StepVerifier.create(store.recordLessons("expert:coder", "to-clear", lessons))
                .verifyComplete();

        StepVerifier.create(store.clear("to-clear")).verifyComplete();

        StepVerifier.create(store.recall("expert:coder", "to-clear", 10)).verifyComplete(); // empty
    }

    @Test
    void emptyStoreReturnsEmptyFlux() {
        StepVerifier.create(store.recall("expert:coder", "nonexistent", 10)).verifyComplete();
    }

    @Test
    void persistenceSurvivesNewInstance() {
        var lessons =
                List.of(
                        new ExpertMemoryEntry(
                                "expert:architect",
                                "default",
                                "Design for extensibility",
                                Instant.now(),
                                0.85));

        StepVerifier.create(store.recordLessons("expert:architect", "default", lessons))
                .verifyComplete();

        // Create a new store instance pointing to the same directory
        ExpertMemoryStore newStore = new ExpertMemoryStore(tempDir);

        StepVerifier.create(newStore.recall("expert:architect", "default", 5))
                .assertNext(
                        entry -> {
                            assertThat(entry.lesson()).isEqualTo("Design for extensibility");
                            assertThat(entry.relevanceScore()).isEqualTo(0.85);
                            assertThat(entry.roleId()).isEqualTo("expert:architect");
                        })
                .verifyComplete();
    }

    @Test
    void concurrentWritesToDifferentNamespaces() {
        var lessons1 =
                List.of(
                        new ExpertMemoryEntry(
                                "expert:coder", "ns1", "Lesson in ns1", Instant.now(), 0.7));
        var lessons2 =
                List.of(
                        new ExpertMemoryEntry(
                                "expert:coder", "ns2", "Lesson in ns2", Instant.now(), 0.8));

        // Write concurrently to different namespaces
        StepVerifier.create(
                        store.recordLessons("expert:coder", "ns1", lessons1)
                                .then(store.recordLessons("expert:coder", "ns2", lessons2)))
                .verifyComplete();

        // Both should be independently readable
        StepVerifier.create(store.recall("expert:coder", "ns1", 5))
                .assertNext(entry -> assertThat(entry.lesson()).isEqualTo("Lesson in ns1"))
                .verifyComplete();

        StepVerifier.create(store.recall("expert:coder", "ns2", 5))
                .assertNext(entry -> assertThat(entry.lesson()).isEqualTo("Lesson in ns2"))
                .verifyComplete();
    }

    @Test
    void recordLessonsAppendsToExistingEntries() {
        var batch1 =
                List.of(
                        new ExpertMemoryEntry(
                                "expert:coder", "append-ns", "First lesson", Instant.now(), 0.7));
        var batch2 =
                List.of(
                        new ExpertMemoryEntry(
                                "expert:coder", "append-ns", "Second lesson", Instant.now(), 0.9));

        StepVerifier.create(
                        store.recordLessons("expert:coder", "append-ns", batch1)
                                .then(store.recordLessons("expert:coder", "append-ns", batch2)))
                .verifyComplete();

        StepVerifier.create(store.recall("expert:coder", "append-ns", 10))
                .assertNext(entry -> assertThat(entry.lesson()).isEqualTo("Second lesson"))
                .assertNext(entry -> assertThat(entry.lesson()).isEqualTo("First lesson"))
                .verifyComplete();
    }

    @Test
    void listNamespacesReturnsAvailableNamespaces() {
        var lessons1 =
                List.of(
                        new ExpertMemoryEntry(
                                "expert:coder", "alpha", "A lesson", Instant.now(), 0.5));
        var lessons2 =
                List.of(
                        new ExpertMemoryEntry(
                                "expert:coder", "beta", "B lesson", Instant.now(), 0.6));

        StepVerifier.create(
                        store.recordLessons("expert:coder", "alpha", lessons1)
                                .then(store.recordLessons("expert:coder", "beta", lessons2)))
                .verifyComplete();

        StepVerifier.create(store.listNamespaces("expert:coder").collectList())
                .assertNext(
                        namespaces ->
                                assertThat(namespaces).containsExactlyInAnyOrder("alpha", "beta"))
                .verifyComplete();
    }

    @Test
    void recordEmptyLessonsIsNoOp() {
        StepVerifier.create(store.recordLessons("expert:coder", "ns", List.of())).verifyComplete();

        StepVerifier.create(store.recall("expert:coder", "ns", 10)).verifyComplete(); // still empty
    }

    @Test
    void namespaceDefaultsToRoleIdWhenBlank() {
        var lessons =
                List.of(
                        new ExpertMemoryEntry(
                                "expert:coder", null, "No namespace lesson", Instant.now(), 0.75));

        // namespace defaults to roleId in both the entry and the store
        StepVerifier.create(store.recordLessons("expert:coder", null, lessons)).verifyComplete();

        StepVerifier.create(store.recall("expert:coder", null, 5))
                .assertNext(
                        entry -> {
                            assertThat(entry.namespace()).isEqualTo("expert:coder");
                            assertThat(entry.lesson()).isEqualTo("No namespace lesson");
                        })
                .verifyComplete();
    }
}
