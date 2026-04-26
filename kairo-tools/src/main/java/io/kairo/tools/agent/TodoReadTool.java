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

import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolHandler;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.api.workspace.Workspace;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Reads the agent's todo list from {@code .kairo/todos.json} in the workspace root.
 *
 * <p>Returns an empty JSON array ({@code []}) when no todo file exists yet.
 */
@Tool(
        name = "todo_read",
        description =
                "Read the current todo list from .kairo/todos.json. Returns an empty list if no"
                        + " todos have been written yet.",
        category = ToolCategory.AGENT_AND_TASK,
        sideEffect = ToolSideEffect.READ_ONLY)
public class TodoReadTool implements ToolHandler {

    private final Path overrideRoot;

    /** Default constructor — uses workspace or JVM cwd. */
    public TodoReadTool() {
        this(null);
    }

    /** Constructor for testing with an explicit workspace root. */
    TodoReadTool(Path overrideRoot) {
        this.overrideRoot = overrideRoot;
    }

    @Override
    public ToolResult execute(Map<String, Object> input) {
        Path root = overrideRoot != null ? overrideRoot : Workspace.cwd().root();
        return doExecute(root);
    }

    @Override
    public ToolResult execute(Map<String, Object> input, ToolContext context) {
        Path root = overrideRoot != null ? overrideRoot : context.workspace().root();
        return doExecute(root);
    }

    private ToolResult doExecute(Path workspaceRoot) {
        Path todoFile = workspaceRoot.resolve(TodoWriteTool.TODO_FILE);
        if (!Files.exists(todoFile)) {
            return new ToolResult("todo_read", "[]", false, Map.of("count", 0));
        }
        try {
            String content = Files.readString(todoFile, StandardCharsets.UTF_8);
            return new ToolResult(
                    "todo_read", content.trim(), false, Map.of("file", TodoWriteTool.TODO_FILE));
        } catch (IOException e) {
            return new ToolResult(
                    "todo_read", "Failed to read todos: " + e.getMessage(), true, Map.of());
        }
    }
}
