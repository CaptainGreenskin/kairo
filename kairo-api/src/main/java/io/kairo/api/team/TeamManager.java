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

/** Manages team lifecycle: creation, deletion, and agent membership. */
@Experimental("Team manager SPI; introduced in v0.10, targeting stabilization in v1.1")
public interface TeamManager {

    /**
     * Create a new team.
     *
     * @param name the team name
     * @return the created team
     */
    Team create(String name);

    /**
     * Delete a team by name.
     *
     * @param name the team name
     */
    void delete(String name);

    /**
     * Get a team by name.
     *
     * @param name the team name
     * @return the team
     */
    Team get(String name);

    /**
     * Add an agent to a team.
     *
     * @param teamName the team name
     * @param agent the agent to add
     */
    void addAgent(String teamName, Agent agent);

    /**
     * Remove an agent from a team.
     *
     * @param teamName the team name
     * @param agentId the agent ID to remove
     */
    void removeAgent(String teamName, String agentId);
}
