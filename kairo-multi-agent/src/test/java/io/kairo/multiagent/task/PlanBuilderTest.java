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

import io.kairo.api.task.Plan;
import io.kairo.api.task.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlanBuilderTest {

    private DefaultTaskBoard taskBoard;

    @BeforeEach
    void setUp() {
        taskBoard = new DefaultTaskBoard();
    }

    @Test
    void buildPlanWithMultipleTasks() {
        Plan plan =
                PlanBuilder.create(taskBoard)
                        .name("Refactor plan")
                        .overview("Refactor module X")
                        .addTask("Extract interface", "Create interface from class")
                        .addTask("Implement adapter", "Wire new interface")
                        .addTask("Add tests", "Cover new code")
                        .build();

        assertEquals("Refactor plan", plan.name());
        assertEquals("Refactor module X", plan.overview());
        assertEquals(3, plan.tasks().size());
        assertEquals("Extract interface", plan.tasks().get(0).subject());
        assertEquals("Implement adapter", plan.tasks().get(1).subject());
        assertEquals("Add tests", plan.tasks().get(2).subject());
    }

    @Test
    void buildPlanWithDependencies() {
        Plan plan =
                PlanBuilder.create(taskBoard)
                        .name("Dep plan")
                        .overview("Test deps")
                        .addTask("Setup", "Setup environment")
                        .addTask("Build", "Build artifacts", "1")
                        .build();

        assertEquals(2, plan.tasks().size());
        Task buildTask = plan.tasks().get(1);
        // The build task should be blocked by task "1" (the setup task)
        assertTrue(buildTask.blockedBy().contains("1"));
    }

    @Test
    void buildPlanWithMultipleDependencies() {
        Plan plan =
                PlanBuilder.create(taskBoard)
                        .name("Multi-dep")
                        .overview("Multiple deps")
                        .addTask("A", "Task A")
                        .addTask("B", "Task B")
                        .addTask("C", "Task C, depends on A and B", "1", "2")
                        .build();

        assertEquals(3, plan.tasks().size());
        Task taskC = plan.tasks().get(2);
        assertTrue(taskC.blockedBy().contains("1"));
        assertTrue(taskC.blockedBy().contains("2"));
    }

    @Test
    void buildEmptyPlan() {
        Plan plan = PlanBuilder.create(taskBoard).name("Empty").overview("Nothing here").build();

        assertEquals("Empty", plan.name());
        assertEquals("Nothing here", plan.overview());
        assertTrue(plan.tasks().isEmpty());
    }

    @Test
    void tasksCreatedOnTaskBoard() {
        PlanBuilder.create(taskBoard)
                .name("Board test")
                .overview("Verify tasks on board")
                .addTask("Alpha", "First task")
                .addTask("Beta", "Second task")
                .build();

        // Tasks should be on the board
        assertEquals(2, taskBoard.list().size());
    }

    @Test
    void planTasksAreUnmodifiable() {
        Plan plan =
                PlanBuilder.create(taskBoard)
                        .name("Immutable")
                        .overview("Test immutability")
                        .addTask("X", "Task X")
                        .build();

        assertThrows(UnsupportedOperationException.class, () -> plan.tasks().add(null));
    }
}
