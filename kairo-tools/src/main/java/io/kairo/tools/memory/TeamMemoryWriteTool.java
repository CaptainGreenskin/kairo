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
package io.kairo.tools.memory;

import io.kairo.api.tool.SyncTool;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.core.memory.structured.MemoryDirectoryManager;
import io.kairo.core.memory.structured.MemoryFile;
import io.kairo.core.memory.structured.MemoryType;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import javax.annotation.Nullable;
import reactor.core.publisher.Mono;

/**
 * Write or update a memory shared across all agents in a team. Stores files at {@code
 * .kairo/teams/{teamName}/memory/}.
 */
@Tool(
        name = "team_memory_write",
        description =
                "Write or update a shared team memory. All agents in the same team can read these"
                        + " memories. Useful for sharing findings, marking explored paths, and"
                        + " recording team decisions.",
        category = ToolCategory.AGENT_AND_TASK,
        sideEffect = ToolSideEffect.WRITE)
public class TeamMemoryWriteTool implements SyncTool {

    @ToolParam(description = "Team name", required = true)
    private String teamName;

    @ToolParam(
            description = "Kebab-case slug used as filename (e.g. 'explored-auth-module')",
            required = true)
    private String name;

    @ToolParam(description = "One-line summary for the MEMORY.md index", required = true)
    private String description;

    @ToolParam(description = "Memory type: USER, FEEDBACK, PROJECT, or REFERENCE", required = true)
    private String type;

    @ToolParam(description = "Markdown body content of the memory", required = true)
    private String content;

    @Nullable private final Path overrideRoot;

    public TeamMemoryWriteTool() {
        this.overrideRoot = null;
    }

    TeamMemoryWriteTool(Path overrideRoot) {
        this.overrideRoot = overrideRoot;
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        return Mono.fromCallable(() -> doExecute(args, ctx));
    }

    private ToolResult doExecute(Map<String, Object> args, ToolContext ctx) {
        String teamVal = (String) args.get("teamName");
        String nameVal = (String) args.get("name");
        String descVal = (String) args.get("description");
        String typeVal = (String) args.get("type");
        String contentVal = (String) args.get("content");

        if (teamVal == null || teamVal.isBlank()) {
            return ToolResult.error("team_memory_write", "Parameter 'teamName' is required");
        }
        if (nameVal == null || nameVal.isBlank()) {
            return ToolResult.error("team_memory_write", "Parameter 'name' is required");
        }
        if (descVal == null || descVal.isBlank()) {
            return ToolResult.error("team_memory_write", "Parameter 'description' is required");
        }
        if (typeVal == null || typeVal.isBlank()) {
            return ToolResult.error("team_memory_write", "Parameter 'type' is required");
        }
        if (contentVal == null || contentVal.isBlank()) {
            return ToolResult.error("team_memory_write", "Parameter 'content' is required");
        }

        MemoryType memoryType;
        try {
            memoryType = MemoryType.valueOf(typeVal.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ToolResult.error(
                    "team_memory_write",
                    "Invalid type '"
                            + typeVal
                            + "'. Must be one of: USER, FEEDBACK, PROJECT, REFERENCE");
        }

        try {
            Path root = overrideRoot != null ? overrideRoot : ctx.workspace().root();
            MemoryDirectoryManager manager = createTeamManager(root, teamVal);
            MemoryFile existing = manager.read(nameVal);
            MemoryFile file =
                    new MemoryFile(nameVal, descVal, memoryType, contentVal, Instant.now());
            manager.write(file);

            String action = existing != null ? "Updated" : "Created";
            return ToolResult.success(
                    "team_memory_write",
                    action + " team memory '" + nameVal + "' for team '" + teamVal + "'",
                    Map.of(
                            "name", nameVal,
                            "teamName", teamVal,
                            "type", memoryType.name(),
                            "action", action));
        } catch (IllegalArgumentException e) {
            return ToolResult.error("team_memory_write", e.getMessage());
        } catch (RuntimeException e) {
            return ToolResult.error(
                    "team_memory_write", "Failed to write team memory: " + e.getMessage());
        }
    }

    static MemoryDirectoryManager createTeamManager(Path workspaceRoot, String teamName) {
        return new MemoryDirectoryManager(
                workspaceRoot.resolve(".kairo/teams").resolve(teamName).resolve("memory"));
    }
}
