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
import java.nio.file.Path;
import java.util.Map;
import javax.annotation.Nullable;
import reactor.core.publisher.Mono;

/** Delete a team-shared memory file and update the team's MEMORY.md index. */
@Tool(
        name = "team_memory_delete",
        description = "Delete a shared team memory and update the team's MEMORY.md index.",
        category = ToolCategory.AGENT_AND_TASK,
        sideEffect = ToolSideEffect.WRITE)
public class TeamMemoryDeleteTool implements SyncTool {

    @ToolParam(description = "Team name", required = true)
    private String teamName;

    @ToolParam(description = "Name of the team memory to delete", required = true)
    private String name;

    @Nullable private final Path overrideRoot;

    public TeamMemoryDeleteTool() {
        this.overrideRoot = null;
    }

    TeamMemoryDeleteTool(Path overrideRoot) {
        this.overrideRoot = overrideRoot;
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        return Mono.fromCallable(() -> doExecute(args, ctx));
    }

    private ToolResult doExecute(Map<String, Object> args, ToolContext ctx) {
        String teamVal = (String) args.get("teamName");
        String nameVal = (String) args.get("name");

        if (teamVal == null || teamVal.isBlank()) {
            return ToolResult.error("team_memory_delete", "Parameter 'teamName' is required");
        }
        if (nameVal == null || nameVal.isBlank()) {
            return ToolResult.error("team_memory_delete", "Parameter 'name' is required");
        }

        try {
            Path root = overrideRoot != null ? overrideRoot : ctx.workspace().root();
            MemoryDirectoryManager manager = TeamMemoryWriteTool.createTeamManager(root, teamVal);
            boolean deleted = manager.delete(nameVal);
            if (deleted) {
                return ToolResult.success(
                        "team_memory_delete",
                        "Deleted team memory '" + nameVal + "' from team '" + teamVal + "'");
            } else {
                return ToolResult.error(
                        "team_memory_delete",
                        "No memory found with name '" + nameVal + "' in team '" + teamVal + "'");
            }
        } catch (IllegalArgumentException e) {
            return ToolResult.error("team_memory_delete", e.getMessage());
        } catch (RuntimeException e) {
            return ToolResult.error(
                    "team_memory_delete", "Failed to delete team memory: " + e.getMessage());
        }
    }
}
