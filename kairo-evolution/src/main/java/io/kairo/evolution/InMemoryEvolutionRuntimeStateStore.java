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
package io.kairo.evolution;

import io.kairo.api.evolution.EvolutionCounters;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for per-agent evolution runtime state and counters.
 *
 * <p>Thread-safe via {@link ConcurrentHashMap}. State is lost on JVM restart — suitable for
 * development and testing. Production deployments should use a durable implementation.
 *
 * @since v0.9 (Experimental)
 */
public class InMemoryEvolutionRuntimeStateStore {

    private final ConcurrentHashMap<String, EvolutionState> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, EvolutionCounters> counters = new ConcurrentHashMap<>();

    /**
     * Get the current evolution state for an agent, defaulting to {@link EvolutionState#IDLE}.
     *
     * @param agentName the agent identifier
     * @return the current state, never null
     */
    public EvolutionState getState(String agentName) {
        return states.getOrDefault(agentName, EvolutionState.IDLE);
    }

    /**
     * Set the evolution state for an agent.
     *
     * @param agentName the agent identifier
     * @param state the new state
     */
    public void setState(String agentName, EvolutionState state) {
        states.put(agentName, state);
    }

    /**
     * Get the evolution counters for an agent, defaulting to {@link EvolutionCounters#ZERO}.
     *
     * @param agentName the agent identifier
     * @return the current counters, never null
     */
    public EvolutionCounters getCounters(String agentName) {
        return counters.getOrDefault(agentName, EvolutionCounters.ZERO);
    }

    /**
     * Set the evolution counters for an agent.
     *
     * @param agentName the agent identifier
     * @param c the new counters
     */
    public void setCounters(String agentName, EvolutionCounters c) {
        counters.put(agentName, c);
    }

    /**
     * Reset all state and counters for an agent.
     *
     * @param agentName the agent identifier
     */
    public void reset(String agentName) {
        states.remove(agentName);
        counters.remove(agentName);
    }
}
