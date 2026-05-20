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

import io.kairo.api.a2a.A2aException;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

final class HttpA2aClientTest {

    private MockWebServer server;
    private HttpA2aClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        client =
                HttpA2aClient.builder()
                        .baseUrl(server.url("/").toString())
                        .bearerToken("test-token")
                        .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void sendSuccessful() throws InterruptedException {
        Msg responseMsg = Msg.of(MsgRole.ASSISTANT, "response from remote");
        A2aResponse response = A2aResponse.ok(responseMsg);
        server.enqueue(
                new MockResponse()
                        .setBody(response.toJson())
                        .addHeader("Content-Type", "application/json"));

        Msg input = Msg.of(MsgRole.USER, "hello");

        StepVerifier.create(client.send("remote-agent", input))
                .assertNext(
                        msg -> {
                            assertThat(msg.role()).isEqualTo(MsgRole.ASSISTANT);
                            assertThat(msg.text()).isEqualTo("response from remote");
                        })
                .verifyComplete();

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getPath()).isEqualTo("/a2a/send");
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer test-token");
        assertThat(recorded.getHeader("Content-Type")).isEqualTo("application/json");
    }

    @Test
    void sendRemoteError() {
        A2aResponse errorResp = A2aResponse.error("AGENT_NOT_FOUND", "No such agent");
        server.enqueue(
                new MockResponse()
                        .setBody(errorResp.toJson())
                        .addHeader("Content-Type", "application/json"));

        Msg input = Msg.of(MsgRole.USER, "hello");

        StepVerifier.create(client.send("missing-agent", input))
                .expectErrorMatches(
                        e ->
                                e instanceof A2aException
                                        && e.getMessage().contains("AGENT_NOT_FOUND"))
                .verify();
    }

    @Test
    void sendHttp401() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("Unauthorized"));

        Msg input = Msg.of(MsgRole.USER, "hello");

        StepVerifier.create(client.send("agent-1", input))
                .expectErrorMatches(
                        e ->
                                e instanceof A2aException
                                        && e.getMessage().contains("Authentication failed"))
                .verify();
    }

    @Test
    void sendHttp500() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

        Msg input = Msg.of(MsgRole.USER, "hello");

        StepVerifier.create(client.send("agent-1", input))
                .expectErrorMatches(
                        e -> e instanceof A2aException && e.getMessage().contains("HTTP 500"))
                .verify();
    }

    @Test
    void streamSuccessful() {
        Msg msg1 = Msg.of(MsgRole.ASSISTANT, "chunk 1");
        Msg msg2 = Msg.of(MsgRole.ASSISTANT, "chunk 2");
        String body =
                "data: "
                        + A2aResponse.ok(msg1).toJson()
                        + "\n"
                        + "data: "
                        + A2aResponse.ok(msg2).toJson()
                        + "\n";

        server.enqueue(
                new MockResponse().setBody(body).addHeader("Content-Type", "text/event-stream"));

        Msg input = Msg.of(MsgRole.USER, "stream me");

        StepVerifier.create(client.stream("agent-1", input))
                .assertNext(msg -> assertThat(msg.text()).isEqualTo("chunk 1"))
                .assertNext(msg -> assertThat(msg.text()).isEqualTo("chunk 2"))
                .verifyComplete();
    }

    @Test
    void baseUrlTrailingSlashStripped() {
        HttpA2aClient slashClient =
                HttpA2aClient.builder().baseUrl("http://localhost:9999/").build();
        assertThat(slashClient.baseUrl()).isEqualTo("http://localhost:9999");
    }

    @Test
    void noTokenOmitsAuthHeader() throws InterruptedException {
        HttpA2aClient noAuthClient =
                HttpA2aClient.builder().baseUrl(server.url("/").toString()).build();

        Msg responseMsg = Msg.of(MsgRole.ASSISTANT, "ok");
        server.enqueue(
                new MockResponse()
                        .setBody(A2aResponse.ok(responseMsg).toJson())
                        .addHeader("Content-Type", "application/json"));

        StepVerifier.create(noAuthClient.send("agent-1", Msg.of(MsgRole.USER, "hi")))
                .assertNext(msg -> assertThat(msg.text()).isEqualTo("ok"))
                .verifyComplete();

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getHeader("Authorization")).isNull();
    }
}
