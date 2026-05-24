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
import io.kairo.core.memory.structured.MemoryDirectoryManager;
import io.kairo.core.memory.structured.MemoryFile;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import reactor.core.publisher.Mono;

/**
 * Read or search team-shared memories. With no name/query args, returns the MEMORY.md index for the
 * team. With 'name', reads a specific memory. With 'query', searches across team memories.
 */
@Tool(
        name = "team_memory_read",
        description =
                "Read shared team memories. With no name/query returns the MEMORY.md index. "
                        + "With 'name' returns a specific memory. With 'query' searches for"
                        + " relevant team memories.",
        category = ToolCategory.AGENT_AND_TASK)
public class TeamMemoryReadTool implements SyncTool {

    @ToolParam(description = "Team name", required = true)
    private String teamName;

    @ToolParam(description = "Name of a specific team memory to read")
    private String name;

    @ToolParam(description = "Search query to find relevant team memories")
    private String query;

    @Nullable private final Path overrideRoot;

    public TeamMemoryReadTool() {
        this.overrideRoot = null;
    }

    TeamMemoryReadTool(Path overrideRoot) {
        this.overrideRoot = overrideRoot;
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        return Mono.fromCallable(() -> doExecute(args, ctx));
    }

    private ToolResult doExecute(Map<String, Object> args, ToolContext ctx) {
        String teamVal = (String) args.get("teamName");
        String nameVal = (String) args.get("name");
        String queryVal = (String) args.get("query");

        if (teamVal == null || teamVal.isBlank()) {
            return ToolResult.error("team_memory_read", "Parameter 'teamName' is required");
        }

        try {
            Path root = overrideRoot != null ? overrideRoot : ctx.workspace().root();
            MemoryDirectoryManager manager = TeamMemoryWriteTool.createTeamManager(root, teamVal);

            if (nameVal != null && !nameVal.isBlank()) {
                return readByName(manager, nameVal, teamVal);
            }
            if (queryVal != null && !queryVal.isBlank()) {
                return searchByQuery(manager, queryVal, teamVal);
            }
            return returnIndex(manager, teamVal);
        } catch (IllegalArgumentException e) {
            return ToolResult.error("team_memory_read", e.getMessage());
        } catch (RuntimeException e) {
            return ToolResult.error(
                    "team_memory_read", "Failed to read team memory: " + e.getMessage());
        }
    }

    private ToolResult readByName(MemoryDirectoryManager manager, String name, String teamName) {
        MemoryFile file = manager.read(name);
        if (file == null) {
            return ToolResult.error(
                    "team_memory_read",
                    "No memory found with name '" + name + "' in team '" + teamName + "'");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Team: ").append(teamName).append('\n');
        sb.append("Name: ").append(file.name()).append('\n');
        sb.append("Type: ").append(file.type()).append('\n');
        sb.append("Description: ").append(file.description()).append('\n');
        sb.append("Updated: ").append(file.updatedAt()).append('\n');
        sb.append('\n');
        sb.append(file.body());

        return ToolResult.success(
                "team_memory_read",
                sb.toString(),
                Map.of("name", file.name(), "teamName", teamName, "type", file.type().name()));
    }

    private ToolResult searchByQuery(
            MemoryDirectoryManager manager, String query, String teamName) {
        List<MemoryFile> results = manager.search(query, 5);
        if (results.isEmpty()) {
            return ToolResult.success(
                    "team_memory_read",
                    "No team memories found matching '" + query + "' in team '" + teamName + "'");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(results.size())
                .append(" result(s) for '")
                .append(query)
                .append("' in team '")
                .append(teamName)
                .append("':\n\n");
        for (MemoryFile file : results) {
            sb.append("  [").append(file.type()).append("] ").append(file.name());
            sb.append(" — ").append(file.description()).append('\n');
        }

        return ToolResult.success(
                "team_memory_read",
                sb.toString().stripTrailing(),
                Map.of("count", results.size(), "teamName", teamName));
    }

    private ToolResult returnIndex(MemoryDirectoryManager manager, String teamName) {
        String index = manager.loadIndex();
        if (index.isBlank()) {
            return ToolResult.success(
                    "team_memory_read", "No team memories stored yet for team '" + teamName + "'.");
        }
        return ToolResult.success(
                "team_memory_read", "Team '" + teamName + "' memories:\n\n" + index);
    }
}
