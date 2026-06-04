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
package io.kairo.multiagent.team;

import io.kairo.api.a2a.AgentCard;
import io.kairo.api.a2a.AgentCardResolver;
import io.kairo.api.agent.Agent;
import io.kairo.api.team.MessageBus;
import io.kairo.api.team.Team;
import io.kairo.api.team.TeamCreateRequest;
import io.kairo.api.team.TeamLifecycleStatus;
import io.kairo.api.team.TeamManager;
import io.kairo.core.a2a.InProcessAgentCardResolver;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link TeamManager} for managing agent teams.
 *
 * <p>Creates teams with a shared {@link InProcessMessageBus} for intra-team communication. Each
 * team gets a unique {@code teamId} (UUID-based) separate from its human-readable {@code name}.
 *
 * <p>Thread-safe via {@link ConcurrentHashMap} and {@link CopyOnWriteArrayList}.
 */
public class DefaultTeamManager implements TeamManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultTeamManager.class);

    private final ConcurrentHashMap<String, TeamState> teams = new ConcurrentHashMap<>();
    private final AgentCardResolver agentCardResolver;

    public DefaultTeamManager() {
        this(null);
    }

    public DefaultTeamManager(AgentCardResolver agentCardResolver) {
        this.agentCardResolver = agentCardResolver;
    }

    @Override
    public Team create(TeamCreateRequest request) {
        String teamId = "team-" + UUID.randomUUID().toString().substring(0, 8);
        TeamState state =
                new TeamState(
                        teamId,
                        request.name(),
                        request.goal(),
                        new InProcessMessageBus(),
                        request.metadata());
        teams.put(teamId, state);
        log.info("Created team '{}' (id={})", request.name(), teamId);
        return state.snapshot();
    }

    @Override
    public void delete(String teamId) {
        TeamState state = teams.remove(teamId);
        if (state != null) {
            state.agents.forEach(
                    agent -> {
                        try {
                            agent.interrupt();
                        } catch (Exception e) {
                            log.warn(
                                    "Failed to interrupt agent '{}' during team deletion",
                                    agent.id(),
                                    e);
                        }
                    });
            MessageBus bus = state.messageBus;
            if (bus instanceof InProcessMessageBus inProcessBus) {
                state.agents.forEach(agent -> inProcessBus.unregisterAgent(agent.id()));
            }
            log.info(
                    "Deleted team '{}' (id={}) with {} agents",
                    state.name,
                    teamId,
                    state.agents.size());
        }
    }

    @Override
    public Team get(String teamId) {
        TeamState state = teams.get(teamId);
        return state == null ? null : state.snapshot();
    }

    @Override
    public void addAgent(String teamId, Agent agent) {
        TeamState state = teams.get(teamId);
        if (state == null) {
            throw new IllegalArgumentException("Team not found: " + teamId);
        }
        state.agents.add(agent);
        if (state.messageBus instanceof InProcessMessageBus inProcessBus) {
            inProcessBus.registerAgent(agent.id());
        }
        if (agentCardResolver != null) {
            AgentCard card = AgentCard.of(agent.id(), agent.name(), "");
            if (agentCardResolver instanceof InProcessAgentCardResolver inProcessResolver) {
                inProcessResolver.registerScoped(teamId, card);
            } else {
                agentCardResolver.register(card);
            }
        }
        log.info("Added agent '{}' to team '{}'", agent.id(), teamId);
    }

    @Override
    public void removeAgent(String teamId, String agentId) {
        TeamState state = teams.get(teamId);
        if (state == null) {
            throw new IllegalArgumentException("Team not found: " + teamId);
        }
        state.agents.removeIf(a -> a.id().equals(agentId));
        if (state.messageBus instanceof InProcessMessageBus inProcessBus) {
            inProcessBus.unregisterAgent(agentId);
        }
        if (agentCardResolver != null) {
            if (agentCardResolver instanceof InProcessAgentCardResolver inProcessResolver) {
                inProcessResolver.unregisterScoped(teamId, agentId);
            } else {
                agentCardResolver.unregister(agentId);
            }
        }
        log.info("Removed agent '{}' from team '{}'", agentId, teamId);
    }

    @Override
    public List<Team> listActive() {
        return teams.values().stream()
                .filter(
                        s ->
                                s.status == TeamLifecycleStatus.INITIALIZING
                                        || s.status == TeamLifecycleStatus.ACTIVE)
                .map(TeamState::snapshot)
                .toList();
    }

    @Override
    public void updateStatus(String teamId, TeamLifecycleStatus status) {
        TeamState state = teams.get(teamId);
        if (state != null) {
            state.status = status;
            log.info("Updated team '{}' status to {}", teamId, status);
        }
    }

    private static final class TeamState {
        final String teamId;
        final String name;
        final String goal;
        final MessageBus messageBus;
        final CopyOnWriteArrayList<Agent> agents = new CopyOnWriteArrayList<>();
        final Map<String, Object> metadata;
        final Instant createdAt;
        volatile TeamLifecycleStatus status;

        TeamState(
                String teamId,
                String name,
                String goal,
                MessageBus messageBus,
                Map<String, Object> metadata) {
            this.teamId = teamId;
            this.name = name;
            this.goal = goal;
            this.messageBus = messageBus;
            this.metadata = metadata;
            this.createdAt = Instant.now();
            this.status = TeamLifecycleStatus.INITIALIZING;
        }

        Team snapshot() {
            return new Team(
                    teamId,
                    name,
                    goal,
                    List.copyOf(agents),
                    messageBus,
                    status,
                    metadata,
                    createdAt);
        }
    }
}
