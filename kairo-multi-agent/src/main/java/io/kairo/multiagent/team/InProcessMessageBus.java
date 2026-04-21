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

import io.kairo.api.message.Msg;
import io.kairo.api.team.MessageBus;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * In-process implementation of {@link MessageBus} for inter-agent communication within the same
 * JVM.
 *
 * <p>Supports both pull-based (via {@link #poll(String)}) and push-based (via {@link
 * #receive(String)}) message consumption patterns.
 *
 * <p>Uses Reactor {@link Sinks.Many} for reactive message delivery and {@link
 * ConcurrentLinkedQueue} for reliable message buffering.
 */
public class InProcessMessageBus implements MessageBus {

    private static final Logger log = LoggerFactory.getLogger(InProcessMessageBus.class);

    /**
     * Multicast sinks keep per-subscriber demand; for slow subscribers we prefer bounded-loss over
     * unbounded in-memory growth.
     */
    private static final int MAX_DIRECT_DELIVERY_QUEUE = 1024;

    private final ConcurrentHashMap<String, Queue<Msg>> inboxes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Sinks.Many<Msg>> sinks = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> send(String fromAgentId, String toAgentId, Msg message) {
        return Mono.fromRunnable(
                () -> {
                    // Enqueue message into the target agent's inbox
                    inboxes.computeIfAbsent(toAgentId, k -> new ConcurrentLinkedQueue<>())
                            .add(message);

                    // Notify reactive sink if one exists
                    Sinks.Many<Msg> sink = sinks.get(toAgentId);
                    if (sink != null) {
                        sink.tryEmitNext(message);
                    }
                    log.debug("Message sent from agent '{}' to agent '{}'", fromAgentId, toAgentId);
                });
    }

    @Override
    public Flux<Msg> receive(String agentId) {
        Sinks.Many<Msg> sink =
                sinks.computeIfAbsent(
                        agentId, k -> Sinks.many().replay().limit(MAX_DIRECT_DELIVERY_QUEUE));
        return sink.asFlux();
    }

    @Override
    public Mono<Void> broadcast(String fromAgentId, Msg message) {
        // Collect all send operations and wait for all to complete
        return Flux.fromIterable(inboxes.keySet())
                .filter(id -> !id.equals(fromAgentId))
                .flatMap(id -> send(fromAgentId, id, message))
                .then()
                .doOnSuccess(
                        v ->
                                log.debug(
                                        "Message broadcast from agent '{}' to {} recipients",
                                        fromAgentId,
                                        Math.max(0, inboxes.size() - 1)));
    }

    /**
     * Poll all pending messages for the given agent (non-reactive pull model).
     *
     * @param agentId the agent to poll messages for
     * @return list of pending messages, empty if none
     */
    public List<Msg> poll(String agentId) {
        Queue<Msg> inbox = inboxes.get(agentId);
        if (inbox == null) {
            return List.of();
        }
        List<Msg> messages = new ArrayList<>();
        Msg msg;
        while ((msg = inbox.poll()) != null) {
            messages.add(msg);
        }
        return messages;
    }

    /**
     * Register an agent's inbox so it can receive broadcast messages.
     *
     * @param agentId the agent ID to register
     */
    public void registerAgent(String agentId) {
        inboxes.computeIfAbsent(agentId, k -> new ConcurrentLinkedQueue<>());
    }

    /**
     * Unregister an agent and clean up its resources.
     *
     * @param agentId the agent ID to unregister
     */
    public void unregisterAgent(String agentId) {
        inboxes.remove(agentId);
        Sinks.Many<Msg> sink = sinks.remove(agentId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }
}
