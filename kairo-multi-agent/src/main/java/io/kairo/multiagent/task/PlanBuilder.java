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

import io.kairo.api.task.Plan;
import io.kairo.api.task.Task;
import io.kairo.api.task.TaskBoard;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fluent builder for constructing a {@link Plan} with associated tasks.
 *
 * <p>Tasks are created on the provided {@link TaskBoard} during building, ensuring they are
 * immediately tracked and schedulable.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * Plan plan = PlanBuilder.create(taskBoard)
 *     .name("Refactor module X")
 *     .overview("Extract interfaces, implement adapters, add tests")
 *     .addTask("Extract interface", "Create interface from existing class")
 *     .addTask("Implement adapter", "Wire new interface", "1")
 *     .build();
 * }</pre>
 */
public class PlanBuilder {

    private String name;
    private String overview;
    private final List<Task> tasks = new ArrayList<>();
    private final TaskBoard taskBoard;

    private PlanBuilder(TaskBoard taskBoard) {
        this.taskBoard = taskBoard;
    }

    /**
     * Create a new PlanBuilder backed by the given task board.
     *
     * @param taskBoard the task board to create tasks on
     * @return a new PlanBuilder
     */
    public static PlanBuilder create(TaskBoard taskBoard) {
        return new PlanBuilder(taskBoard);
    }

    /**
     * Set the plan name.
     *
     * @param name the plan name
     * @return this builder
     */
    public PlanBuilder name(String name) {
        this.name = name;
        return this;
    }

    /**
     * Set the plan overview description.
     *
     * @param overview the plan overview
     * @return this builder
     */
    public PlanBuilder overview(String overview) {
        this.overview = overview;
        return this;
    }

    /**
     * Add a task with no dependencies.
     *
     * @param subject the task subject
     * @param description the task description
     * @return this builder
     */
    public PlanBuilder addTask(String subject, String description) {
        Task task = taskBoard.create(subject, description);
        tasks.add(task);
        return this;
    }

    /**
     * Add a task with dependencies on other tasks.
     *
     * @param subject the task subject
     * @param description the task description
     * @param blockedByIds IDs of tasks that must complete before this one
     * @return this builder
     */
    public PlanBuilder addTask(String subject, String description, String... blockedByIds) {
        Task task = taskBoard.create(subject, description);
        for (String blockedById : blockedByIds) {
            taskBoard.addDependency(task.id(), blockedById);
        }
        tasks.add(task);
        return this;
    }

    /**
     * Build the plan.
     *
     * @return the constructed Plan
     */
    public Plan build() {
        return new Plan(name, overview, Collections.unmodifiableList(new ArrayList<>(tasks)));
    }
}
