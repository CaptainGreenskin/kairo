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
package io.kairo.api.agent;

import io.kairo.api.message.Msg;
import reactor.core.publisher.Mono;

/**
 * The agent loop drives the reasoning-acting cycle.
 *
 * <p>It manages the iterative process of:
 *
 * <ol>
 *   <li>Sending messages to the model (reasoning)
 *   <li>Executing tool calls (acting)
 *   <li>Feeding results back into context
 *   <li>Repeating until completion or budget exhaustion
 * </ol>
 */
public interface AgentLoop {

    /**
     * Run the agent loop with the given input message.
     *
     * @param agent the agent to run
     * @param input the initial input message
     * @return a Mono emitting the final response message
     */
    Mono<Msg> run(Agent agent, Msg input);

    /** Interrupt the currently running loop. */
    void interrupt();

    /**
     * Take a checkpoint of the current agent state.
     *
     * @return the current agent state
     */
    AgentState checkpoint();

    /**
     * Restore the agent loop from a saved state.
     *
     * @param state the state to restore
     */
    void restore(AgentState state);
}
