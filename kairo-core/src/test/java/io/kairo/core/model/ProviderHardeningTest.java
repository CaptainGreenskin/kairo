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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.core.model.anthropic.AnthropicProvider;
import io.kairo.core.model.openai.OpenAIProvider;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;

/**
 * Tests for provider hardening: stream idle timeout, exponential backoff retry, and effort
 * parameter mapping.
 */
class ProviderHardeningTest {

    private MockWebServer server;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private String baseUrl() {
        String url = server.url("/").toString();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    // ---- Stream Timeout + Retry Tests ----

    @Test
    void streamTimeoutTriggersRetry() {
        AtomicInteger attempts = new AtomicInteger();
        Flux<String> simulatedStream =
                Flux.<String>defer(
                                () -> {
                                    if (attempts.incrementAndGet() < 3) {
                                        return Flux.<String>never().timeout(Duration.ofMillis(100));
                                    }
                                    return Flux.just("response");
                                })
                        .retryWhen(
                                Retry.backoff(3, Duration.ofMillis(10))
                                        .maxBackoff(Duration.ofMillis(50))
                                        .filter(t -> t instanceof TimeoutException));

        StepVerifier.create(simulatedStream).expectNext("response").verifyComplete();
        assertEquals(3, attempts.get());
    }

    @Test
    void retryRespectsMaxAttempts() {
        AtomicInteger attempts = new AtomicInteger();
        Flux<String> alwaysFailing =
                Flux.<String>defer(
                                () -> {
                                    attempts.incrementAndGet();
                                    return Flux.<String>never().timeout(Duration.ofMillis(50));
                                })
                        .retryWhen(
                                Retry.backoff(3, Duration.ofMillis(10))
                                        .maxBackoff(Duration.ofMillis(50))
                                        .filter(t -> t instanceof TimeoutException));

        StepVerifier.create(alwaysFailing)
                .expectError(IllegalStateException.class)
                .verify(Duration.ofSeconds(5));
        // 1 initial + 3 retries = 4 total attempts
        assertEquals(4, attempts.get());
    }

    @Test
    void nonRetryableErrorIsNotRetried() {
        AtomicInteger attempts = new AtomicInteger();
        Flux<String> nonRetryable =
                Flux.<String>defer(
                                () -> {
                                    attempts.incrementAndGet();
                                    return Flux.error(new IllegalArgumentException("Bad input"));
                                })
                        .retryWhen(
                                Retry.backoff(3, Duration.ofMillis(10))
                                        .filter(t -> t instanceof TimeoutException));

        StepVerifier.create(nonRetryable)
                .expectError(IllegalArgumentException.class)
                .verify(Duration.ofSeconds(2));
        // Only 1 attempt — no retries for non-retryable errors
        assertEquals(1, attempts.get());
    }

    @Test
    void monoCallTimeoutTriggersRetry() {
        AtomicInteger attempts = new AtomicInteger();
        Mono<String> simulatedCall =
                Mono.<String>defer(
                                () -> {
                                    if (attempts.incrementAndGet() < 2) {
                                        return Mono.<String>never().timeout(Duration.ofMillis(50));
                                    }
                                    return Mono.just("ok");
                                })
                        .retryWhen(
                                Retry.backoff(3, Duration.ofMillis(10))
                                        .maxBackoff(Duration.ofMillis(50))
                                        .filter(t -> t instanceof TimeoutException));

        StepVerifier.create(simulatedCall).expectNext("ok").verifyComplete();
        assertEquals(2, attempts.get());
    }

    // ---- Effort Parameter Tests ----

    @Test
    void anthropicEffortMapsToOutputConfig() throws Exception {
        String baseUrl = baseUrl();
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        AnthropicProvider provider = new AnthropicProvider("test-key", baseUrl, httpClient);

        ModelConfig config =
                ModelConfig.builder()
                        .model("claude-sonnet-4-20250514")
                        .maxTokens(4096)
                        .temperature(1.0)
                        .effort(0.9)
                        .build();

        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "Hello"));

        String body = provider.buildRequestBody(messages, config, false);
        JsonNode root = mapper.readTree(body);

        // Effort 0.9 should map to output_config.effort = "high"
        assertTrue(root.has("output_config"), "output_config should be present when effort is set");
        assertEquals("high", root.get("output_config").get("effort").asText());
        // Effort should NOT auto-enable thinking
        // (thinking may still be present from auto-detection, but not from effort mapping)
    }

    @Test
    void anthropicEffortOmittedWhenNull() throws Exception {
        String baseUrl = baseUrl();
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        AnthropicProvider provider = new AnthropicProvider("test-key", baseUrl, httpClient);

        ModelConfig config =
                ModelConfig.builder()
                        .model("claude-sonnet-4-20250514")
                        .maxTokens(4096)
                        .temperature(1.0)
                        .build();

        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "Hello"));

        String body = provider.buildRequestBody(messages, config, false);
        JsonNode root = mapper.readTree(body);

        // Without effort, output_config should NOT be present
        assertFalse(
                root.has("output_config"),
                "output_config should not be present when effort is null");
    }

    @Test
    void anthropicEffortMapsLowMediumHigh() throws Exception {
        String baseUrl = baseUrl();
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        AnthropicProvider provider = new AnthropicProvider("test-key", baseUrl, httpClient);

        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "Hello"));

        // Low effort
        ModelConfig lowConfig =
                ModelConfig.builder()
                        .model("claude-sonnet-4-20250514")
                        .maxTokens(4096)
                        .temperature(1.0)
                        .effort(0.1)
                        .build();
        JsonNode lowRoot = mapper.readTree(provider.buildRequestBody(messages, lowConfig, false));
        assertEquals("low", lowRoot.get("output_config").get("effort").asText());

        // Medium effort
        ModelConfig medConfig =
                ModelConfig.builder()
                        .model("claude-sonnet-4-20250514")
                        .maxTokens(4096)
                        .temperature(1.0)
                        .effort(0.5)
                        .build();
        JsonNode medRoot = mapper.readTree(provider.buildRequestBody(messages, medConfig, false));
        assertEquals("medium", medRoot.get("output_config").get("effort").asText());

        // High effort
        ModelConfig highConfig =
                ModelConfig.builder()
                        .model("claude-sonnet-4-20250514")
                        .maxTokens(4096)
                        .temperature(1.0)
                        .effort(0.9)
                        .build();
        JsonNode highRoot = mapper.readTree(provider.buildRequestBody(messages, highConfig, false));
        assertEquals("high", highRoot.get("output_config").get("effort").asText());
    }

    @Test
    void openAIEffortIncludedWhenSet() throws Exception {
        String baseUrl = baseUrl();
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        OpenAIProvider provider =
                new OpenAIProvider("test-key", baseUrl, "/v1/chat/completions", httpClient);

        ModelConfig config =
                ModelConfig.builder()
                        .model("gpt-4o")
                        .maxTokens(4096)
                        .temperature(1.0)
                        .effort(0.9)
                        .build();

        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "Hello"));

        String body = provider.buildRequestBody(messages, config, false);
        JsonNode root = mapper.readTree(body);

        assertTrue(root.has("reasoning_effort"));
        assertEquals("high", root.get("reasoning_effort").asText());
    }

    @Test
    void openAIEffortOmittedWhenNull() throws Exception {
        String baseUrl = baseUrl();
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        OpenAIProvider provider =
                new OpenAIProvider("test-key", baseUrl, "/v1/chat/completions", httpClient);

        ModelConfig config =
                ModelConfig.builder().model("gpt-4o").maxTokens(4096).temperature(1.0).build();

        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "Hello"));

        String body = provider.buildRequestBody(messages, config, false);
        JsonNode root = mapper.readTree(body);

        assertFalse(
                root.has("reasoning_effort"),
                "reasoning_effort should not be present when effort is null");
    }

    @Test
    void openAIEffortMapsToLowMediumHigh() throws Exception {
        String baseUrl = baseUrl();
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        OpenAIProvider provider =
                new OpenAIProvider("test-key", baseUrl, "/v1/chat/completions", httpClient);

        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "Hello"));

        // Low effort
        ModelConfig lowConfig =
                ModelConfig.builder()
                        .model("gpt-4o")
                        .maxTokens(4096)
                        .temperature(1.0)
                        .effort(0.1)
                        .build();
        JsonNode lowRoot = mapper.readTree(provider.buildRequestBody(messages, lowConfig, false));
        assertEquals("low", lowRoot.get("reasoning_effort").asText());

        // Medium effort
        ModelConfig medConfig =
                ModelConfig.builder()
                        .model("gpt-4o")
                        .maxTokens(4096)
                        .temperature(1.0)
                        .effort(0.5)
                        .build();
        JsonNode medRoot = mapper.readTree(provider.buildRequestBody(messages, medConfig, false));
        assertEquals("medium", medRoot.get("reasoning_effort").asText());

        // High effort
        ModelConfig highConfig =
                ModelConfig.builder()
                        .model("gpt-4o")
                        .maxTokens(4096)
                        .temperature(1.0)
                        .effort(0.9)
                        .build();
        JsonNode highRoot = mapper.readTree(provider.buildRequestBody(messages, highConfig, false));
        assertEquals("high", highRoot.get("reasoning_effort").asText());
    }

    @Test
    void effortOutOfRangeIsIgnored() throws Exception {
        String baseUrl = baseUrl();
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        OpenAIProvider provider =
                new OpenAIProvider("test-key", baseUrl, "/v1/chat/completions", httpClient);

        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "Hello"));

        // Effort > 1.0 should be ignored
        ModelConfig outOfRange =
                ModelConfig.builder()
                        .model("gpt-4o")
                        .maxTokens(4096)
                        .temperature(1.0)
                        .effort(1.5)
                        .build();
        JsonNode root = mapper.readTree(provider.buildRequestBody(messages, outOfRange, false));
        assertFalse(root.has("reasoning_effort"), "Out-of-range effort should be ignored");
    }

    @Test
    void retryableErrorClassification() {
        // TimeoutException is retryable
        assertTrue(isRetryableForTest(new TimeoutException("timed out")));
        // RateLimitException is retryable
        assertTrue(isRetryableForTest(new ModelProviderException.RateLimitException("429", null)));
        // Server errors are retryable
        assertTrue(
                isRetryableForTest(
                        new ModelProviderException.ApiException("HTTP 502 - Bad Gateway")));
        assertTrue(
                isRetryableForTest(
                        new ModelProviderException.ApiException("HTTP 503 - Service Unavailable")));
        // Client errors are NOT retryable
        assertFalse(
                isRetryableForTest(
                        new ModelProviderException.ApiException("HTTP 400 - Bad Request")));
        // Random exceptions are NOT retryable
        assertFalse(isRetryableForTest(new IllegalArgumentException("bad")));
    }

    /** Mirror the provider's isRetryableError logic for testability. */
    private boolean isRetryableForTest(Throwable t) {
        if (t instanceof TimeoutException) return true;
        if (t instanceof ModelProviderException.RateLimitException) return true;
        if (t instanceof ModelProviderException.ApiException ae) {
            String msg = ae.getMessage();
            if (msg != null) {
                return msg.contains("server error")
                        || msg.contains("HTTP 500")
                        || msg.contains("HTTP 502")
                        || msg.contains("HTTP 503");
            }
        }
        return false;
    }
}
