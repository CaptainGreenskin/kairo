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
import io.kairo.api.team.TeamManager;
import io.kairo.core.a2a.InProcessAgentCardResolver;
import io.kairo.multiagent.task.DefaultTaskBoard;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link TeamManager} for managing agent teams.
 *
 * <p>Creates teams with shared {@link DefaultTaskBoard} and {@link InProcessMessageBus} for
 * intra-team coordination. Thread-safe via {@link ConcurrentHashMap}.
 */
public class DefaultTeamManager implements TeamManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultTeamManager.class);

    private final ConcurrentHashMap<String, Team> teams = new ConcurrentHashMap<>();
    private final AgentCardResolver agentCardResolver;

    /** Creates a {@code DefaultTeamManager} without A2A agent-card integration. */
    public DefaultTeamManager() {
        this(null);
    }

    /**
     * Creates a {@code DefaultTeamManager} with optional A2A agent-card integration.
     *
     * <p>When a non-null resolver is provided, agents added via {@link #addAgent(String, Agent)}
     * are automatically registered as {@link AgentCard}s for A2A discovery, and removed agents are
     * unregistered.
     *
     * @param agentCardResolver the resolver to use for agent card registration, or {@code null}
     */
    public DefaultTeamManager(AgentCardResolver agentCardResolver) {
        this.agentCardResolver = agentCardResolver;
    }

    @Override
    public Team create(String name) {
        DefaultTaskBoard taskBoard = new DefaultTaskBoard();
        InProcessMessageBus messageBus = new InProcessMessageBus();
        Team team = new Team(name, new CopyOnWriteArrayList<>(), taskBoard, messageBus);
        teams.put(name, team);
        log.info("Created team '{}'", name);
        return team;
    }

    @Override
    public void delete(String name) {
        Team team = teams.remove(name);
        if (team != null) {
            // Interrupt all running agents in the team
            team.agents()
                    .forEach(
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
            // Clean up message bus
            MessageBus bus = team.messageBus();
            if (bus instanceof InProcessMessageBus inProcessBus) {
                team.agents().forEach(agent -> inProcessBus.unregisterAgent(agent.id()));
            }
            log.info("Deleted team '{}' with {} agents", name, team.agents().size());
        }
    }

    @Override
    public Team get(String name) {
        return teams.get(name);
    }

    @Override
    public void addAgent(String teamName, Agent agent) {
        Team team = teams.get(teamName);
        if (team == null) {
            throw new IllegalArgumentException("Team not found: " + teamName);
        }
        team.agents().add(agent);
        // Register agent on message bus for broadcast support
        MessageBus bus = team.messageBus();
        if (bus instanceof InProcessMessageBus inProcessBus) {
            inProcessBus.registerAgent(agent.id());
        }
        // Register agent card for A2A discovery
        if (agentCardResolver != null) {
            AgentCard card = AgentCard.of(agent.id(), agent.name(), "");
            if (agentCardResolver instanceof InProcessAgentCardResolver inProcessResolver) {
                inProcessResolver.registerScoped(teamName, card);
            } else {
                agentCardResolver.register(card);
            }
            log.debug("Registered AgentCard for '{}'", agent.id());
        }
        log.info("Added agent '{}' to team '{}'", agent.id(), teamName);
    }

    @Override
    public void removeAgent(String teamName, String agentId) {
        Team team = teams.get(teamName);
        if (team == null) {
            throw new IllegalArgumentException("Team not found: " + teamName);
        }
        team.agents().removeIf(a -> a.id().equals(agentId));
        // Unregister from message bus
        MessageBus bus = team.messageBus();
        if (bus instanceof InProcessMessageBus inProcessBus) {
            inProcessBus.unregisterAgent(agentId);
        }
        // Unregister agent card from A2A discovery
        if (agentCardResolver != null) {
            if (agentCardResolver instanceof InProcessAgentCardResolver inProcessResolver) {
                inProcessResolver.unregisterScoped(teamName, agentId);
            } else {
                agentCardResolver.unregister(agentId);
            }
            log.debug("Unregistered AgentCard for '{}'", agentId);
        }
        log.info("Removed agent '{}' from team '{}'", agentId, teamName);
    }
}
