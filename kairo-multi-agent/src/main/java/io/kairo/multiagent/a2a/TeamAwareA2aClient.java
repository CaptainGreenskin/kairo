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
package io.kairo.multiagent.a2a;

import io.kairo.api.a2a.A2aClient;
import io.kairo.api.a2a.A2aException;
import io.kairo.api.a2a.AgentCard;
import io.kairo.api.a2a.AgentCardResolver;
import io.kairo.api.agent.Agent;
import io.kairo.api.message.Msg;
import io.kairo.api.team.TeamManager;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * {@link A2aClient} that scopes agent discovery and invocation within a team.
 *
 * <p>Wraps a delegate {@link A2aClient} and restricts operations to agents registered in a specific
 * team. Also supports auto-registering agents with both the {@link AgentCardResolver} and the
 * underlying delegate when they are added to the team via {@link TeamManager#addAgent(String,
 * Agent)}.
 */
public final class TeamAwareA2aClient implements A2aClient {

    private final A2aClient delegate;
    private final AgentCardResolver resolver;
    private final TeamManager teamManager;
    private final String teamName;

    public TeamAwareA2aClient(
            A2aClient delegate,
            AgentCardResolver resolver,
            TeamManager teamManager,
            String teamName) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
        this.teamManager = Objects.requireNonNull(teamManager, "teamManager must not be null");
        this.teamName = Objects.requireNonNull(teamName, "teamName must not be null");
    }

    /**
     * Register an agent with both the team and the A2A infrastructure.
     *
     * <p>This is a convenience method that: 1) adds the agent to the team via {@link
     * TeamManager#addAgent(String, Agent)}, 2) registers the agent card with the {@link
     * AgentCardResolver}, and 3) registers the agent instance with the delegate client.
     *
     * @param card the agent card to register
     * @param agent the agent instance
     */
    public void registerTeamAgent(AgentCard card, Agent agent) {
        resolver.register(card);
        if (delegate.supportsAgentRegistration()) {
            delegate.registerAgent(agent);
        }
        teamManager.addAgent(teamName, agent);
    }

    /**
     * Discover agents within this team by capability tags.
     *
     * @param requiredTags tags that must all be present
     * @return list of matching agent cards within the team
     */
    public List<AgentCard> discoverTeamAgents(Set<String> requiredTags) {
        List<String> teamAgentIds =
                teamManager.get(teamName).agents().stream().map(Agent::id).toList();
        return resolver.discover(requiredTags).stream()
                .filter(card -> teamAgentIds.contains(card.id()))
                .toList();
    }

    @Override
    public Mono<Msg> send(String targetAgentId, Msg message) {
        validateTeamAgent(targetAgentId);
        return delegate.send(targetAgentId, message);
    }

    @Override
    public Flux<Msg> stream(String targetAgentId, Msg message) {
        validateTeamAgent(targetAgentId);
        return delegate.stream(targetAgentId, message);
    }

    private void validateTeamAgent(String targetAgentId) {
        var team = teamManager.get(teamName);
        if (team == null) {
            throw new A2aException(targetAgentId, "Team not found: " + teamName);
        }
        boolean inTeam = team.agents().stream().anyMatch(a -> a.id().equals(targetAgentId));
        if (!inTeam) {
            throw new A2aException(
                    targetAgentId,
                    "Agent '" + targetAgentId + "' is not a member of team '" + teamName + "'");
        }
    }
}
