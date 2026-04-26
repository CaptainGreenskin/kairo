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
package io.kairo.api.a2a;

import io.kairo.api.Experimental;
import io.kairo.api.message.Msg;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Client SPI for invoking other agents via the A2A Protocol.
 *
 * <p>Provides a request-response interaction pattern that complements the existing {@code
 * MessageBus} (fire-and-forget). Use {@code A2aClient} when the caller needs a result back from the
 * target agent; use {@code MessageBus} for broadcast and event-driven communication.
 *
 * <p>The API is pure Reactor. Users who need blocking semantics can call {@code .block()} on the
 * returned {@code Mono} or {@code Flux}.
 *
 * @see AgentCardResolver
 */
@Experimental("A2A client SPI; introduced in v0.10, targeting stabilization in v1.1")
public interface A2aClient {

    /**
     * Send a message to a target agent and wait for its response.
     *
     * @param targetAgentId the identifier of the target agent
     * @param message the input message
     * @return a {@link Mono} emitting the target agent's response
     * @throws A2aException if the target agent is not found or invocation fails
     */
    Mono<Msg> send(String targetAgentId, Msg message);

    /**
     * Send a message to a target agent and stream its response.
     *
     * @param targetAgentId the identifier of the target agent
     * @param message the input message
     * @return a {@link Flux} of response messages from the target agent
     * @throws A2aException if the target agent is not found or invocation fails
     */
    Flux<Msg> stream(String targetAgentId, Msg message);

    /**
     * Check whether this client supports runtime agent instance registration.
     *
     * <p>In-process implementations return {@code true} and can be cast to register agent
     * instances. Remote/HTTP implementations return {@code false}.
     *
     * @return true if agent instances can be registered at runtime
     */
    default boolean supportsAgentRegistration() {
        return false;
    }

    /**
     * Register an agent instance for in-process invocation.
     *
     * <p>Only supported when {@link #supportsAgentRegistration()} returns {@code true}.
     *
     * @param agent the agent instance to register
     * @throws UnsupportedOperationException if not supported by this implementation
     */
    default void registerAgent(io.kairo.api.agent.Agent agent) {
        throw new UnsupportedOperationException("Agent registration not supported");
    }
}
