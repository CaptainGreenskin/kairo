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

import io.kairo.api.task.Task;
import io.kairo.api.task.TaskBoard;
import io.kairo.api.task.TaskStatus;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolHandler;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import java.util.Map;

/**
 * Updates the status of a task on the task board.
 *
 * <p>Supports all lifecycle transitions: pending, in_progress, completed, failed, cancelled. When a
 * task is marked as completed, downstream dependencies are automatically resolved.
 */
@Tool(
        name = "task_update",
        description = "Update the status of a task.",
        category = ToolCategory.AGENT_AND_TASK)
public class TaskUpdateTool implements ToolHandler {

    @ToolParam(description = "The ID of the task to update", required = true)
    private String taskId;

    @ToolParam(
            description = "New status: pending, in_progress, completed, failed, cancelled",
            required = true)
    private String status;

    private final TaskBoard taskBoard;

    /**
     * Create a new TaskUpdateTool.
     *
     * @param taskBoard the task board to update tasks on
     */
    public TaskUpdateTool(TaskBoard taskBoard) {
        this.taskBoard = taskBoard;
    }

    @Override
    public ToolResult execute(Map<String, Object> input) {
        String taskId = (String) input.get("taskId");
        String statusStr = (String) input.get("status");

        if (taskId == null || taskId.isBlank()) {
            return new ToolResult(null, "Parameter 'taskId' is required", true, Map.of());
        }
        if (statusStr == null || statusStr.isBlank()) {
            return new ToolResult(null, "Parameter 'status' is required", true, Map.of());
        }

        try {
            TaskStatus status = TaskStatus.valueOf(statusStr.toUpperCase());
            Task task = taskBoard.update(taskId, status);
            return new ToolResult(
                    null, String.format("Updated task #%s to %s", taskId, status), false, Map.of());
        } catch (IllegalArgumentException e) {
            return new ToolResult(null, "Error: " + e.getMessage(), true, Map.of());
        }
    }
}
