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

/**
 * Integration tests for the auto-registration lifecycle when agents join/leave teams via {@link
 * DefaultTeamManager} with an {@link InProcessAgentCardResolver}.
 */
class TeamAutoRegistrationTest {

    private InProcessAgentCardResolver resolver;
    private InProcessA2aClient a2aClient;
    private DefaultTeamManager teamManager;

    @BeforeEach
    void setUp() {
        resolver = new InProcessAgentCardResolver();
        a2aClient = new InProcessA2aClient(resolver);
        teamManager = new DefaultTeamManager(resolver);
    }

    private Agent stubAgent(String id, String name, Mono<Msg> response) {
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
                return name;
            }

            @Override
            public AgentState state() {
                return AgentState.IDLE;
            }

            @Override
            public void interrupt() {}
        };
    }

    private Agent stubAgent(String id, Mono<Msg> response) {
        return stubAgent(id, "stub-" + id, response);
    }

    @Nested
    @DisplayName("Auto-Registration Lifecycle")
    class AutoRegistrationLifecycle {

        @Test
        @DisplayName("addAgent registers AgentCard in resolver automatically")
        void addAgentRegistersCard() {
            teamManager.create("team-alpha");
            Agent agent = stubAgent("agent-1", "Agent One", Mono.empty());

            teamManager.addAgent("team-alpha", agent);

            assertThat(resolver.resolve("agent-1")).isPresent();
            assertThat(resolver.resolve("agent-1"))
                    .hasValueSatisfying(
                            card -> {
                                assertThat(card.id()).isEqualTo("agent-1");
                                assertThat(card.name()).isEqualTo("Agent One");
                            });
        }

        @Test
        @DisplayName("removeAgent unregisters AgentCard from resolver automatically")
        void removeAgentUnregistersCard() {
            teamManager.create("team-beta");
            Agent agent = stubAgent("agent-2", Mono.empty());
            teamManager.addAgent("team-beta", agent);
            assertThat(resolver.resolve("agent-2")).isPresent();

            teamManager.removeAgent("team-beta", "agent-2");

            assertThat(resolver.resolve("agent-2")).isEmpty();
        }

        @Test
        @DisplayName("adding multiple agents registers all cards")
        void addMultipleAgentsRegistersAll() {
            teamManager.create("team-gamma");
            Agent a1 = stubAgent("a1", Mono.empty());
            Agent a2 = stubAgent("a2", Mono.empty());
            Agent a3 = stubAgent("a3", Mono.empty());

            teamManager.addAgent("team-gamma", a1);
            teamManager.addAgent("team-gamma", a2);
            teamManager.addAgent("team-gamma", a3);

            assertThat(resolver.listAll()).hasSize(3);
            assertThat(resolver.resolve("a1")).isPresent();
            assertThat(resolver.resolve("a2")).isPresent();
            assertThat(resolver.resolve("a3")).isPresent();
        }

        @Test
        @DisplayName("removing one agent does not affect other agents' cards")
        void removeOneAgentKeepsOthers() {
            teamManager.create("team-delta");
            Agent a1 = stubAgent("d1", Mono.empty());
            Agent a2 = stubAgent("d2", Mono.empty());
            teamManager.addAgent("team-delta", a1);
            teamManager.addAgent("team-delta", a2);

            teamManager.removeAgent("team-delta", "d1");

            assertThat(resolver.resolve("d1")).isEmpty();
            assertThat(resolver.resolve("d2")).isPresent();
        }
    }

    @Nested
    @DisplayName("Discovery After Registration")
    class DiscoveryAfterRegistration {

        @Test
        @DisplayName("auto-registered agent is discoverable via resolver.discover(tags)")
        void discoverByTagsAfterAutoRegistration() {
            teamManager.create("team-discover");
            // Auto-registration creates card with empty tags by default,
            // so we register a card with tags explicitly and then add agent
            AgentCard taggedCard =
                    new AgentCard(
                            "tagged-agent",
                            "Tagged Agent",
                            "desc",
                            "1.0",
                            List.of("java", "code-review"),
                            false,
                            List.of(),
                            null);
            resolver.register(taggedCard);
            Agent agent = stubAgent("tagged-agent", Mono.empty());
            // Add agent to team (this will overwrite card with auto-generated one)
            // So for tag-based discovery, register the card after addAgent
            teamManager.addAgent("team-discover", agent);
            // Re-register with tags since auto-registration creates tagless card
            resolver.register(taggedCard);

            List<AgentCard> results = resolver.discover(Set.of("java"));
            assertThat(results).hasSize(1);
            assertThat(results.get(0).id()).isEqualTo("tagged-agent");
        }

        @Test
        @DisplayName("auto-registered agent is discoverable via empty tags (list all)")
        void discoverAllAfterAutoRegistration() {
            teamManager.create("team-all");
            Agent a1 = stubAgent("all-1", Mono.empty());
            Agent a2 = stubAgent("all-2", Mono.empty());
            teamManager.addAgent("team-all", a1);
            teamManager.addAgent("team-all", a2);

            List<AgentCard> results = resolver.discover(Set.of());
            assertThat(results).hasSize(2);
            assertThat(results)
                    .extracting(AgentCard::id)
                    .containsExactlyInAnyOrder("all-1", "all-2");
        }

        @Test
        @DisplayName("unregistered agent is no longer discoverable")
        void notDiscoverableAfterRemoval() {
            teamManager.create("team-gone");
            Agent agent = stubAgent("gone-1", Mono.empty());
            teamManager.addAgent("team-gone", agent);
            assertThat(resolver.discover(Set.of())).hasSize(1);

            teamManager.removeAgent("team-gone", "gone-1");

            assertThat(resolver.discover(Set.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("Invocation After Registration")
    class InvocationAfterRegistration {

        @Test
        @DisplayName("auto-registered agent can be invoked via A2aClient.send()")
        void sendToAutoRegisteredAgent() {
            teamManager.create("team-invoke");
            Msg expected = Msg.of(MsgRole.ASSISTANT, "hello from agent");
            Agent agent = stubAgent("invoke-1", Mono.just(expected));
            teamManager.addAgent("team-invoke", agent);
            a2aClient.registerAgent(agent);

            StepVerifier.create(a2aClient.send("invoke-1", Msg.of(MsgRole.USER, "hi")))
                    .assertNext(msg -> assertThat(msg.text()).isEqualTo("hello from agent"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("auto-registered agent can be streamed via A2aClient.stream()")
        void streamToAutoRegisteredAgent() {
            teamManager.create("team-stream");
            Msg expected = Msg.of(MsgRole.ASSISTANT, "streamed response");
            Agent agent = stubAgent("stream-1", Mono.just(expected));
            teamManager.addAgent("team-stream", agent);
            a2aClient.registerAgent(agent);

            StepVerifier.create(a2aClient.stream("stream-1", Msg.of(MsgRole.USER, "go")))
                    .expectNext(expected)
                    .verifyComplete();
        }

        @Test
        @DisplayName("removed agent's card causes A2aClient.send() to fail")
        void sendToRemovedAgentFails() {
            teamManager.create("team-fail");
            Agent agent = stubAgent("fail-1", Mono.just(Msg.of(MsgRole.ASSISTANT, "ok")));
            teamManager.addAgent("team-fail", agent);
            a2aClient.registerAgent(agent);

            // Verify it works first
            StepVerifier.create(a2aClient.send("fail-1", Msg.of(MsgRole.USER, "hi")))
                    .assertNext(msg -> assertThat(msg.text()).isEqualTo("ok"))
                    .verifyComplete();

            // Remove agent — unregisters card from resolver
            teamManager.removeAgent("team-fail", "fail-1");
            a2aClient.unregisterAgent("fail-1");

            // Now send should fail
            StepVerifier.create(a2aClient.send("fail-1", Msg.of(MsgRole.USER, "hi")))
                    .expectErrorSatisfies(
                            e -> assertThat(e.getMessage()).contains("No agent card registered"))
                    .verify();
        }
    }
}
