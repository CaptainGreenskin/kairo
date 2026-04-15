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

import io.kairo.api.message.Msg;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Asynchronous message bus for inter-agent communication. */
public interface MessageBus {

    /**
     * Send a message from one agent to another.
     *
     * @param fromAgentId the sender agent ID
     * @param toAgentId the recipient agent ID
     * @param message the message to send
     * @return a Mono completing when the message is enqueued
     */
    Mono<Void> send(String fromAgentId, String toAgentId, Msg message);

    /**
     * Receive messages destined for the given agent.
     *
     * @param agentId the agent ID to receive messages for
     * @return a Flux of messages as they arrive
     */
    Flux<Msg> receive(String agentId);

    /**
     * Broadcast a message from one agent to all other agents in the team.
     *
     * @param fromAgentId the sender agent ID
     * @param message the message to broadcast
     * @return a Mono completing when the broadcast is enqueued
     */
    Mono<Void> broadcast(String fromAgentId, Msg message);
}
