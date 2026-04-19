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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.a2a.A2aException;
import io.kairo.api.a2a.AgentCard;
import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentState;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Tests for {@link InProcessA2aClient}. */
class InProcessA2aClientTest {

    private InProcessAgentCardResolver resolver;
    private InProcessA2aClient client;

    @BeforeEach
    void setUp() {
        resolver = new InProcessAgentCardResolver();
        client = new InProcessA2aClient(resolver);
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

    private void registerAgent(String id, boolean streaming, Mono<Msg> response) {
        AgentCard card =
                new AgentCard(id, "Agent " + id, "desc", "1.0", null, streaming, null, null);
        resolver.register(card);
        client.registerAgent(stubAgent(id, response));
    }

    @Nested
    @DisplayName("send() — request-response")
    class Send {

        @Test
        @DisplayName("send to registered agent returns response")
        void sendReturnsResponse() {
            Msg expected = Msg.of(MsgRole.ASSISTANT, "hello back");
            registerAgent("target", false, Mono.just(expected));

            Msg result = client.send("target", Msg.of(MsgRole.USER, "hello")).block();

            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("send to unknown agent emits A2aException")
        void sendUnknownThrows() {
            StepVerifier.create(client.send("unknown", Msg.of(MsgRole.USER, "hi")))
                    .expectErrorSatisfies(
                            e -> {
                                assertThat(e).isInstanceOf(A2aException.class);
                                A2aException a2a = (A2aException) e;
                                assertThat(a2a.targetAgentId()).isEqualTo("unknown");
                                assertThat(a2a.getMessage()).contains("No agent card registered");
                            })
                    .verify();
        }

        @Test
        @DisplayName("send to agent with card but no instance emits A2aException")
        void sendCardOnlyNoInstance() {
            resolver.register(AgentCard.of("ghost", "Ghost", "No instance"));

            StepVerifier.create(client.send("ghost", Msg.of(MsgRole.USER, "hi")))
                    .expectErrorSatisfies(
                            e -> {
                                assertThat(e).isInstanceOf(A2aException.class);
                                assertThat(e.getMessage()).contains("no Agent instance registered");
                            })
                    .verify();
        }

        @Test
        @DisplayName("send propagates agent error as A2aException")
        void sendErrorWrapped() {
            registerAgent("failing", false, Mono.error(new RuntimeException("boom")));

            StepVerifier.create(client.send("failing", Msg.of(MsgRole.USER, "hi")))
                    .expectErrorSatisfies(
                            e -> {
                                assertThat(e).isInstanceOf(A2aException.class);
                                A2aException a2a = (A2aException) e;
                                assertThat(a2a.targetAgentId()).isEqualTo("failing");
                                assertThat(a2a.getMessage()).contains("boom");
                            })
                    .verify();
        }
    }

    @Nested
    @DisplayName("stream() — streaming response")
    class Stream {

        @Test
        @DisplayName("stream from non-streaming agent wraps single response")
        void streamNonStreaming() {
            Msg response = Msg.of(MsgRole.ASSISTANT, "result");
            registerAgent("non-stream", false, Mono.just(response));

            StepVerifier.create(client.stream("non-stream", Msg.of(MsgRole.USER, "go")))
                    .expectNext(response)
                    .verifyComplete();
        }

        @Test
        @DisplayName("stream from streaming-capable agent")
        void streamStreaming() {
            Msg response = Msg.of(MsgRole.ASSISTANT, "streamed");
            registerAgent("streamer", true, Mono.just(response));

            StepVerifier.create(client.stream("streamer", Msg.of(MsgRole.USER, "go")))
                    .expectNext(response)
                    .verifyComplete();
        }

        @Test
        @DisplayName("stream to unknown agent emits A2aException")
        void streamUnknownThrows() {
            StepVerifier.create(client.stream("unknown", Msg.of(MsgRole.USER, "hi")))
                    .expectErrorSatisfies(
                            e -> {
                                assertThat(e).isInstanceOf(A2aException.class);
                                assertThat(e.getMessage()).contains("No agent card registered");
                            })
                    .verify();
        }
    }

    @Nested
    @DisplayName("Agent registration lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("unregisterAgent removes agent from client")
        void unregisterRemovesAgent() {
            registerAgent("removeme", false, Mono.just(Msg.of(MsgRole.ASSISTANT, "ok")));
            client.unregisterAgent("removeme");

            StepVerifier.create(client.send("removeme", Msg.of(MsgRole.USER, "hi")))
                    .expectErrorSatisfies(
                            e -> {
                                assertThat(e).isInstanceOf(A2aException.class);
                                assertThat(e.getMessage()).contains("no Agent instance registered");
                            })
                    .verify();
        }

        @Test
        @DisplayName("register null agent throws NPE")
        void registerNullThrows() {
            assertThatThrownBy(() -> client.registerAgent(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("constructor with null resolver throws NPE")
        void nullResolverThrows() {
            assertThatThrownBy(() -> new InProcessA2aClient(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
