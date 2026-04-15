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

/** Core agent abstraction. An agent can receive a message and produce a response. */
public interface Agent {

    /**
     * Process an input message and return the agent's response.
     *
     * @param input the input message
     * @return a Mono emitting the agent's response message
     */
    Mono<Msg> call(Msg input);

    /**
     * The unique identifier of this agent.
     *
     * @return the agent ID
     */
    String id();

    /**
     * The human-readable name of this agent.
     *
     * @return the agent name
     */
    String name();

    /**
     * The current lifecycle state of this agent.
     *
     * @return the agent state
     */
    AgentState state();

    /** Interrupt the agent's current processing. */
    void interrupt();
}
