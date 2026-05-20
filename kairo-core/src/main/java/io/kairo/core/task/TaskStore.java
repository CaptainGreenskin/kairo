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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Storage abstraction for task entries with dependency tracking.
 *
 * <p>Implementations must be thread-safe — multiple tool invocations may access the store
 * concurrently.
 */
public interface TaskStore {

    /**
     * Create a new task with auto-generated ID. The task starts in {@link TaskStatus#PENDING}
     * status.
     *
     * @return the created task entry with assigned ID
     */
    TaskEntry create(
            String subject,
            @Nullable String description,
            @Nullable String activeForm,
            @Nullable Map<String, Object> metadata);

    /** Get a task by ID. Returns empty if the task does not exist. */
    Optional<TaskEntry> get(String taskId);

    /** List all tasks in insertion order. */
    List<TaskEntry> listAll();

    /** Replace a task entry (the entry's ID must already exist). */
    void update(TaskEntry entry);

    /** Delete a task and cascade-remove its ID from other tasks' blocks/blockedBy sets. */
    void delete(String taskId);

    /**
     * Add a bidirectional dependency: {@code fromId} blocks {@code toId}.
     *
     * <p>Updates {@code from.blocks += toId} and {@code to.blockedBy += fromId}.
     */
    void addDependency(String fromId, String toId);

    /**
     * Called when a task is completed. Removes the completed task's ID from all other tasks'
     * blockedBy sets.
     */
    void cascadeCompletion(String completedTaskId);

    /** Count tasks that are not yet completed (PENDING or IN_PROGRESS). */
    int pendingOrInProgressCount();
}
