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

import io.kairo.api.team.Team;
import io.kairo.api.team.TeamManager;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.tool.ToolHandler;
import java.util.Map;

/**
 * Creates a new agent team for collaborative work.
 *
 * <p>A team provides a shared task board and message bus for multiple agents to coordinate on
 * complex tasks.
 */
@Tool(
        name = "team_create",
        description = "Create a new agent team for collaborative work.",
        category = ToolCategory.AGENT_AND_TASK)
public class TeamCreateTool implements ToolHandler {

    @ToolParam(description = "Name for the team", required = true)
    private String name;

    private final TeamManager teamManager;

    /**
     * Create a new TeamCreateTool.
     *
     * @param teamManager the team manager for creating teams
     */
    public TeamCreateTool(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @Override
    public ToolResult execute(Map<String, Object> input) {
        String name = (String) input.get("name");
        if (name == null || name.isBlank()) {
            return new ToolResult(null, "Parameter 'name' is required", true, Map.of());
        }

        Team team = teamManager.create(name);
        return new ToolResult(
                null, String.format("Created team '%s'", name), false, Map.of("teamName", name));
    }
}
