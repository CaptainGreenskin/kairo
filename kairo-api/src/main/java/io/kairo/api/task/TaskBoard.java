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
package io.kairo.api.task;

import java.util.List;

/**
 * Manages the task board: create, update, query, and track dependencies.
 *
 * @deprecated since v0.10 — superseded by the Expert Team {@code TeamCoordinator} SPI. Scheduled
 *     for deletion in v0.11 along with {@link Task}, {@link TaskStatus}, and {@link Plan}.
 */
@Deprecated(since = "0.10", forRemoval = true)
public interface TaskBoard {

    /**
     * Create a new task.
     *
     * @param subject the task subject/title
     * @param description the task description
     * @return the created task
     */
    Task create(String subject, String description);

    /**
     * Update a task's status.
     *
     * @param taskId the task ID
     * @param status the new status
     * @return the updated task
     */
    Task update(String taskId, TaskStatus status);

    /**
     * Get a task by ID.
     *
     * @param taskId the task ID
     * @return the task
     */
    Task get(String taskId);

    /**
     * List all tasks.
     *
     * @return all tasks
     */
    List<Task> list();

    /**
     * Get all tasks that have no unresolved dependencies and are ready to execute.
     *
     * @return the unblocked tasks
     */
    List<Task> getUnblockedTasks();

    /**
     * Add a dependency between two tasks.
     *
     * @param taskId the task that is blocked
     * @param blockedByTaskId the task that must complete first
     */
    void addDependency(String taskId, String blockedByTaskId);
}
