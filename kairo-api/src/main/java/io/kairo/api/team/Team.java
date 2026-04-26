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
import java.util.Objects;

/**
 * A team of agents collaborating via a shared {@link MessageBus}.
 *
 * <p>Task-board semantics that the legacy v0.9 {@code Team} aggregated directly are now an
 * implementation detail of the concrete {@link TeamCoordinator} that drives the team (for example,
 * {@code DefaultTaskDispatchCoordinator} in {@code kairo-multi-agent}). Callers wire a team with
 * its agents and message bus and hand it to a coordinator.
 *
 * @since v0.10
 */
@Experimental("Team aggregate root; introduced in v0.10, targeting stabilization in v1.1")
public class Team {

    private final String name;
    private final List<Agent> agents;
    private final MessageBus messageBus;

    /**
     * Create a new team.
     *
     * @param name team name; non-null
     * @param agents initial agent roster; defensively copied, may be empty
     * @param messageBus message bus for inter-agent communication; non-null
     */
    public Team(String name, List<Agent> agents, MessageBus messageBus) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(agents, "agents must not be null");
        this.agents = List.copyOf(agents);
        this.messageBus = Objects.requireNonNull(messageBus, "messageBus must not be null");
    }

    /** The team name. */
    public String name() {
        return name;
    }

    /** The agents in this team (immutable view). */
    public List<Agent> agents() {
        return agents;
    }

    /** The message bus for inter-agent communication. */
    public MessageBus messageBus() {
        return messageBus;
    }
}
