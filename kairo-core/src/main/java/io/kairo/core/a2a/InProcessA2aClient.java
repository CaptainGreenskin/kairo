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
package io.kairo.core.a2a;

import io.kairo.api.a2a.A2aClient;
import io.kairo.api.a2a.A2aException;
import io.kairo.api.a2a.A2aNamespaces;
import io.kairo.api.a2a.AgentCard;
import io.kairo.api.a2a.AgentCardResolver;
import io.kairo.api.agent.Agent;
import io.kairo.api.message.Msg;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * In-process {@link A2aClient} that routes invocations to agents within the same JVM.
 *
 * <p>Resolves target agents via a local {@link AgentCardResolver} and delegates to {@link
 * Agent#call(Msg)}. Streaming is supported if the target agent's card declares {@code
 * streaming=true}; otherwise {@link #stream(String, Msg)} falls back to wrapping the single
 * response in a {@code Flux}.
 */
public final class InProcessA2aClient implements A2aClient {

    private final AgentCardResolver resolver;
    private final Map<String, Agent> agents = new ConcurrentHashMap<>();

    public InProcessA2aClient(AgentCardResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
    }

    /**
     * Register an agent instance that can be invoked by its card's ID.
     *
     * <p>The agent's {@link AgentCard} must already be registered in the {@link AgentCardResolver}.
     *
     * @param agent the agent instance to register
     */
    @Override
    public boolean supportsAgentRegistration() {
        return true;
    }

    @Override
    public void registerAgent(Agent agent) {
        Objects.requireNonNull(agent, "agent must not be null");
        agents.put(agent.id(), agent);
    }

    public void registerScopedAgent(String namespace, Agent agent) {
        Objects.requireNonNull(agent, "agent must not be null");
        agents.put(A2aNamespaces.scoped(namespace, agent.id()), agent);
    }

    /**
     * Unregister an agent instance.
     *
     * @param agentId the agent identifier to remove
     */
    public void unregisterAgent(String agentId) {
        agents.remove(agentId);
    }

    public void unregisterScopedAgent(String namespace, String agentId) {
        agents.remove(A2aNamespaces.scoped(namespace, agentId));
    }

    @Override
    public Mono<Msg> send(String targetAgentId, Msg message) {
        return resolveAgent(targetAgentId)
                .flatMap(
                        agent ->
                                agent.call(message)
                                        .onErrorMap(
                                                e -> !(e instanceof A2aException),
                                                e ->
                                                        new A2aException(
                                                                targetAgentId,
                                                                "Agent invocation failed: "
                                                                        + e.getMessage(),
                                                                e)));
    }

    @Override
    public Flux<Msg> stream(String targetAgentId, Msg message) {
        return resolveAgent(targetAgentId)
                .flatMapMany(
                        agent -> {
                            // Full streaming requires Agent.callStream() — planned for v0.5.
                            // Currently wraps the single response in a Flux.
                            return agent.call(message)
                                    .flux()
                                    .onErrorMap(
                                            e -> !(e instanceof A2aException),
                                            e ->
                                                    new A2aException(
                                                            targetAgentId,
                                                            "Agent invocation failed: "
                                                                    + e.getMessage(),
                                                            e));
                        });
    }

    private Mono<Agent> resolveAgent(String targetAgentId) {
        Agent agent = agents.get(targetAgentId);
        if (agent != null) {
            return Mono.just(agent);
        }
        if (targetAgentId != null && targetAgentId.contains(":")) {
            String rawAgentId = targetAgentId.substring(targetAgentId.indexOf(':') + 1);
            Agent raw = agents.get(rawAgentId);
            if (raw != null) {
                return Mono.just(raw);
            }
        }
        // Distinguish between "card not found" and "no agent instance"
        boolean cardMissing = resolver.resolve(targetAgentId).isEmpty();
        if (cardMissing
                && resolver instanceof InProcessAgentCardResolver inProcess
                && targetAgentId != null
                && targetAgentId.contains(":")) {
            String namespace = targetAgentId.substring(0, targetAgentId.indexOf(':'));
            String rawAgentId = targetAgentId.substring(targetAgentId.indexOf(':') + 1);
            cardMissing = inProcess.resolveScoped(namespace, rawAgentId).isEmpty();
        }
        if (cardMissing) {
            return Mono.error(
                    new A2aException(
                            targetAgentId, "No agent card registered for id: " + targetAgentId));
        }
        return Mono.error(
                new A2aException(
                        targetAgentId,
                        "Agent card found but no Agent instance registered for id: "
                                + targetAgentId));
    }
}
