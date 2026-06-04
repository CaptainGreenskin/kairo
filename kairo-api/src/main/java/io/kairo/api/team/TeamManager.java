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
package io.kairo.api.team;

import io.kairo.api.Experimental;
import io.kairo.api.agent.Agent;
import java.util.List;

/** Manages team lifecycle: creation, deletion, and agent membership. */
@Experimental("Team manager SPI; introduced in v0.10, targeting stabilization in v1.2.0")
public interface TeamManager {

    /**
     * Create a new team from a request. The implementation generates a unique teamId.
     *
     * @param request the creation request containing name, goal, and metadata
     * @return the created team
     */
    Team create(TeamCreateRequest request);

    /**
     * Create a new team by name only.
     *
     * @deprecated Use {@link #create(TeamCreateRequest)} for richer team creation.
     */
    @Deprecated(since = "1.2.0", forRemoval = true)
    default Team create(String name) {
        return create(TeamCreateRequest.ofName(name));
    }

    /**
     * Delete a team.
     *
     * @param teamId the team identifier
     */
    void delete(String teamId);

    /**
     * Get a team by identifier. Returns null if not found.
     *
     * @param teamId the team identifier
     * @return the team, or null
     */
    Team get(String teamId);

    /**
     * Add an agent to a team.
     *
     * @param teamId the team identifier
     * @param agent the agent to add
     */
    void addAgent(String teamId, Agent agent);

    /**
     * Remove an agent from a team.
     *
     * @param teamId the team identifier
     * @param agentId the agent ID to remove
     */
    void removeAgent(String teamId, String agentId);

    /**
     * List all active (non-terminal) teams.
     *
     * @return list of active teams
     */
    default List<Team> listActive() {
        return List.of();
    }

    /**
     * Update a team's lifecycle status.
     *
     * @param teamId the team identifier
     * @param status the new status
     */
    default void updateStatus(String teamId, TeamLifecycleStatus status) {}
}
