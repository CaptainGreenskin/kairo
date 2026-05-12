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

import io.kairo.api.team.TeamManager;
import io.kairo.api.tool.SyncTool;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import java.util.Map;
import reactor.core.publisher.Mono;

/** Deletes an agent team and stops all its agents. */
@Tool(
        name = "team_delete",
        description = "Delete an agent team and stop all its agents.",
        category = ToolCategory.AGENT_AND_TASK)
public class TeamDeleteTool implements SyncTool {

    @ToolParam(description = "Name of the team to delete", required = true)
    private String name;

    private final TeamManager teamManager;

    /**
     * Create a new TeamDeleteTool.
     *
     * @param teamManager the team manager for deleting teams
     */
    public TeamDeleteTool(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        return Mono.fromCallable(() -> executeSync(args, ctx));
    }

    private ToolResult executeSync(Map<String, Object> input, ToolContext ctx) {
        String name = (String) input.get("name");
        if (name == null || name.isBlank()) {
            return ToolResult.error(null, "Parameter 'name' is required");
        }

        teamManager.delete(name);
        return ToolResult.success(null, "Team deleted: " + name, Map.of());
    }
}
