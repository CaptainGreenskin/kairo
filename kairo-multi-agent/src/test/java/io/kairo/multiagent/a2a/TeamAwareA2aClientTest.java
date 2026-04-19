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
package io.kairo.multiagent.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.a2a.A2aException;
import io.kairo.api.a2a.AgentCard;
import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentState;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.core.a2a.InProcessA2aClient;
import io.kairo.core.a2a.InProcessAgentCardResolver;
import io.kairo.multiagent.team.DefaultTeamManager;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Tests for {@link TeamAwareA2aClient}. */
class TeamAwareA2aClientTest {

    private InProcessAgentCardResolver resolver;
    private InProcessA2aClient delegate;
    private DefaultTeamManager teamManager;
    private TeamAwareA2aClient client;
    private String teamName;

    @BeforeEach
    void setUp() {
        resolver = new InProcessAgentCardResolver();
        delegate = new InProcessA2aClient(resolver);
        teamManager = new DefaultTeamManager();
        teamName = "test-team";
        teamManager.create(teamName);
        client = new TeamAwareA2aClient(delegate, resolver, teamManager, teamName);
    }

    private Agent stubAgent(String id, Mono<Msg> response) {
        return new Agent() {
            @Override
            public Mono<Msg> call(Msg input) {
                return response;
            }

            @Override
            public String id() {
                return id;
            }

            @Override
            public String name() {
                return "stub-" + id;
            }

            @Override
            public AgentState state() {
                return AgentState.IDLE;
            }

            @Override
            public void interrupt() {}
        };
    }

    @Nested
    @DisplayName("registerTeamAgent")
    class RegisterTeamAgent {

        @Test
        @DisplayName("registers card, agent instance, and team membership")
        void fullRegistration() {
            AgentCard card =
                    new AgentCard(
                            "a1", "Agent 1", "desc", "1.0", List.of("java"), true, null, null);
            Agent agent = stubAgent("a1", Mono.just(Msg.of(MsgRole.ASSISTANT, "ok")));

            client.registerTeamAgent(card, agent);

            // Card registered
            assertThat(resolver.resolve("a1")).isPresent();
            // Agent instance registered
            assertThat(delegate.supportsAgentRegistration()).isTrue();
            // Team membership
            assertThat(teamManager.get(teamName).agents()).hasSize(1);
            assertThat(teamManager.get(teamName).agents().get(0).id()).isEqualTo("a1");
        }

        @Test
        @DisplayName("registered agent can be invoked via send")
        void invokeAfterRegistration() {
            AgentCard card = AgentCard.of("a1", "Agent 1", "desc");
            Agent agent = stubAgent("a1", Mono.just(Msg.of(MsgRole.ASSISTANT, "response")));

            client.registerTeamAgent(card, agent);

            StepVerifier.create(client.send("a1", Msg.of(MsgRole.USER, "hello")))
                    .assertNext(msg -> assertThat(msg.text()).isEqualTo("response"))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Team-scoped access control")
    class TeamScoping {

        @Test
        @DisplayName("send to agent in another team fails")
        void sendToOtherTeamAgentFails() {
            // Register agent in a different team
            teamManager.create("other-team");
            AgentCard card = AgentCard.of("a2", "Agent 2", "desc");
            Agent agent = stubAgent("a2", Mono.just(Msg.of(MsgRole.ASSISTANT, "ok")));
            resolver.register(card);
            delegate.registerAgent(agent);
            teamManager.addAgent("other-team", agent);

            // validateTeamAgent throws synchronously before the Mono is subscribed
            assertThatThrownBy(() -> client.send("a2", Msg.of(MsgRole.USER, "hi")))
                    .isInstanceOf(A2aException.class)
                    .hasMessageContaining("not a member of team");
        }

        @Test
        @DisplayName("discoverTeamAgents filters by team membership")
        void discoverFiltersByTeam() {
            AgentCard card1 =
                    new AgentCard(
                            "a1", "Agent 1", "desc", "1.0", List.of("java"), false, null, null);
            AgentCard card2 =
                    new AgentCard(
                            "a2", "Agent 2", "desc", "1.0", List.of("java"), false, null, null);

            // a1 in test-team
            client.registerTeamAgent(
                    card1, stubAgent("a1", Mono.just(Msg.of(MsgRole.ASSISTANT, "ok"))));

            // a2 in other-team (registered in resolver but not in test-team)
            teamManager.create("other-team");
            resolver.register(card2);
            delegate.registerAgent(stubAgent("a2", Mono.just(Msg.of(MsgRole.ASSISTANT, "ok"))));
            teamManager.addAgent(
                    "other-team", stubAgent("a2", Mono.just(Msg.of(MsgRole.ASSISTANT, "ok"))));

            var results = client.discoverTeamAgents(Set.of("java"));
            assertThat(results).hasSize(1);
            assertThat(results.get(0).id()).isEqualTo("a1");
        }

        @Test
        @DisplayName("send to nonexistent team fails")
        void nonexistentTeamFails() {
            TeamAwareA2aClient orphanClient =
                    new TeamAwareA2aClient(delegate, resolver, teamManager, "no-such-team");

            assertThatThrownBy(() -> orphanClient.send("a1", Msg.of(MsgRole.USER, "hi")))
                    .isInstanceOf(A2aException.class)
                    .hasMessageContaining("Team not found");
        }
    }
}
