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
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolHandler;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import java.util.Map;

/** Retrieves full details of a specific task by ID. */
@Tool(
        name = "task_get",
        description = "Get full details of a specific task.",
        category = ToolCategory.AGENT_AND_TASK)
public class TaskGetTool implements ToolHandler {

    @ToolParam(description = "The ID of the task", required = true)
    private String taskId;

    private final TaskBoard taskBoard;

    /**
     * Create a new TaskGetTool.
     *
     * @param taskBoard the task board to retrieve tasks from
     */
    public TaskGetTool(TaskBoard taskBoard) {
        this.taskBoard = taskBoard;
    }

    @Override
    public ToolResult execute(Map<String, Object> input) {
        String taskId = (String) input.get("taskId");
        if (taskId == null || taskId.isBlank()) {
            return new ToolResult(null, "Parameter 'taskId' is required", true, Map.of());
        }

        Task task = taskBoard.get(taskId);
        if (task == null) {
            return new ToolResult(null, "Task not found: " + taskId, true, Map.of());
        }

        String detail =
                String.format(
                        "Task #%s\nSubject: %s\nStatus: %s\nOwner: %s\nDescription: %s\nBlockedBy: %s\nBlocks: %s",
                        task.id(),
                        task.subject(),
                        task.status(),
                        task.owner() != null ? task.owner() : "unassigned",
                        task.description(),
                        task.blockedBy().isEmpty() ? "none" : task.blockedBy(),
                        task.blocks().isEmpty() ? "none" : task.blocks());
        return new ToolResult(null, detail, false, Map.of());
    }
}
