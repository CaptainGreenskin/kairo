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

class TaskCreateToolTest {

    private DefaultTaskBoard taskBoard;
    private TaskCreateTool tool;

    @BeforeEach
    void setUp() {
        taskBoard = new DefaultTaskBoard();
        tool = new TaskCreateTool(taskBoard);
    }

    @Test
    void createTaskSuccessfully() {
        ToolResult result =
                tool.execute(Map.of("subject", "Fix bug", "description", "NPE in service"));
        assertFalse(result.isError());
        assertTrue(result.content().contains("Created task"));
        assertTrue(result.content().contains("Fix bug"));
        assertEquals(1, taskBoard.list().size());
    }

    @Test
    void createTaskWithoutDescription() {
        ToolResult result = tool.execute(Map.of("subject", "Quick fix"));
        assertFalse(result.isError());
        assertEquals(1, taskBoard.list().size());
        assertEquals("", taskBoard.list().get(0).description());
    }

    @Test
    void createTaskMissingSubject() {
        ToolResult result = tool.execute(Map.of("description", "some desc"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("'subject' is required"));
    }

    @Test
    void createTaskBlankSubject() {
        ToolResult result = tool.execute(Map.of("subject", "  ", "description", "desc"));
        assertTrue(result.isError());
    }

    @Test
    void createTaskReturnsTaskId() {
        ToolResult result = tool.execute(Map.of("subject", "Task A", "description", "desc"));
        assertFalse(result.isError());
        assertNotNull(result.metadata().get("taskId"));
    }

    @Test
    void createMultipleTasks() {
        tool.execute(Map.of("subject", "A", "description", "a"));
        tool.execute(Map.of("subject", "B", "description", "b"));
        tool.execute(Map.of("subject", "C", "description", "c"));
        assertEquals(3, taskBoard.list().size());
    }
}
