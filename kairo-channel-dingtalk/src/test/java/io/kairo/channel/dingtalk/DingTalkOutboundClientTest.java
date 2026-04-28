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
package io.kairo.channel.dingtalk;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.channel.ChannelFailureMode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class DingTalkOutboundClientTest {

    private MockWebServer server;
    private DingTalkOutboundClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = buildClient(server.url("/webhook").toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private DingTalkOutboundClient buildClient(String url) {
        return new DingTalkOutboundClient(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                mapper,
                url,
                null,
                Duration.ofSeconds(5));
    }

    private MockResponse jsonResponse(int status, int errcode, String errmsg) {
        String body = String.format("{\"errcode\":%d,\"errmsg\":\"%s\"}", errcode, errmsg);
        return new MockResponse()
                .setResponseCode(status)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private Map<String, Object> payload() {
        return Map.of("msgtype", "text", "text", Map.of("content", "hello"));
    }

    // --- Success ---

    @Test
    void success_errcode0_returnsOk() {
        server.enqueue(jsonResponse(200, 0, "ok"));

        StepVerifier.create(client.send(payload()))
                .assertNext(
                        ack -> {
                            assertThat(ack.success()).isTrue();
                            assertThat(ack.failureMode()).isNull();
                        })
                .verifyComplete();
    }

    @Test
    void success_requestBodyIsSentAsJson() throws Exception {
        server.enqueue(jsonResponse(200, 0, "ok"));

        client.send(payload()).block();

        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getHeader("Content-Type")).contains("application/json");
        assertThat(req.getBody().readUtf8()).contains("msgtype");
    }

    // --- Rate-limited via errcode ---

    @Test
    void rateLimitErrcode130101_returnsRateLimited() {
        server.enqueue(jsonResponse(200, 130101, "flow control"));

        StepVerifier.create(client.send(payload()))
                .assertNext(
                        ack -> {
                            assertThat(ack.success()).isFalse();
                            assertThat(ack.failureMode())
                                    .isEqualTo(ChannelFailureMode.RATE_LIMITED);
                        })
                .verifyComplete();
    }

    @Test
    void rateLimitErrcode130102_returnsRateLimited() {
        server.enqueue(jsonResponse(200, 130102, "flow control"));

        StepVerifier.create(client.send(payload()))
                .assertNext(
                        ack ->
                                assertThat(ack.failureMode())
                                        .isEqualTo(ChannelFailureMode.RATE_LIMITED))
                .verifyComplete();
    }

    @Test
    void rateLimitErrcode130103_returnsRateLimited() {
        server.enqueue(jsonResponse(200, 130103, "global throttle"));

        StepVerifier.create(client.send(payload()))
                .assertNext(
                        ack ->
                                assertThat(ack.failureMode())
                                        .isEqualTo(ChannelFailureMode.RATE_LIMITED))
                .verifyComplete();
    }

    // --- Rate-limited via HTTP 429 ---

    @Test
    void http429_returnsRateLimited() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("rate limited"));

        StepVerifier.create(client.send(payload()))
                .assertNext(
                        ack ->
                                assertThat(ack.failureMode())
                                        .isEqualTo(ChannelFailureMode.RATE_LIMITED))
                .verifyComplete();
    }

    // --- Rejected ---

    @Test
    void errcode400001_returnsRejected() {
        server.enqueue(jsonResponse(200, 400001, "invalid keyword"));

        StepVerifier.create(client.send(payload()))
                .assertNext(
                        ack -> {
                            assertThat(ack.success()).isFalse();
                            assertThat(ack.failureMode()).isEqualTo(ChannelFailureMode.REJECTED);
                        })
                .verifyComplete();
    }

    @Test
    void errcode310000_returnsRejected() {
        server.enqueue(jsonResponse(200, 310000, "message blocked"));

        StepVerifier.create(client.send(payload()))
                .assertNext(
                        ack -> assertThat(ack.failureMode()).isEqualTo(ChannelFailureMode.REJECTED))
                .verifyComplete();
    }

    // --- Delivery failed ---

    @Test
    void http500_returnsDeliveryFailed() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("server error"));

        StepVerifier.create(client.send(payload()))
                .assertNext(
                        ack ->
                                assertThat(ack.failureMode())
                                        .isEqualTo(ChannelFailureMode.DELIVERY_FAILED))
                .verifyComplete();
    }

    @Test
    void http403_returnsDeliveryFailed() {
        server.enqueue(new MockResponse().setResponseCode(403).setBody("forbidden"));

        StepVerifier.create(client.send(payload()))
                .assertNext(
                        ack ->
                                assertThat(ack.failureMode())
                                        .isEqualTo(ChannelFailureMode.DELIVERY_FAILED))
                .verifyComplete();
    }

    @Test
    void http400_returnsDeliveryFailed() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("bad request"));

        StepVerifier.create(client.send(payload()))
                .assertNext(
                        ack ->
                                assertThat(ack.failureMode())
                                        .isEqualTo(ChannelFailureMode.DELIVERY_FAILED))
                .verifyComplete();
    }

    // --- Transport failure ---

    @Test
    void sendFailed_whenServerUnreachable() {
        DingTalkOutboundClient unreachableClient =
                new DingTalkOutboundClient(
                        HttpClient.newBuilder().connectTimeout(Duration.ofMillis(200)).build(),
                        mapper,
                        "http://192.0.2.1/webhook", // TEST-NET, guaranteed unreachable
                        null,
                        Duration.ofMillis(200));

        StepVerifier.create(unreachableClient.send(payload()))
                .assertNext(
                        ack -> {
                            assertThat(ack.success()).isFalse();
                            assertThat(ack.failureMode()).isEqualTo(ChannelFailureMode.SEND_FAILED);
                        })
                .verifyComplete();
    }

    // --- Unparseable body ---

    @Test
    void malformedJsonBody_returnsUnknown() {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Type", "application/json")
                        .setBody("not-json{{{"));

        StepVerifier.create(client.send(payload()))
                .assertNext(
                        ack -> {
                            assertThat(ack.success()).isFalse();
                            assertThat(ack.failureMode()).isEqualTo(ChannelFailureMode.UNKNOWN);
                        })
                .verifyComplete();
    }

    // --- Signed URL ---

    @Test
    void withSigner_requestUrlContainsTimestampAndSign() throws Exception {
        server.enqueue(jsonResponse(200, 0, "ok"));

        DingTalkSignatureVerifier signer = new DingTalkSignatureVerifier("secret-key");
        DingTalkOutboundClient signedClient =
                new DingTalkOutboundClient(
                        HttpClient.newBuilder().build(),
                        mapper,
                        server.url("/signed-webhook").toString(),
                        signer,
                        Duration.ofSeconds(5));

        signedClient.send(payload()).block();

        RecordedRequest req = server.takeRequest();
        String path = req.getPath();
        assertThat(path).contains("timestamp=");
        assertThat(path).contains("sign=");
    }
}
