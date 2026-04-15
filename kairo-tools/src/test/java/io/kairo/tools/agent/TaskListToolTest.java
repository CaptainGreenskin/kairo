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

import io.kairo.api.tool.ToolResult;
import io.kairo.multiagent.task.DefaultTaskBoard;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskListToolTest {

    private DefaultTaskBoard taskBoard;
    private TaskListTool tool;

    @BeforeEach
    void setUp() {
        taskBoard = new DefaultTaskBoard();
        tool = new TaskListTool(taskBoard);
    }

    @Test
    void listEmptyBoard() {
        ToolResult result = tool.execute(Map.of());
        assertFalse(result.isError());
        assertTrue(result.content().contains("No tasks"));
        assertEquals(0, result.metadata().get("count"));
    }

    @Test
    void listWithTasks() {
        taskBoard.create("Task A", "desc A");
        taskBoard.create("Task B", "desc B");

        ToolResult result = tool.execute(Map.of());
        assertFalse(result.isError());
        assertTrue(result.content().contains("Task A"));
        assertTrue(result.content().contains("Task B"));
        assertEquals(2, result.metadata().get("count"));
    }

    @Test
    void listShowsStatus() {
        var task = taskBoard.create("Work", "desc");
        taskBoard.update(task.id(), io.kairo.api.task.TaskStatus.IN_PROGRESS);

        ToolResult result = tool.execute(Map.of());
        assertTrue(result.content().contains("IN_PROGRESS"));
    }

    @Test
    void listShowsOwnerInfo() {
        var task = taskBoard.create("Work", "desc");
        task.setOwner("agent-1");

        ToolResult result = tool.execute(Map.of());
        assertTrue(result.content().contains("agent-1"));
    }

    @Test
    void listShowsUnassignedForNoOwner() {
        taskBoard.create("Work", "desc");

        ToolResult result = tool.execute(Map.of());
        assertTrue(result.content().contains("unassigned"));
    }

    @Test
    void listShowsDependencyInfo() {
        var a = taskBoard.create("A", "");
        var b = taskBoard.create("B", "");
        taskBoard.addDependency(b.id(), a.id());

        ToolResult result = tool.execute(Map.of());
        String content = result.content();
        // Task B should show blockedBy info
        assertTrue(content.contains(a.id()));
    }
}
