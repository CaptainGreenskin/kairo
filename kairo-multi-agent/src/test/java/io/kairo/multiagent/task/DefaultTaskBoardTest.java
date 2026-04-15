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
package io.kairo.multiagent.task;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.task.Task;
import io.kairo.api.task.TaskStatus;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultTaskBoardTest {

    private DefaultTaskBoard board;

    @BeforeEach
    void setUp() {
        board = new DefaultTaskBoard();
    }

    // ==================== CREATE ====================

    @Test
    void createTaskShouldStoreAndReturnTask() {
        Task task = board.create("Fix bug", "Fix the NPE in service");
        assertNotNull(task);
        assertNotNull(task.id());
        assertEquals("Fix bug", task.subject());
        assertEquals("Fix the NPE in service", task.description());
        assertEquals(TaskStatus.PENDING, task.status());
    }

    @Test
    void createTaskShouldAssignIncrementingIds() {
        Task t1 = board.create("Task 1", "desc");
        Task t2 = board.create("Task 2", "desc");
        Task t3 = board.create("Task 3", "desc");
        assertEquals("1", t1.id());
        assertEquals("2", t2.id());
        assertEquals("3", t3.id());
    }

    @Test
    void createdTaskShouldBeRetrievableById() {
        Task task = board.create("Test", "desc");
        Task retrieved = board.get(task.id());
        assertNotNull(retrieved);
        assertEquals(task.id(), retrieved.id());
        assertEquals("Test", retrieved.subject());
    }

    // ==================== UPDATE ====================

    @Test
    void updateStatusFromPendingToInProgress() {
        Task task = board.create("Work item", "desc");
        Task updated = board.update(task.id(), TaskStatus.IN_PROGRESS);
        assertEquals(TaskStatus.IN_PROGRESS, updated.status());
    }

    @Test
    void updateStatusFromInProgressToCompleted() {
        Task task = board.create("Work item", "desc");
        board.update(task.id(), TaskStatus.IN_PROGRESS);
        Task updated = board.update(task.id(), TaskStatus.COMPLETED);
        assertEquals(TaskStatus.COMPLETED, updated.status());
    }

    @Test
    void updateStatusToFailed() {
        Task task = board.create("Flaky", "desc");
        board.update(task.id(), TaskStatus.IN_PROGRESS);
        Task updated = board.update(task.id(), TaskStatus.FAILED);
        assertEquals(TaskStatus.FAILED, updated.status());
    }

    @Test
    void updateStatusToCancelled() {
        Task task = board.create("Cancelled work", "desc");
        Task updated = board.update(task.id(), TaskStatus.CANCELLED);
        assertEquals(TaskStatus.CANCELLED, updated.status());
    }

    @Test
    void updateNonExistentTaskShouldThrow() {
        assertThrows(
                IllegalArgumentException.class, () -> board.update("999", TaskStatus.COMPLETED));
    }

    // ==================== GET ====================

    @Test
    void getNonExistentTaskShouldReturnNull() {
        assertNull(board.get("does-not-exist"));
    }

    // ==================== LIST ====================

    @Test
    void listShouldReturnAllTasks() {
        board.create("A", "a");
        board.create("B", "b");
        board.create("C", "c");
        List<Task> all = board.list();
        assertEquals(3, all.size());
    }

    @Test
    void listOnEmptyBoardShouldReturnEmpty() {
        assertTrue(board.list().isEmpty());
    }

    // ==================== DEPENDENCIES ====================

    @Test
    void addDependencyShouldBlockTask() {
        Task a = board.create("A", "");
        Task b = board.create("B", "");
        board.addDependency(b.id(), a.id());

        Task bRetrieved = board.get(b.id());
        assertTrue(bRetrieved.blockedBy().contains(a.id()));

        Task aRetrieved = board.get(a.id());
        assertTrue(aRetrieved.blocks().contains(b.id()));
    }

    @Test
    void addDependencyWithInvalidTaskShouldThrow() {
        Task a = board.create("A", "");
        assertThrows(IllegalArgumentException.class, () -> board.addDependency("999", a.id()));
    }

    @Test
    void addDependencyWithInvalidBlockerShouldThrow() {
        Task a = board.create("A", "");
        assertThrows(IllegalArgumentException.class, () -> board.addDependency(a.id(), "999"));
    }

    // ==================== AUTO-UNBLOCK ====================

    @Test
    void completingTaskShouldResolveDownstreamDependencies() {
        Task a = board.create("A", "");
        Task b = board.create("B", "");
        board.addDependency(b.id(), a.id());

        // Before completing A, B is blocked
        assertFalse(board.get(b.id()).blockedBy().isEmpty());

        // Complete A
        board.update(a.id(), TaskStatus.COMPLETED);

        // After completing A, B should be unblocked
        assertTrue(board.get(b.id()).blockedBy().isEmpty());
    }

    @Test
    void completingOneOfMultipleBlockersShouldNotFullyUnblock() {
        Task a = board.create("A", "");
        Task b = board.create("B", "");
        Task c = board.create("C", "");
        board.addDependency(c.id(), a.id());
        board.addDependency(c.id(), b.id());

        board.update(a.id(), TaskStatus.COMPLETED);

        // C still blocked by B
        assertFalse(board.get(c.id()).blockedBy().isEmpty());
        assertTrue(board.get(c.id()).blockedBy().contains(b.id()));

        board.update(b.id(), TaskStatus.COMPLETED);

        // Now C is fully unblocked
        assertTrue(board.get(c.id()).blockedBy().isEmpty());
    }

    // ==================== getUnblockedTasks ====================

    @Test
    void getUnblockedTasksShouldReturnOnlyPendingUnblockedTasks() {
        Task a = board.create("A", "");
        Task b = board.create("B", "");
        Task c = board.create("C", "");
        board.addDependency(c.id(), a.id());

        // A and B are pending and unblocked; C is pending but blocked
        List<Task> unblocked = board.getUnblockedTasks();
        assertEquals(2, unblocked.size());
        assertTrue(unblocked.stream().anyMatch(t -> t.id().equals(a.id())));
        assertTrue(unblocked.stream().anyMatch(t -> t.id().equals(b.id())));
    }

    @Test
    void getUnblockedTasksShouldExcludeInProgressTasks() {
        Task a = board.create("A", "");
        board.update(a.id(), TaskStatus.IN_PROGRESS);

        List<Task> unblocked = board.getUnblockedTasks();
        assertTrue(unblocked.isEmpty());
    }

    @Test
    void getUnblockedTasksShouldExcludeCompletedTasks() {
        Task a = board.create("A", "");
        board.update(a.id(), TaskStatus.COMPLETED);

        List<Task> unblocked = board.getUnblockedTasks();
        assertTrue(unblocked.isEmpty());
    }

    @Test
    void getUnblockedTasksAfterCompletingBlockerShouldIncludeUnblockedTask() {
        Task a = board.create("A", "");
        Task b = board.create("B", "");
        board.addDependency(b.id(), a.id());

        // Complete A → B should now appear in unblocked
        board.update(a.id(), TaskStatus.COMPLETED);
        List<Task> unblocked = board.getUnblockedTasks();
        assertEquals(1, unblocked.size());
        assertEquals(b.id(), unblocked.get(0).id());
    }

    @Test
    void getUnblockedTasksOnEmptyBoardShouldReturnEmpty() {
        assertTrue(board.getUnblockedTasks().isEmpty());
    }

    // ==================== DIAMOND DEPENDENCIES ====================

    @Test
    void diamondDependencyShouldWorkCorrectly() {
        // A → B, A → C, B → D, C → D (diamond)
        Task a = board.create("A", "");
        Task b = board.create("B", "");
        Task c = board.create("C", "");
        Task d = board.create("D", "");

        board.addDependency(b.id(), a.id());
        board.addDependency(c.id(), a.id());
        board.addDependency(d.id(), b.id());
        board.addDependency(d.id(), c.id());

        // Only A is unblocked
        assertEquals(1, board.getUnblockedTasks().size());

        // Complete A → B and C become unblocked
        board.update(a.id(), TaskStatus.COMPLETED);
        List<Task> unblocked = board.getUnblockedTasks();
        assertEquals(2, unblocked.size());

        // Complete B → D still blocked by C
        board.update(b.id(), TaskStatus.IN_PROGRESS);
        board.update(b.id(), TaskStatus.COMPLETED);
        unblocked = board.getUnblockedTasks();
        assertEquals(1, unblocked.size());
        assertEquals(c.id(), unblocked.get(0).id());

        // Complete C → D becomes unblocked
        board.update(c.id(), TaskStatus.IN_PROGRESS);
        board.update(c.id(), TaskStatus.COMPLETED);
        unblocked = board.getUnblockedTasks();
        assertEquals(1, unblocked.size());
        assertEquals(d.id(), unblocked.get(0).id());
    }

    // ==================== CONCURRENCY ====================

    @Test
    void concurrentCreateShouldNotLoseTasks() throws Exception {
        int threadCount = 10;
        int tasksPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadIdx = t;
            executor.submit(
                    () -> {
                        try {
                            for (int i = 0; i < tasksPerThread; i++) {
                                board.create("Task-" + threadIdx + "-" + i, "desc");
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(threadCount * tasksPerThread, board.list().size());
    }
}
