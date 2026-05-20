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
package io.kairo.core.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileTaskStoreTest {

    @TempDir Path tempDir;

    private FileTaskStore store;

    @BeforeEach
    void setUp() {
        store = new FileTaskStore(tempDir);
    }

    @Test
    void createAssignsAutoIncrementId() {
        TaskEntry first = store.create("First task", null, null, null);
        TaskEntry second = store.create("Second task", null, null, null);

        assertThat(first.id()).isEqualTo("1");
        assertThat(second.id()).isEqualTo("2");
    }

    @Test
    void createPersistsToFile() {
        store.create("Persistent task", "description", "Working on it", Map.of("key", "val"));

        Path tasksFile = tempDir.resolve(FileTaskStore.TASKS_FILE);
        assertThat(tasksFile).exists();
    }

    @Test
    void createSetsDefaultStatus() {
        TaskEntry entry = store.create("Task", null, null, null);
        assertThat(entry.status()).isEqualTo(TaskStatus.PENDING);
    }

    @Test
    void createWithAllFields() {
        TaskEntry entry =
                store.create(
                        "Full task",
                        "detailed description",
                        "Creating task",
                        Map.of("priority", "high"));

        assertThat(entry.subject()).isEqualTo("Full task");
        assertThat(entry.description()).isEqualTo("detailed description");
        assertThat(entry.activeForm()).isEqualTo("Creating task");
        assertThat(entry.metadata()).containsEntry("priority", "high");
        assertThat(entry.owner()).isNull();
        assertThat(entry.blocks()).isEmpty();
        assertThat(entry.blockedBy()).isEmpty();
    }

    @Test
    void getReturnsEmptyForMissingId() {
        assertThat(store.get("999")).isEmpty();
    }

    @Test
    void getReturnsCreatedTask() {
        TaskEntry created = store.create("Task", null, null, null);
        Optional<TaskEntry> found = store.get(created.id());

        assertThat(found).isPresent();
        assertThat(found.get().subject()).isEqualTo("Task");
    }

    @Test
    void updatePersistsChanges() {
        TaskEntry created = store.create("Original", null, null, null);
        store.update(created.withStatus(TaskStatus.IN_PROGRESS).withOwner("agent-1"));

        Optional<TaskEntry> updated = store.get(created.id());
        assertThat(updated).isPresent();
        assertThat(updated.get().status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(updated.get().owner()).isEqualTo("agent-1");
    }

    @Test
    void deleteRemovesTask() {
        TaskEntry created = store.create("To delete", null, null, null);
        store.delete(created.id());

        assertThat(store.get(created.id())).isEmpty();
        assertThat(store.listAll()).isEmpty();
    }

    @Test
    void deleteCascadesReferences() {
        TaskEntry a = store.create("A", null, null, null);
        TaskEntry b = store.create("B", null, null, null);
        store.addDependency(a.id(), b.id());

        assertThat(store.get(b.id()).get().blockedBy()).contains(a.id());

        store.delete(a.id());

        assertThat(store.get(b.id()).get().blockedBy()).isEmpty();
    }

    @Test
    void addDependencyUpdatesBothSides() {
        TaskEntry a = store.create("A", null, null, null);
        TaskEntry b = store.create("B", null, null, null);

        store.addDependency(a.id(), b.id());

        assertThat(store.get(a.id()).get().blocks()).contains(b.id());
        assertThat(store.get(b.id()).get().blockedBy()).contains(a.id());
    }

    @Test
    void addDependencyIgnoresMissingTasks() {
        TaskEntry a = store.create("A", null, null, null);
        store.addDependency(a.id(), "999");
        assertThat(store.get(a.id()).get().blocks()).isEmpty();
    }

    @Test
    void cascadeCompletionRemovesFromBlockedBy() {
        TaskEntry a = store.create("A", null, null, null);
        TaskEntry b = store.create("B", null, null, null);
        TaskEntry c = store.create("C", null, null, null);

        store.addDependency(a.id(), b.id());
        store.addDependency(a.id(), c.id());

        store.cascadeCompletion(a.id());

        assertThat(store.get(b.id()).get().blockedBy()).isEmpty();
        assertThat(store.get(c.id()).get().blockedBy()).isEmpty();
    }

    @Test
    void pendingOrInProgressCountExcludesCompleted() {
        store.create("Task 1", null, null, null);
        store.create("Task 2", null, null, null);
        TaskEntry third = store.create("Task 3", null, null, null);
        store.update(third.withStatus(TaskStatus.COMPLETED));

        assertThat(store.pendingOrInProgressCount()).isEqualTo(2);
    }

    @Test
    void listAllReturnsInsertionOrder() {
        store.create("First", null, null, null);
        store.create("Second", null, null, null);
        store.create("Third", null, null, null);

        List<TaskEntry> all = store.listAll();
        assertThat(all).hasSize(3);
        assertThat(all.get(0).subject()).isEqualTo("First");
        assertThat(all.get(1).subject()).isEqualTo("Second");
        assertThat(all.get(2).subject()).isEqualTo("Third");
    }

    @Test
    void loadFromExistingFile() throws IOException {
        Path tasksFile = tempDir.resolve(FileTaskStore.TASKS_FILE);
        Files.createDirectories(tasksFile.getParent());
        Files.writeString(
                tasksFile,
                """
                {
                  "_nextId": 5,
                  "tasks": [
                    {
                      "id": "3",
                      "subject": "Existing task",
                      "status": "in_progress",
                      "owner": "agent-x",
                      "blocks": ["4"],
                      "blockedBy": [],
                      "metadata": {"source": "test"}
                    },
                    {
                      "id": "4",
                      "subject": "Blocked task",
                      "status": "pending",
                      "blocks": [],
                      "blockedBy": ["3"],
                      "metadata": {}
                    }
                  ]
                }
                """,
                StandardCharsets.UTF_8);

        FileTaskStore freshStore = new FileTaskStore(tempDir);
        List<TaskEntry> all = freshStore.listAll();

        assertThat(all).hasSize(2);
        assertThat(all.get(0).id()).isEqualTo("3");
        assertThat(all.get(0).owner()).isEqualTo("agent-x");
        assertThat(all.get(0).blocks()).containsExactly("4");
        assertThat(all.get(1).blockedBy()).containsExactly("3");

        TaskEntry newTask = freshStore.create("New after load", null, null, null);
        assertThat(newTask.id()).isEqualTo("5");
    }

    @Test
    void loadEmptyFileReturnsEmptyState() throws IOException {
        Path tasksFile = tempDir.resolve(FileTaskStore.TASKS_FILE);
        Files.createDirectories(tasksFile.getParent());
        Files.writeString(tasksFile, "{\"_nextId\": 1, \"tasks\": []}", StandardCharsets.UTF_8);

        FileTaskStore freshStore = new FileTaskStore(tempDir);
        assertThat(freshStore.listAll()).isEmpty();
        assertThat(freshStore.pendingOrInProgressCount()).isZero();
    }

    @Test
    void concurrentCreatesAllUnique() throws InterruptedException {
        int threadCount = 10;
        int tasksPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        CopyOnWriteArrayList<String> allIds = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(
                    () -> {
                        try {
                            for (int j = 0; j < tasksPerThread; j++) {
                                TaskEntry entry = store.create("Task", null, null, null);
                                allIds.add(entry.id());
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
        }

        latch.await();
        executor.shutdown();

        assertThat(allIds).hasSize(threadCount * tasksPerThread);
        assertThat(Set.copyOf(allIds)).hasSize(threadCount * tasksPerThread);
    }

    @Test
    void unresolvedBlockersFiltersCompleted() {
        TaskEntry a = store.create("A", null, null, null);
        TaskEntry b = store.create("B", null, null, null);
        TaskEntry c = store.create("C", null, null, null);

        store.addDependency(a.id(), c.id());
        store.addDependency(b.id(), c.id());

        store.update(store.get(a.id()).get().withStatus(TaskStatus.COMPLETED));

        Map<String, TaskEntry> allTasks = new java.util.LinkedHashMap<>();
        for (TaskEntry t : store.listAll()) {
            allTasks.put(t.id(), t);
        }

        Set<String> unresolved = store.get(c.id()).get().unresolvedBlockers(allTasks);
        assertThat(unresolved).containsExactly(b.id());
    }

    @Test
    void mergedMetadataRemovesNullKeys() {
        TaskEntry entry = store.create("Task", null, null, Map.of("keep", "yes", "remove", "no"));
        Map<String, Object> patch = new java.util.HashMap<>();
        patch.put("remove", null);
        patch.put("add", "new");

        TaskEntry updated = entry.withMergedMetadata(patch);
        store.update(updated);

        TaskEntry reloaded = store.get(entry.id()).get();
        assertThat(reloaded.metadata()).containsEntry("keep", "yes");
        assertThat(reloaded.metadata()).containsEntry("add", "new");
        assertThat(reloaded.metadata()).doesNotContainKey("remove");
    }
}
