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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.tool.SyncTool;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.core.task.FileTaskStore;
import io.kairo.core.task.TaskEntry;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import reactor.core.publisher.Mono;

/** Retrieves full details of a single task by ID. */
@Tool(
        name = "task_get",
        description =
                "Get full details of a task by its ID, including description,"
                        + " dependencies (blocks/blockedBy), owner, and metadata.",
        category = ToolCategory.AGENT_AND_TASK,
        sideEffect = ToolSideEffect.READ_ONLY)
public class TaskGetTool implements SyncTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path overrideRoot;

    public TaskGetTool() {
        this(null);
    }

    TaskGetTool(Path overrideRoot) {
        this.overrideRoot = overrideRoot;
    }

    @ToolParam(description = "The ID of the task to retrieve", required = true)
    private String taskId;

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        Path root = overrideRoot != null ? overrideRoot : ctx.workspace().root();
        return Mono.fromCallable(() -> doExecute(args, root));
    }

    private ToolResult doExecute(Map<String, Object> args, Path workspaceRoot) {
        String id = asString(args.get("taskId"));
        if (id == null || id.isBlank()) {
            return ToolResult.error("task_get", "Parameter 'taskId' is required");
        }

        FileTaskStore store = FileTaskStore.forWorkspace(workspaceRoot);
        Optional<TaskEntry> task = store.get(id);
        if (task.isEmpty()) {
            return ToolResult.error("task_get", "Task '" + id + "' not found");
        }

        return ToolResult.success("task_get", formatTaskDetail(task.get()), Map.of("taskId", id));
    }

    private String formatTaskDetail(TaskEntry task) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", task.id());
        map.put("subject", task.subject());
        map.put("description", task.description());
        map.put("status", task.status().toJson());
        map.put("owner", task.owner());
        map.put("blocks", task.blocks());
        map.put("blockedBy", task.blockedBy());
        map.put("activeForm", task.activeForm());
        map.put("metadata", task.metadata());
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "Task #" + task.id() + ": " + task.subject();
        }
    }

    private static String asString(Object val) {
        if (val == null) return null;
        if (val instanceof String s) return s;
        return val.toString();
    }
}
