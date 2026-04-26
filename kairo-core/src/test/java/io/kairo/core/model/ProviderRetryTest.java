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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.exception.ModelRateLimitException;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.RetryConfig;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Unit tests for {@link ProviderRetry}. */
class ProviderRetryTest {

    // ===== isTransientProviderError =====

    @Test
    void isTransientProviderError_null_returnsFalse() {
        assertThat(ProviderRetry.isTransientProviderError(null)).isFalse();
    }

    @Test
    void isTransientProviderError_timeoutException_returnsTrue() {
        assertThat(ProviderRetry.isTransientProviderError(new TimeoutException("timed out")))
                .isTrue();
    }

    @Test
    void isTransientProviderError_rateLimitException_returnsTrue() {
        assertThat(
                        ProviderRetry.isTransientProviderError(
                                new ModelProviderException.RateLimitException("rate limit", null)))
                .isTrue();
    }

    @Test
    void isTransientProviderError_publicRateLimitException_returnsTrue() {
        assertThat(
                        ProviderRetry.isTransientProviderError(
                                new ModelRateLimitException("rate limit exceeded")))
                .isTrue();
    }

    @Test
    void isTransientProviderError_http500ApiException_returnsTrue() {
        assertThat(
                        ProviderRetry.isTransientProviderError(
                                new ModelProviderException.ApiException(
                                        "HTTP 500 internal server error")))
                .isTrue();
    }

    @Test
    void isTransientProviderError_http503ApiException_returnsTrue() {
        assertThat(
                        ProviderRetry.isTransientProviderError(
                                new ModelProviderException.ApiException("HTTP 503 unavailable")))
                .isTrue();
    }

    @Test
    void isTransientProviderError_http401ApiException_returnsFalse() {
        // 401 is not a transient error — it's an auth failure
        assertThat(
                        ProviderRetry.isTransientProviderError(
                                new ModelProviderException.ApiException("HTTP 401 unauthorized")))
                .isFalse();
    }

    @Test
    void isTransientProviderError_genericRuntimeException_returnsFalse() {
        assertThat(ProviderRetry.isTransientProviderError(new RuntimeException("unknown error")))
                .isFalse();
    }

    // ===== withPolicy: immediate success — no retry =====

    @Test
    void withPolicy_immediateSuccess_returnsValueWithoutRetry() {
        AtomicInteger callCount = new AtomicInteger(0);

        Mono<String> source = Mono.fromCallable(() -> "result-" + callCount.incrementAndGet());

        StepVerifier.create(
                        ProviderRetry.withPolicy(
                                source,
                                "test-provider",
                                ProviderRetry::isTransientProviderError,
                                Duration.ofSeconds(5)))
                .assertNext(v -> assertThat(v).isEqualTo("result-1"))
                .verifyComplete();

        assertThat(callCount.get()).isEqualTo(1);
    }

    // ===== withConfigPolicy: retryable error → retries and eventually succeeds =====

    @Test
    void withConfigPolicy_retryableError_retriesAndSucceeds() {
        AtomicInteger callCount = new AtomicInteger(0);

        // Fail on first 2 attempts (rate limit), succeed on 3rd
        Mono<String> source =
                Mono.fromCallable(
                        () -> {
                            int n = callCount.incrementAndGet();
                            if (n < 3) {
                                throw new ModelProviderException.RateLimitException(
                                        "rate limit", null);
                            }
                            return "success-on-attempt-" + n;
                        });

        // 3 max attempts, near-zero backoff for fast test
        RetryConfig fastRetry =
                RetryConfig.builder()
                        .maxAttempts(3)
                        .initialBackoff(Duration.ofMillis(1))
                        .retryOn(t -> t instanceof ModelProviderException.RateLimitException)
                        .build();

        ModelConfig config =
                ModelConfig.builder()
                        .model("test-model")
                        .maxTokens(4096)
                        .temperature(0.7)
                        .tools(java.util.List.of())
                        .retryConfig(fastRetry)
                        .build();

        StepVerifier.create(
                        ProviderRetry.withConfigPolicy(
                                source,
                                config,
                                "test-provider",
                                t -> t instanceof ModelProviderException.RateLimitException,
                                Duration.ofSeconds(5)))
                .assertNext(v -> assertThat(v).contains("success-on-attempt-3"))
                .verifyComplete();

        assertThat(callCount.get()).isEqualTo(3);
    }

    // ===== withConfigPolicy: max retries exceeded → error =====

    @Test
    void withConfigPolicy_maxRetriesExceeded_returnsError() {
        AtomicInteger callCount = new AtomicInteger(0);

        // Always fail with rate limit
        Mono<String> source =
                Mono.fromCallable(
                        () -> {
                            callCount.incrementAndGet();
                            throw new ModelProviderException.RateLimitException(
                                    "always fail", null);
                        });

        // Only 2 max attempts (1 retry), near-zero backoff
        RetryConfig fastRetry =
                RetryConfig.builder()
                        .maxAttempts(2)
                        .initialBackoff(Duration.ofMillis(1))
                        .retryOn(t -> t instanceof ModelProviderException.RateLimitException)
                        .build();

        ModelConfig config =
                ModelConfig.builder()
                        .model("test-model")
                        .maxTokens(4096)
                        .temperature(0.7)
                        .tools(java.util.List.of())
                        .retryConfig(fastRetry)
                        .build();

        StepVerifier.create(
                        ProviderRetry.withConfigPolicy(
                                source,
                                config,
                                "test-provider",
                                t -> t instanceof ModelProviderException.RateLimitException,
                                Duration.ofSeconds(5)))
                .expectError()
                .verify();

        assertThat(callCount.get()).isEqualTo(2);
    }

    // ===== withConfigPolicy: non-retryable error → immediate failure =====

    @Test
    void withConfigPolicy_nonRetryableError_doesNotRetry() {
        AtomicInteger callCount = new AtomicInteger(0);

        // Fail with a non-retryable 401 error
        Mono<String> source =
                Mono.fromCallable(
                        () -> {
                            callCount.incrementAndGet();
                            throw new ModelProviderException.ApiException("HTTP 401 unauthorized");
                        });

        RetryConfig fastRetry =
                RetryConfig.builder()
                        .maxAttempts(3)
                        .initialBackoff(Duration.ofMillis(1))
                        .retryOn(ProviderRetry::isTransientProviderError)
                        .build();

        ModelConfig config =
                ModelConfig.builder()
                        .model("test-model")
                        .maxTokens(4096)
                        .temperature(0.7)
                        .tools(java.util.List.of())
                        .retryConfig(fastRetry)
                        .build();

        StepVerifier.create(
                        ProviderRetry.withConfigPolicy(
                                source,
                                config,
                                "test-provider",
                                ProviderRetry::isTransientProviderError,
                                Duration.ofSeconds(5)))
                .expectError()
                .verify();

        // Should only be called once — no retry for 401
        assertThat(callCount.get()).isEqualTo(1);
    }
}
