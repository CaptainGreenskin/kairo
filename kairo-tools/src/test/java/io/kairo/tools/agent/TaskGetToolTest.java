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
package io.kairo.tools.agent;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.task.Task;
import io.kairo.api.tool.ToolResult;
import io.kairo.multiagent.task.DefaultTaskBoard;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskGetToolTest {

    private DefaultTaskBoard taskBoard;
    private TaskGetTool tool;

    @BeforeEach
    void setUp() {
        taskBoard = new DefaultTaskBoard();
        tool = new TaskGetTool(taskBoard);
    }

    @Test
    void missingTaskIdParameter() {
        ToolResult result = tool.execute(Map.of());
        assertTrue(result.isError());
        assertTrue(result.content().contains("'taskId' is required"));
    }

    @Test
    void blankTaskIdParameter() {
        ToolResult result = tool.execute(Map.of("taskId", "   "));
        assertTrue(result.isError());
        assertTrue(result.content().contains("'taskId' is required"));
    }

    @Test
    void taskNotFound() {
        ToolResult result = tool.execute(Map.of("taskId", "nonexistent"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("Task not found"));
        assertTrue(result.content().contains("nonexistent"));
    }

    @Test
    void getExistingTask() {
        Task created = taskBoard.create("Fix bug", "NPE in service layer");

        ToolResult result = tool.execute(Map.of("taskId", created.id()));
        assertFalse(result.isError());
        assertTrue(result.content().contains("Fix bug"));
        assertTrue(result.content().contains("NPE in service layer"));
    }

    @Test
    void getTaskShowsStatus() {
        Task created = taskBoard.create("Work item", "desc");
        taskBoard.update(created.id(), io.kairo.api.task.TaskStatus.IN_PROGRESS);

        ToolResult result = tool.execute(Map.of("taskId", created.id()));
        assertFalse(result.isError());
        assertTrue(result.content().contains("IN_PROGRESS"));
    }

    @Test
    void getTaskShowsOwner() {
        Task created = taskBoard.create("Work item", "desc");
        created.setOwner("agent-1");

        ToolResult result = tool.execute(Map.of("taskId", created.id()));
        assertFalse(result.isError());
        assertTrue(result.content().contains("agent-1"));
    }

    @Test
    void getTaskShowsUnassignedWhenNoOwner() {
        Task created = taskBoard.create("Work item", "desc");

        ToolResult result = tool.execute(Map.of("taskId", created.id()));
        assertFalse(result.isError());
        assertTrue(result.content().contains("unassigned"));
    }

    @Test
    void getTaskShowsDependencies() {
        Task a = taskBoard.create("Task A", "");
        Task b = taskBoard.create("Task B", "");
        taskBoard.addDependency(b.id(), a.id());

        ToolResult result = tool.execute(Map.of("taskId", b.id()));
        assertFalse(result.isError());
        assertTrue(result.content().contains(a.id()));
    }

    @Test
    void getTaskShowsNoDependencies() {
        Task created = taskBoard.create("Independent task", "desc");

        ToolResult result = tool.execute(Map.of("taskId", created.id()));
        assertFalse(result.isError());
        assertTrue(result.content().contains("none"));
    }

    @Test
    void getTaskShowsTaskId() {
        Task created = taskBoard.create("My task", "desc");

        ToolResult result = tool.execute(Map.of("taskId", created.id()));
        assertFalse(result.isError());
        assertTrue(result.content().contains(created.id()));
    }

    @Test
    void getTaskDetailFormat() {
        Task created = taskBoard.create("Subject", "Description text");

        ToolResult result = tool.execute(Map.of("taskId", created.id()));
        assertFalse(result.isError());
        assertTrue(result.content().contains("Subject: Subject"));
        assertTrue(result.content().contains("Description: Description text"));
    }
}
