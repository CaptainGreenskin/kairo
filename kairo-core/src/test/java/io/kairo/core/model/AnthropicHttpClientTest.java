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
package io.kairo.core.model;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class AnthropicHttpClientTest {

    private MockWebServer server;
    private AnthropicHttpClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("/").toString();
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        HttpClient httpClient =
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();
        client = new AnthropicHttpClient(httpClient, baseUrl, "test-key");
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void buildHttpRequestSetsCorrectHeaders() {
        HttpRequest request = client.buildHttpRequest("{\"model\":\"test\"}");
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElse(null));
        assertEquals("test-key", request.headers().firstValue("x-api-key").orElse(null));
        assertEquals("2023-06-01", request.headers().firstValue("anthropic-version").orElse(null));
        assertTrue(request.uri().toString().endsWith("/v1/messages"));
    }

    @Test
    void sendRequestReturnsBodyOn200() throws InterruptedException {
        server.enqueue(new MockResponse().setBody("{\"id\":\"r\"}").setHeader("Content-Type", "application/json"));

        StepVerifier.create(client.sendRequest("{\"model\":\"test\"}"))
                .assertNext(body -> assertTrue(body.contains("\"id\":\"r\"")))
                .verifyComplete();

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
    }

    @Test
    void sendRequestThrowsRateLimitOn429() {
        server.enqueue(new MockResponse().setResponseCode(429)
                .setBody("Rate limited")
                .setHeader("retry-after", "30"));

        StepVerifier.create(client.sendRequest("{\"model\":\"test\"}"))
                .expectErrorMatches(e -> {
                    assertInstanceOf(ModelProviderException.RateLimitException.class, e);
                    ModelProviderException.RateLimitException rle =
                            (ModelProviderException.RateLimitException) e;
                    assertEquals(30L, rle.getRetryAfterSeconds());
                    return true;
                })
                .verify();
    }

    @Test
    void sendRequestThrowsApiExceptionOn500() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Error"));

        StepVerifier.create(client.sendRequest("{\"model\":\"test\"}"))
                .expectErrorMatches(e -> {
                    assertInstanceOf(ModelProviderException.ApiException.class, e);
                    assertTrue(e.getMessage().contains("server error"));
                    return true;
                })
                .verify();
    }

    @Test
    void sendRequestThrowsApiExceptionOn400() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("Bad Request"));

        StepVerifier.create(client.sendRequest("{\"model\":\"test\"}"))
                .expectErrorMatches(e -> {
                    assertInstanceOf(ModelProviderException.ApiException.class, e);
                    assertTrue(e.getMessage().contains("400"));
                    return true;
                })
                .verify();
    }

    @Test
    void defaultTimeoutIsReasonable() {
        assertTrue(AnthropicHttpClient.defaultTimeout().getSeconds() >= 60);
    }
}
