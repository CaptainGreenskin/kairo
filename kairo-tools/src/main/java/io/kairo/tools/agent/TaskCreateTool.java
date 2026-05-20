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
import io.kairo.api.hook.TaskCreatedEvent;
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
import reactor.core.publisher.Mono;

/** Creates a new task with auto-generated ID. */
@Tool(
        name = "task_create",
        description =
                "Create a new task with auto-generated ID. Returns the created task."
                        + " Tasks start in 'pending' status with no owner."
                        + " Use task_update to set dependencies (addBlocks/addBlockedBy) or change status.",
        category = ToolCategory.AGENT_AND_TASK,
        sideEffect = ToolSideEffect.WRITE)
public class TaskCreateTool implements SyncTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path overrideRoot;

    public TaskCreateTool() {
        this(null);
    }

    TaskCreateTool(Path overrideRoot) {
        this.overrideRoot = overrideRoot;
    }

    @ToolParam(description = "Brief, actionable title for the task", required = true)
    private String subject;

    @ToolParam(description = "Detailed description of what needs to be done")
    private String description;

    @ToolParam(
            description =
                    "Present continuous form shown in spinner when task is in_progress"
                            + " (e.g. 'Fixing authentication bug')")
    private String activeForm;

    @ToolParam(description = "Arbitrary metadata as JSON object string")
    private String metadata;

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        Path root = overrideRoot != null ? overrideRoot : ctx.workspace().root();
        return Mono.fromCallable(() -> doExecute(args, root, ctx));
    }

    private ToolResult doExecute(Map<String, Object> args, Path workspaceRoot, ToolContext ctx) {
        String subjectVal = asString(args.get("subject"));
        if (subjectVal == null || subjectVal.isBlank()) {
            return ToolResult.error("task_create", "Parameter 'subject' is required");
        }

        String descriptionVal = asString(args.get("description"));
        String activeFormVal = asString(args.get("activeForm"));
        Map<String, Object> metadataVal = parseMetadata(args.get("metadata"));

        FileTaskStore store = FileTaskStore.forWorkspace(workspaceRoot);
        TaskEntry created = store.create(subjectVal, descriptionVal, activeFormVal, metadataVal);

        fireTaskCreated(ctx, created);

        return ToolResult.success(
                "task_create", formatTask(created), Map.of("taskId", created.id()));
    }

    private void fireTaskCreated(ToolContext ctx, TaskEntry task) {
        ctx.getBean(HookChain.class)
                .ifPresent(
                        hookChain ->
                                hookChain
                                        .firePhase(
                                                HookPhase.TASK_CREATED,
                                                new TaskCreatedEvent(
                                                        ctx.sessionId(),
                                                        task.id(),
                                                        task.subject(),
                                                        task.description()))
                                        .subscribe());
    }

    private String formatTask(TaskEntry task) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", task.id());
        map.put("subject", task.subject());
        map.put("description", task.description());
        map.put("status", task.status().toJson());
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "Task #" + task.id() + " created: " + task.subject();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
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
