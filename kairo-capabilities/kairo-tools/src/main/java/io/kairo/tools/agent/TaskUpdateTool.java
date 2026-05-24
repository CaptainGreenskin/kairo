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
import io.kairo.api.hook.HookChain;
import io.kairo.api.hook.HookPhase;
import io.kairo.api.hook.TaskCompletedEvent;
import io.kairo.api.tool.SyncTool;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.core.task.FileTaskStore;
import io.kairo.core.task.TaskEntry;
import io.kairo.core.task.TaskStatus;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import reactor.core.publisher.Mono;

/**
 * Updates an existing task. Supports status changes, field updates, dependency management, and
 * deletion.
 */
@Tool(
        name = "task_update",
        description =
                "Update an existing task. Can change status, subject, description, owner,"
                        + " activeForm, metadata, and add dependency relationships."
                        + " Set status to 'deleted' to permanently remove the task."
                        + " Use addBlocks/addBlockedBy to establish dependencies between tasks.",
        category = ToolCategory.AGENT_AND_TASK,
        sideEffect = ToolSideEffect.WRITE)
public class TaskUpdateTool implements SyncTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path overrideRoot;

    public TaskUpdateTool() {
        this(null);
    }

    TaskUpdateTool(Path overrideRoot) {
        this.overrideRoot = overrideRoot;
    }

    @ToolParam(description = "The ID of the task to update", required = true)
    private String taskId;

    @ToolParam(
            description =
                    "New status: pending, in_progress, completed, or deleted (deleted removes the"
                            + " task)")
    private String status;

    @ToolParam(description = "New subject/title for the task")
    private String subject;

    @ToolParam(description = "New description for the task")
    private String description;

    @ToolParam(description = "New owner for the task (agent name)")
    private String owner;

    @ToolParam(
            description =
                    "Present continuous form shown in spinner when in_progress"
                            + " (e.g. 'Running tests')")
    private String activeForm;

    @ToolParam(
            description = "Metadata keys to merge as JSON object. Set a key to null to delete it.")
    private String metadata;

    @ToolParam(description = "Task IDs that this task blocks (JSON array of strings)")
    private String addBlocks;

    @ToolParam(description = "Task IDs that block this task (JSON array of strings)")
    private String addBlockedBy;

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        Path root = overrideRoot != null ? overrideRoot : ctx.workspace().root();
        return Mono.fromCallable(() -> doExecute(args, root, ctx));
    }

    private ToolResult doExecute(Map<String, Object> args, Path workspaceRoot, ToolContext ctx) {
        String id = asString(args.get("taskId"));
        if (id == null || id.isBlank()) {
            return ToolResult.error("task_update", "Parameter 'taskId' is required");
        }

        FileTaskStore store = FileTaskStore.forWorkspace(workspaceRoot);

        String statusStr = asString(args.get("status"));
        if ("deleted".equalsIgnoreCase(statusStr)) {
            if (store.get(id).isEmpty()) {
                return ToolResult.error("task_update", "Task '" + id + "' not found");
            }
            store.delete(id);
            return ToolResult.success(
                    "task_update",
                    "Task '" + id + "' deleted",
                    Map.of("taskId", id, "status", "deleted"));
        }

        Optional<TaskEntry> existing = store.get(id);
        if (existing.isEmpty()) {
            return ToolResult.error("task_update", "Task '" + id + "' not found");
        }

        TaskEntry task = existing.get();
        TaskStatus previousStatus = task.status();

        if (statusStr != null) {
            task = task.withStatus(TaskStatus.fromString(statusStr));
        }
        if (args.containsKey("subject")) {
            String val = asString(args.get("subject"));
            if (val != null) task = task.withSubject(val);
        }
        if (args.containsKey("description")) {
            task = task.withDescription(asString(args.get("description")));
        }
        if (args.containsKey("owner")) {
            task = task.withOwner(asString(args.get("owner")));
        }
        if (args.containsKey("activeForm")) {
            task = task.withActiveForm(asString(args.get("activeForm")));
        }
        if (args.containsKey("metadata")) {
            Map<String, Object> patch = parseMetadata(args.get("metadata"));
            if (patch != null) {
                task = task.withMergedMetadata(patch);
            }
        }

        store.update(task);

        applyDependencyChanges(store, id, args);

        if (task.status() == TaskStatus.COMPLETED && previousStatus != TaskStatus.COMPLETED) {
            store.cascadeCompletion(id);
            fireTaskCompleted(ctx, task);
        }

        return ToolResult.success(
                "task_update",
                "Task '" + id + "' updated",
                Map.of("taskId", id, "status", task.status().toJson()));
    }

    private void applyDependencyChanges(
            FileTaskStore store, String taskId, Map<String, Object> args) {
        List<String> blocksList = parseStringList(args.get("addBlocks"));
        List<String> blockedByList = parseStringList(args.get("addBlockedBy"));

        for (String blockedId : blocksList) {
            store.addDependency(taskId, blockedId);
        }
        for (String blockerId : blockedByList) {
            store.addDependency(blockerId, taskId);
        }
    }

    private void fireTaskCompleted(ToolContext ctx, TaskEntry task) {
        ctx.getBean(HookChain.class)
                .ifPresent(
                        hookChain ->
                                hookChain
                                        .firePhase(
                                                HookPhase.TASK_COMPLETED,
                                                new TaskCompletedEvent(
                                                        ctx.sessionId(), task.id(), task.subject()))
                                        .subscribe());
    }

    @SuppressWarnings("unchecked")
    private List<String> parseStringList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String s) result.add(s);
                else if (item != null) result.add(item.toString());
            }
            return result;
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return MAPPER.readValue(s, List.class);
            } catch (JsonProcessingException e) {
                return List.of();
            }
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Map<?, ?> m) return (Map<String, Object>) m;
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return MAPPER.readValue(s, Map.class);
            } catch (JsonProcessingException e) {
                return null;
            }
        }
        return null;
    }

    private static String asString(Object val) {
        if (val == null) return null;
        if (val instanceof String s) return s;
        return val.toString();
    }
}
