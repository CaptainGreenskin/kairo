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
import io.kairo.api.tool.ToolResult;
import io.kairo.core.tool.ToolHandler;
import java.util.List;
import java.util.Map;

/** Lists all tasks on the task board with their status, owner, and dependency info. */
@Tool(
        name = "task_list",
        description = "List all tasks on the task board with their status.",
        category = ToolCategory.AGENT_AND_TASK)
public class TaskListTool implements ToolHandler {

    private final TaskBoard taskBoard;

    /**
     * Create a new TaskListTool.
     *
     * @param taskBoard the task board to list tasks from
     */
    public TaskListTool(TaskBoard taskBoard) {
        this.taskBoard = taskBoard;
    }

    @Override
    public ToolResult execute(Map<String, Object> input) {
        List<Task> tasks = taskBoard.list();
        if (tasks.isEmpty()) {
            return new ToolResult(null, "No tasks on the board.", false, Map.of("count", 0));
        }

        StringBuilder sb = new StringBuilder();
        for (Task t : tasks) {
            sb.append(
                    String.format(
                            "#%s [%s] %s (owner: %s, blockedBy: %s)\n",
                            t.id(),
                            t.status(),
                            t.subject(),
                            t.owner() != null ? t.owner() : "unassigned",
                            t.blockedBy().isEmpty() ? "none" : String.join(",", t.blockedBy())));
        }
        return new ToolResult(null, sb.toString(), false, Map.of("count", tasks.size()));
    }
}
