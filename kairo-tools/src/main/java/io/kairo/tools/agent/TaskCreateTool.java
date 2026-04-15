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
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.tool.ToolHandler;
import java.util.Map;

/**
 * Creates a new task on the task board for tracking work items.
 *
 * <p>Used by agents to decompose complex work into trackable units with subject, description, and
 * automatic status tracking.
 */
@Tool(
        name = "task_create",
        description = "Create a new task on the task board. Use to track work items.",
        category = ToolCategory.AGENT_AND_TASK)
public class TaskCreateTool implements ToolHandler {

    @ToolParam(description = "Brief title for the task", required = true)
    private String subject;

    @ToolParam(description = "Detailed description of what needs to be done", required = true)
    private String description;

    private final TaskBoard taskBoard;

    /**
     * Create a new TaskCreateTool.
     *
     * @param taskBoard the task board to create tasks on
     */
    public TaskCreateTool(TaskBoard taskBoard) {
        this.taskBoard = taskBoard;
    }

    @Override
    public ToolResult execute(Map<String, Object> input) {
        String subject = (String) input.get("subject");
        String description = (String) input.get("description");

        if (subject == null || subject.isBlank()) {
            return new ToolResult(null, "Parameter 'subject' is required", true, Map.of());
        }
        if (description == null) {
            description = "";
        }

        Task task = taskBoard.create(subject, description);
        String result =
                String.format(
                        "Created task #%s: %s (status: %s)",
                        task.id(), task.subject(), task.status());
        return new ToolResult(null, result, false, Map.of("taskId", task.id()));
    }
}
