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
package io.kairo.core.a2a.remote;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.a2a.AgentCard;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.core.a2a.InProcessA2aClient;
import io.kairo.core.a2a.InProcessAgentCardResolver;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

final class A2aEndpointHandlerTest {

    @Test
    void handleSendDispatchesToLocalAgent() {
        InProcessAgentCardResolver resolver = new InProcessAgentCardResolver();
        resolver.register(AgentCard.of("echo", "Echo Agent", "Echoes input"));
        InProcessA2aClient localClient = new InProcessA2aClient(resolver);
        localClient.registerAgent(new EchoAgent());

        A2aEndpointHandler handler = new A2aEndpointHandler(localClient);

        Msg input = Msg.of(MsgRole.USER, "ping");
        A2aRequest request = new A2aRequest("echo", input, false);

        StepVerifier.create(handler.handleSend(request.toJson(), null))
                .assertNext(
                        json -> {
                            A2aResponse resp = A2aResponse.fromJson(json);
                            assertThat(resp.isSuccess()).isTrue();
                            assertThat(resp.message().text()).isEqualTo("echo: ping");
                        })
                .verifyComplete();
    }

    @Test
    void handleSendUnauthorized() {
        InProcessAgentCardResolver resolver = new InProcessAgentCardResolver();
        InProcessA2aClient localClient = new InProcessA2aClient(resolver);

        A2aEndpointHandler handler =
                new A2aEndpointHandler(localClient, token -> "valid-token".equals(token));

        Msg input = Msg.of(MsgRole.USER, "ping");
        A2aRequest request = new A2aRequest("echo", input, false);

        StepVerifier.create(handler.handleSend(request.toJson(), "bad-token"))
                .assertNext(
                        json -> {
                            A2aResponse resp = A2aResponse.fromJson(json);
                            assertThat(resp.isSuccess()).isFalse();
                            assertThat(resp.errorCode()).isEqualTo("UNAUTHORIZED");
                        })
                .verifyComplete();
    }

    @Test
    void handleSendMissingTokenWhenRequired() {
        InProcessAgentCardResolver resolver = new InProcessAgentCardResolver();
        InProcessA2aClient localClient = new InProcessA2aClient(resolver);

        A2aEndpointHandler handler =
                new A2aEndpointHandler(localClient, token -> "valid-token".equals(token));

        Msg input = Msg.of(MsgRole.USER, "ping");
        A2aRequest request = new A2aRequest("echo", input, false);

        StepVerifier.create(handler.handleSend(request.toJson(), null))
                .assertNext(
                        json -> {
                            A2aResponse resp = A2aResponse.fromJson(json);
                            assertThat(resp.isSuccess()).isFalse();
                            assertThat(resp.errorCode()).isEqualTo("UNAUTHORIZED");
                        })
                .verifyComplete();
    }

    @Test
    void handleSendAgentNotFound() {
        InProcessAgentCardResolver resolver = new InProcessAgentCardResolver();
        InProcessA2aClient localClient = new InProcessA2aClient(resolver);

        A2aEndpointHandler handler = new A2aEndpointHandler(localClient);

        Msg input = Msg.of(MsgRole.USER, "ping");
        A2aRequest request = new A2aRequest("nonexistent", input, false);

        StepVerifier.create(handler.handleSend(request.toJson(), null))
                .assertNext(
                        json -> {
                            A2aResponse resp = A2aResponse.fromJson(json);
                            assertThat(resp.isSuccess()).isFalse();
                            assertThat(resp.errorCode()).isEqualTo("AGENT_ERROR");
                        })
                .verifyComplete();
    }

    @Test
    void handleSendBadRequest() {
        InProcessAgentCardResolver resolver = new InProcessAgentCardResolver();
        InProcessA2aClient localClient = new InProcessA2aClient(resolver);
        A2aEndpointHandler handler = new A2aEndpointHandler(localClient);

        StepVerifier.create(handler.handleSend("not json", null))
                .assertNext(
                        json -> {
                            A2aResponse resp = A2aResponse.fromJson(json);
                            assertThat(resp.isSuccess()).isFalse();
                            assertThat(resp.errorCode()).isEqualTo("BAD_REQUEST");
                        })
                .verifyComplete();
    }

    @Test
    void handleStreamDispatchesToLocalAgent() {
        InProcessAgentCardResolver resolver = new InProcessAgentCardResolver();
        resolver.register(AgentCard.of("echo", "Echo Agent", "Echoes input"));
        InProcessA2aClient localClient = new InProcessA2aClient(resolver);
        localClient.registerAgent(new EchoAgent());

        A2aEndpointHandler handler = new A2aEndpointHandler(localClient);

        Msg input = Msg.of(MsgRole.USER, "stream me");
        A2aRequest request = new A2aRequest("echo", input, true);

        StepVerifier.create(handler.handleStream(request.toJson(), null))
                .assertNext(
                        line -> {
                            assertThat(line).startsWith("data: ");
                            String json = line.substring(6).trim();
                            A2aResponse resp = A2aResponse.fromJson(json);
                            assertThat(resp.isSuccess()).isTrue();
                            assertThat(resp.message().text()).isEqualTo("echo: stream me");
                        })
                .verifyComplete();
    }

    @Test
    void healthCheckReturnsOk() {
        InProcessAgentCardResolver resolver = new InProcessAgentCardResolver();
        InProcessA2aClient localClient = new InProcessA2aClient(resolver);
        A2aEndpointHandler handler = new A2aEndpointHandler(localClient);

        String health = handler.healthCheck();
        assertThat(health).contains("\"status\":\"ok\"");
        assertThat(health).contains("\"protocol\":\"a2a\"");
    }

    @Test
    void noTokenValidatorAllowsAllRequests() {
        InProcessAgentCardResolver resolver = new InProcessAgentCardResolver();
        resolver.register(AgentCard.of("echo", "Echo Agent", "Echoes input"));
        InProcessA2aClient localClient = new InProcessA2aClient(resolver);
        localClient.registerAgent(new EchoAgent());

        A2aEndpointHandler handler = new A2aEndpointHandler(localClient);

        Msg input = Msg.of(MsgRole.USER, "no auth needed");
        A2aRequest request = new A2aRequest("echo", input, false);

        StepVerifier.create(handler.handleSend(request.toJson(), null))
                .assertNext(
                        json -> {
                            A2aResponse resp = A2aResponse.fromJson(json);
                            assertThat(resp.isSuccess()).isTrue();
                        })
                .verifyComplete();
    }

    private static final class EchoAgent implements io.kairo.api.agent.Agent {
        @Override
        public reactor.core.publisher.Mono<Msg> call(Msg input) {
            return reactor.core.publisher.Mono.just(
                    Msg.of(MsgRole.ASSISTANT, "echo: " + input.text()));
        }

        @Override
        public String id() {
            return "echo";
        }

        @Override
        public String name() {
            return "Echo Agent";
        }

        @Override
        public io.kairo.api.agent.AgentState state() {
            return io.kairo.api.agent.AgentState.IDLE;
        }

        @Override
        public void interrupt() {}
    }
}
