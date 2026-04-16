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
import io.kairo.api.task.TaskStatus;
import io.kairo.api.tool.ToolResult;
import io.kairo.multiagent.task.DefaultTaskBoard;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskUpdateToolTest {

    private DefaultTaskBoard taskBoard;
    private TaskUpdateTool tool;

    @BeforeEach
    void setUp() {
        taskBoard = new DefaultTaskBoard();
        tool = new TaskUpdateTool(taskBoard);
    }

    @Test
    void updateTaskStatusToInProgress() {
        Task task = taskBoard.create("Work", "desc");
        ToolResult result = tool.execute(Map.of("taskId", task.id(), "status", "in_progress"));
        assertFalse(result.isError());
        assertEquals(TaskStatus.IN_PROGRESS, taskBoard.get(task.id()).status());
    }

    @Test
    void updateTaskStatusToCompleted() {
        Task task = taskBoard.create("Work", "desc");
        taskBoard.update(task.id(), TaskStatus.IN_PROGRESS);

        ToolResult result = tool.execute(Map.of("taskId", task.id(), "status", "completed"));
        assertFalse(result.isError());
        assertEquals(TaskStatus.COMPLETED, taskBoard.get(task.id()).status());
    }

    @Test
    void updateWithUpperCaseStatus() {
        Task task = taskBoard.create("Work", "desc");
        ToolResult result = tool.execute(Map.of("taskId", task.id(), "status", "CANCELLED"));
        assertFalse(result.isError());
        assertEquals(TaskStatus.CANCELLED, taskBoard.get(task.id()).status());
    }

    @Test
    void updateNonExistentTask() {
        ToolResult result = tool.execute(Map.of("taskId", "999", "status", "completed"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("Error"));
    }

    @Test
    void updateWithInvalidStatus() {
        Task task = taskBoard.create("Work", "desc");
        ToolResult result = tool.execute(Map.of("taskId", task.id(), "status", "bogus"));
        assertTrue(result.isError());
    }

    @Test
    void updateMissingTaskId() {
        ToolResult result = tool.execute(Map.of("status", "completed"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("'taskId' is required"));
    }

    @Test
    void updateMissingStatus() {
        Task task = taskBoard.create("Work", "desc");
        ToolResult result = tool.execute(Map.of("taskId", task.id()));
        assertTrue(result.isError());
        assertTrue(result.content().contains("'status' is required"));
    }
}
