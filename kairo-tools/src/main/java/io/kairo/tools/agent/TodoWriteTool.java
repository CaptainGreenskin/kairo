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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Writes (replaces) the agent's todo list stored at {@code .kairo/todos.json} in the workspace
 * root.
 *
 * <p>The entire list is replaced on each call. Use {@link TodoReadTool} to read the current list.
 */
@Tool(
        name = "todo_write",
        description =
                "Create or replace the todo list. Replaces the entire list with the provided todos."
                        + " The 'todos' parameter accepts EITHER a JSON array directly, OR a string containing"
                        + " a JSON array. Each item must be an object with these fields:"
                        + " id (string), content (string),"
                        + " status (one of: pending, in_progress, completed),"
                        + " priority (one of: high, medium, low)."
                        + " Example: [{\"id\":\"1\",\"content\":\"Read pom.xml\",\"status\":\"pending\",\"priority\":\"high\"},"
                        + "{\"id\":\"2\",\"content\":\"Remove empty controller module\",\"status\":\"pending\",\"priority\":\"high\"}]."
                        + " After writing todos, IMMEDIATELY continue executing them by calling other tools —"
                        + " do NOT stop your turn after todo_write; the user expects you to start working"
                        + " on the first todo right away in the same turn.",
        category = ToolCategory.AGENT_AND_TASK,
        sideEffect = ToolSideEffect.WRITE)
public class TodoWriteTool implements SyncTool {

    static final String TODO_FILE = ".kairo/todos.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> VALID_STATUSES =
            List.of("pending", "in_progress", "completed");
    private static final List<String> VALID_PRIORITIES = List.of("high", "medium", "low");

    private final Path overrideRoot;

    /** Default constructor — uses workspace or JVM cwd. */
    public TodoWriteTool() {
        this(null);
    }

    /** Constructor for testing with an explicit workspace root. */
    TodoWriteTool(Path overrideRoot) {
        this.overrideRoot = overrideRoot;
    }

    @ToolParam(
            description =
                    "JSON array of todo items (pass either the array directly or a stringified JSON array)."
                            + " Each item must have: id (string), content (string),"
                            + " status (pending|in_progress|completed), priority (high|medium|low).",
            required = true)
    private String todos;

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        Path root = overrideRoot != null ? overrideRoot : ctx.workspace().root();
        return Mono.fromCallable(() -> doExecute(args, root));
    }

    private ToolResult doExecute(Map<String, Object> input, Path workspaceRoot) {
        // Accept both shapes: {todos: [...]} (LLM-friendly) and {todos: "[...]"} (String param).
        // Models routinely ignore "stringified" instructions and pass the array directly, so we
        // coerce both back into a List<?> via Jackson before validating.
        Object rawTodos = input.get("todos");
        if (rawTodos == null) {
            return error(
                    "Parameter 'todos' is required. Provide a JSON array of todo items, e.g.:"
                            + " [{\"id\":\"1\",\"content\":\"...\",\"status\":\"pending\",\"priority\":\"high\"}]");
        }

        List<?> todoList;
        try {
            if (rawTodos instanceof List<?> list) {
                todoList = list;
            } else if (rawTodos instanceof String s) {
                if (s.isBlank()) {
                    return error(
                            "Parameter 'todos' is empty. Provide at least one todo item, or pass []"
                                    + " explicitly to clear the list.");
                }
                todoList = MAPPER.readValue(s, List.class);
            } else {
                return error(
                        "Parameter 'todos' must be a JSON array (or stringified JSON array), got: "
                                + rawTodos.getClass().getSimpleName());
            }
        } catch (JsonProcessingException e) {
            return error("Invalid JSON in 'todos': " + e.getOriginalMessage());
        }

        for (Object item : todoList) {
            if (!(item instanceof Map<?, ?> m)) {
                return error("Each todo must be a JSON object");
            }
            String status = (String) m.get("status");
            String priority = (String) m.get("priority");
            if (status != null && !VALID_STATUSES.contains(status)) {
                return error("Invalid status '" + status + "'; must be one of " + VALID_STATUSES);
            }
            if (priority != null && !VALID_PRIORITIES.contains(priority)) {
                return error(
                        "Invalid priority '" + priority + "'; must be one of " + VALID_PRIORITIES);
            }
        }

        Path todoFile = workspaceRoot.resolve(TODO_FILE);
        try {
            Files.createDirectories(todoFile.getParent());
            String formatted = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(todoList);
            Files.writeString(todoFile, formatted, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return error("Failed to write todos: " + e.getMessage());
        }

        return ToolResult.success(
                "todo_write",
                "Wrote " + todoList.size() + " todo(s)",
                Map.of("count", todoList.size(), "file", TODO_FILE));
    }

    private ToolResult error(String msg) {
        return ToolResult.error("todo_write", msg);
    }
}
