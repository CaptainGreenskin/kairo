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

import io.kairo.api.task.Task;
import io.kairo.api.task.TaskBoard;
import io.kairo.api.task.TaskStatus;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default in-memory implementation of {@link TaskBoard} with dependency tracking.
 *
 * <p>Supports DAG-based task scheduling: when a task is completed, all downstream tasks that were
 * blocked by it are automatically unblocked.
 *
 * <p>Thread-safe via {@link ConcurrentHashMap} for concurrent agent access.
 */
public class DefaultTaskBoard implements TaskBoard {

    private static final Logger log = LoggerFactory.getLogger(DefaultTaskBoard.class);

    private final ConcurrentHashMap<String, Task> tasks = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(0);

    @Override
    public Task create(String subject, String description) {
        String id = String.valueOf(idCounter.incrementAndGet());
        Task task =
                Task.builder()
                        .id(id)
                        .subject(subject)
                        .description(description)
                        .status(TaskStatus.PENDING)
                        .build();
        tasks.put(id, task);
        log.debug("Created task #{}: {}", id, subject);
        return task;
    }

    @Override
    public Task update(String taskId, TaskStatus status) {
        Task task = tasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        TaskStatus oldStatus = task.status();
        task.setStatus(status);
        log.debug("Updated task #{} from {} to {}", taskId, oldStatus, status);

        // Auto-resolve downstream dependencies when a task completes
        if (status == TaskStatus.COMPLETED) {
            resolveDownstreamDependencies(taskId);
        }
        return task;
    }

    @Override
    public Task get(String taskId) {
        return tasks.get(taskId);
    }

    @Override
    public List<Task> list() {
        return List.copyOf(tasks.values());
    }

    @Override
    public List<Task> getUnblockedTasks() {
        return tasks.values().stream()
                .filter(t -> t.status() == TaskStatus.PENDING)
                .filter(
                        t ->
                                t.blockedBy().isEmpty()
                                        || t.blockedBy().stream()
                                                .allMatch(
                                                        id -> {
                                                            Task blocker = tasks.get(id);
                                                            return blocker != null
                                                                    && blocker.status()
                                                                            == TaskStatus.COMPLETED;
                                                        }))
                .toList();
    }

    @Override
    public void addDependency(String taskId, String blockedByTaskId) {
        Task task = tasks.get(taskId);
        Task blocker = tasks.get(blockedByTaskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        if (blocker == null) {
            throw new IllegalArgumentException("Blocker task not found: " + blockedByTaskId);
        }
        task.addBlockedBy(blockedByTaskId);
        blocker.addBlocks(taskId);
        log.debug("Added dependency: task #{} blocked by #{}", taskId, blockedByTaskId);
    }

    /**
     * When a task completes, remove it from all downstream tasks' blockedBy sets.
     *
     * @param completedTaskId the ID of the completed task
     */
    private void resolveDownstreamDependencies(String completedTaskId) {
        tasks.values().forEach(t -> t.removeBlockedBy(completedTaskId));
        log.debug("Resolved downstream dependencies for completed task #{}", completedTaskId);
    }
}
