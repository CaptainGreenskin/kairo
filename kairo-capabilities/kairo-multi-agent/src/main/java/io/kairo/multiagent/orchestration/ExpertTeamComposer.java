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
package io.kairo.multiagent.orchestration;

import io.kairo.api.agent.Agent;
import io.kairo.api.message.Msg;
import io.kairo.api.team.MessageBus;
import io.kairo.multiagent.subagent.ExpertRoleRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Generic builder for the four parts every expert-team consumer needs:
 *
 * <ol>
 *   <li>{@link ExpertTeamCoordinator}
 *   <li>{@link ExpertRoleRegistry}
 *   <li>{@link MessageBus}
 *   <li>A {@link List} of worker {@link Agent}s
 * </ol>
 *
 * <p>This was extracted upstream from kairo-code's {@code ExpertTeamFactory} (which hardcoded
 * {@code CodeAgentFactory.createSession(...)} as the worker supplier) so kairo-assistant — and any
 * other Kairo agent framework — can compose an expert team using its own agent factory instead of
 * having to depend on kairo-code.
 *
 * <p>Typical use:
 *
 * <pre>{@code
 * Composition c = ExpertTeamComposer.create(
 *     3, () -> myAgentFactory.createSession(myConfig));
 * mySwarmWrapper = new MySwarmWrapper(c.coordinator(), c.roleRegistry(), c.messageBus(), c.agents());
 * }</pre>
 *
 * @since 1.0
 */
public final class ExpertTeamComposer {

    private ExpertTeamComposer() {}

    /**
     * Build a fresh team composition: a default {@link ExpertTeamCoordinator} (no executor
     * dispatcher), an empty {@link ExpertRoleRegistry}, a no-op {@link MessageBus} suitable for
     * single-process use, and {@code agentCount} worker agents from {@code agentSupplier}.
     *
     * <p>Callers wanting custom plumbing (real cross-process bus, role registry pre-populated from
     * disk, etc.) should call the constructor pieces directly rather than this helper.
     *
     * @param agentCount number of worker agents to create (must be ≥ 1)
     * @param agentSupplier factory invoked once per worker; must not return null
     * @return a {@link Composition} bundling the four parts
     */
    public static Composition create(int agentCount, Supplier<Agent> agentSupplier) {
        if (agentCount < 1) {
            throw new IllegalArgumentException("agentCount must be ≥ 1, got " + agentCount);
        }
        if (agentSupplier == null) {
            throw new IllegalArgumentException("agentSupplier must not be null");
        }
        List<Agent> agents = new ArrayList<>(agentCount);
        for (int i = 0; i < agentCount; i++) {
            Agent a = agentSupplier.get();
            if (a == null) {
                throw new IllegalStateException("agentSupplier returned null on iteration " + i);
            }
            agents.add(a);
        }
        ExpertTeamCoordinator coordinator = new ExpertTeamCoordinator(null);
        ExpertRoleRegistry roleRegistry = new ExpertRoleRegistry();
        MessageBus messageBus = noOpMessageBus();
        return new Composition(coordinator, roleRegistry, messageBus, agents);
    }

    /**
     * No-op message bus — implementations of {@code send}/{@code receive}/{@code broadcast} return
     * empty Mono / Flux. Use this when running an expert team in-process where workers coordinate
     * through the coordinator directly, not through cross-agent messaging.
     */
    public static MessageBus noOpMessageBus() {
        return new MessageBus() {
            @Override
            public Mono<Void> send(String fromAgentId, String toAgentId, Msg msg) {
                return Mono.empty();
            }

            @Override
            public Flux<Msg> receive(String agentId) {
                return Flux.empty();
            }

            @Override
            public Mono<Void> broadcast(String fromAgentId, Msg msg) {
                return Mono.empty();
            }
        };
    }

    /** Bundled output of {@link #create(int, Supplier)}. */
    public record Composition(
            ExpertTeamCoordinator coordinator,
            ExpertRoleRegistry roleRegistry,
            MessageBus messageBus,
            List<Agent> agents) {
        public Composition {
            agents = List.copyOf(agents);
        }
    }
}
