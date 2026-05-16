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
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.core.task.FileTaskStore;
import io.kairo.core.task.TaskEntry;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import reactor.core.publisher.Mono;

/** Lists all tasks with summary view, sorted unblocked-first. */
@Tool(
        name = "task_list",
        description =
                "List all tasks with summary view. Shows ID, subject, status, owner,"
                        + " and unresolved blockers. Sorted: unblocked tasks first, then blocked,"
                        + " by ID order within each group.",
        category = ToolCategory.AGENT_AND_TASK,
        sideEffect = ToolSideEffect.READ_ONLY)
public class TaskListTool implements SyncTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path overrideRoot;

    public TaskListTool() {
        this(null);
    }

    TaskListTool(Path overrideRoot) {
        this.overrideRoot = overrideRoot;
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        Path root = overrideRoot != null ? overrideRoot : ctx.workspace().root();
        return Mono.fromCallable(() -> doExecute(root));
    }

    private ToolResult doExecute(Path workspaceRoot) {
        FileTaskStore store = FileTaskStore.forWorkspace(workspaceRoot);
        List<TaskEntry> all = store.listAll();

        if (all.isEmpty()) {
            return ToolResult.success("task_list", "No tasks.", Map.of("count", 0));
        }

        Map<String, TaskEntry> taskMap = new LinkedHashMap<>();
        for (TaskEntry t : all) {
            taskMap.put(t.id(), t);
        }

        List<TaskEntry> sorted = new ArrayList<>(all);
        sorted.sort(
                (a, b) -> {
                    boolean aBlocked = !a.unresolvedBlockers(taskMap).isEmpty();
                    boolean bBlocked = !b.unresolvedBlockers(taskMap).isEmpty();
                    if (aBlocked != bBlocked) {
                        return aBlocked ? 1 : -1;
                    }
                    return Integer.compare(parseIdSafe(a.id()), parseIdSafe(b.id()));
                });

        return ToolResult.success(
                "task_list", formatTaskList(sorted, taskMap), Map.of("count", all.size()));
    }

    private String formatTaskList(List<TaskEntry> sorted, Map<String, TaskEntry> allTasks) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (TaskEntry task : sorted) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", task.id());
            item.put("subject", task.subject());
            item.put("status", task.status().toJson());
            item.put("owner", task.owner());
            Set<String> unresolved = task.unresolvedBlockers(allTasks);
            if (!unresolved.isEmpty()) {
                item.put("blockedBy", unresolved);
            }
            items.add(item);
        }
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(items);
        } catch (JsonProcessingException e) {
            return sorted.size() + " task(s)";
        }
    }

    private static int parseIdSafe(String id) {
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
