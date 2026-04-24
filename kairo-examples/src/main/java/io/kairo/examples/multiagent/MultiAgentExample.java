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
package io.kairo.examples.multiagent;

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentState;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.team.Team;
import io.kairo.api.team.TeamConfig;
import io.kairo.api.team.TeamCoordinator;
import io.kairo.api.team.TeamExecutionRequest;
import io.kairo.api.team.TeamResult;
import io.kairo.multiagent.team.DefaultTaskDispatchCoordinator;
import io.kairo.multiagent.team.InProcessMessageBus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * Demonstrates Kairo v0.10 multi-agent orchestration via the new {@link TeamCoordinator} SPI
 * (ADR-015, ADR-016).
 *
 * <p>The example wires three role-bound agents into a {@link Team}, hands the team to a {@link
 * DefaultTaskDispatchCoordinator}, and prints the resulting {@link TeamResult}. No LLM API key is
 * required — the agents are in-process stubs that echo their assignment.
 *
 * <p>Usage:
 *
 * <pre>
 *   mvn exec:java -pl kairo-examples \
 *     -Dexec.mainClass="io.kairo.examples.multiagent.MultiAgentExample"
 * </pre>
 */
public final class MultiAgentExample {

    private MultiAgentExample() {}

    /**
     * Entry point.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        System.out.println("=== Kairo Multi-Agent Orchestration Example (v0.10) ===");

        // 1. Build a team of three named stub agents sharing an in-process message bus.
        InProcessMessageBus bus = new InProcessMessageBus();
        List<Agent> agents =
                List.of(
                        new EchoAgent("architect"),
                        new EchoAgent("coder"),
                        new EchoAgent("tester"));
        agents.forEach(a -> bus.registerAgent(a.id()));
        Team team = new Team("product-team", agents, bus);

        // 2. Pick a coordinator. The ADR-016 SPI is TeamCoordinator — we program against the
        //    interface so adopters can swap in ExpertTeamCoordinator (kairo-expert-team) or any
        //    third-party implementation without touching this call site.
        TeamCoordinator coordinator = new DefaultTaskDispatchCoordinator();

        // 3. Build an execution request. A caller-supplied plan in context is optional; when
        //    absent the coordinator synthesises a single-step plan from the goal.
        TeamExecutionRequest request =
                new TeamExecutionRequest(
                        UUID.randomUUID().toString(),
                        "Ship the v0.10 docs landing page.",
                        Map.of(),
                        TeamConfig.defaults());

        // 4. Execute and block for the terminal result. Reactive callers would subscribe instead.
        TeamResult result = coordinator.execute(request, team).block();

        // 5. Print the outcome.
        System.out.println();
        System.out.println("requestId      : " + result.requestId());
        System.out.println("status         : " + result.status());
        System.out.println("totalDuration  : " + result.totalDuration());
        System.out.println("stepOutcomes   : " + result.stepOutcomes().size());
        result.stepOutcomes()
                .forEach(
                        step ->
                                System.out.println(
                                        "  - "
                                                + step.stepId()
                                                + " ["
                                                + step.finalVerdict().outcome()
                                                + "] → "
                                                + step.output()));
        result.finalOutput().ifPresent(out -> System.out.println("finalOutput    : " + out));
        if (!result.warnings().isEmpty()) {
            System.out.println("warnings       : " + result.warnings());
        }
    }

    /**
     * Minimal {@link Agent} stub that echoes the prompt it receives. Enough to exercise the
     * coordinator choreography without touching an LLM.
     */
    private static final class EchoAgent implements Agent {

        private final String id;

        EchoAgent(String id) {
            this.id = id;
        }

        @Override
        public Mono<Msg> call(Msg message) {
            return Mono.just(
                    Msg.of(MsgRole.ASSISTANT, "[" + id + "] acknowledged: " + message.text()));
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String name() {
            return id;
        }

        @Override
        public AgentState state() {
            return AgentState.IDLE;
        }

        @Override
        public void interrupt() {
            // no-op
        }
    }
}
