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

import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.RetryConfig;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Tests for {@link ReactiveRetryPolicy} and the unified retry/timeout path. */
class ReactiveRetryPolicyTest {

    // ---- Retry count ----

    @Test
    void monoRetriesUpToMaxAttempts() {
        RetryConfig config =
                RetryConfig.builder()
                        .maxAttempts(3) // 1 initial + 2 retries
                        .initialBackoff(Duration.ofMillis(10))
                        .maxBackoff(Duration.ofMillis(50))
                        .jitter(0)
                        .retryOn(e -> e instanceof TimeoutException)
                        .build();
        ReactiveRetryPolicy policy = new ReactiveRetryPolicy(config, "test");

        AtomicInteger attempts = new AtomicInteger();
        Mono<String> source =
                Mono.defer(
                        () -> {
                            attempts.incrementAndGet();
                            return Mono.error(new TimeoutException("boom"));
                        });

        StepVerifier.create(policy.applyMono(source, Duration.ofSeconds(5)))
                .expectError()
                .verify(Duration.ofSeconds(5));

        assertEquals(3, attempts.get(), "Should attempt 3 times (1 initial + 2 retries)");
    }

    @Test
    void fluxRetriesUpToMaxAttempts() {
        RetryConfig config =
                RetryConfig.builder()
                        .maxAttempts(2) // 1 initial + 1 retry
                        .initialBackoff(Duration.ofMillis(10))
                        .maxBackoff(Duration.ofMillis(50))
                        .jitter(0)
                        .retryOn(e -> e instanceof TimeoutException)
                        .build();
        ReactiveRetryPolicy policy = new ReactiveRetryPolicy(config, "test");

        AtomicInteger attempts = new AtomicInteger();
        Flux<String> source =
                Flux.defer(
                        () -> {
                            attempts.incrementAndGet();
                            return Flux.error(new TimeoutException("boom"));
                        });

        StepVerifier.create(policy.applyFlux(source, Duration.ofSeconds(5)))
                .expectError()
                .verify(Duration.ofSeconds(5));

        assertEquals(2, attempts.get(), "Should attempt 2 times (1 initial + 1 retry)");
    }

    // ---- Exception filtering ----

    @Test
    void doesNotRetryNonMatchingExceptions() {
        RetryConfig config =
                RetryConfig.builder()
                        .maxAttempts(3)
                        .initialBackoff(Duration.ofMillis(10))
                        .maxBackoff(Duration.ofMillis(50))
                        .jitter(0)
                        .retryOn(e -> e instanceof TimeoutException)
                        .build();
        ReactiveRetryPolicy policy = new ReactiveRetryPolicy(config, "test");

        AtomicInteger attempts = new AtomicInteger();
        Mono<String> source =
                Mono.defer(
                        () -> {
                            attempts.incrementAndGet();
                            return Mono.error(new IllegalArgumentException("bad"));
                        });

        StepVerifier.create(policy.applyMono(source, Duration.ofSeconds(5)))
                .expectError(IllegalArgumentException.class)
                .verify(Duration.ofSeconds(5));

        assertEquals(1, attempts.get(), "Should NOT retry non-matching exceptions");
    }

    @Test
    void combinesConfigAndProviderPredicates() {
        RetryConfig config =
                RetryConfig.builder()
                        .maxAttempts(3)
                        .initialBackoff(Duration.ofMillis(10))
                        .maxBackoff(Duration.ofMillis(50))
                        .jitter(0)
                        .retryOn(e -> e instanceof TimeoutException)
                        .build();

        // Provider-specific predicate: also retry RateLimitException
        ReactiveRetryPolicy policy =
                new ReactiveRetryPolicy(
                        config,
                        "test",
                        e -> e instanceof ModelProviderException.RateLimitException);

        AtomicInteger attempts = new AtomicInteger();
        Mono<String> source =
                Mono.defer(
                        () -> {
                            attempts.incrementAndGet();
                            return Mono.error(
                                    new ModelProviderException.RateLimitException(
                                            "rate limited", 1L));
                        });

        StepVerifier.create(policy.applyMono(source, Duration.ofSeconds(5)))
                .expectError()
                .verify(Duration.ofSeconds(5));

        assertEquals(3, attempts.get(), "Should retry RateLimitException via provider predicate");
    }

    // ---- Timeout ----

    @Test
    void monoTimesOut() {
        RetryConfig config =
                RetryConfig.builder()
                        .maxAttempts(1) // no retries
                        .initialBackoff(Duration.ofMillis(10))
                        .maxBackoff(Duration.ofMillis(50))
                        .jitter(0)
                        .build();
        ReactiveRetryPolicy policy = new ReactiveRetryPolicy(config, "test");

        Mono<String> source = Mono.never();

        StepVerifier.create(policy.applyMono(source, Duration.ofMillis(100)))
                .expectError(TimeoutException.class)
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void fluxIdleTimesOut() {
        RetryConfig config =
                RetryConfig.builder()
                        .maxAttempts(1) // no retries
                        .initialBackoff(Duration.ofMillis(10))
                        .maxBackoff(Duration.ofMillis(50))
                        .jitter(0)
                        .build();
        ReactiveRetryPolicy policy = new ReactiveRetryPolicy(config, "test");

        // Emits one item then goes silent
        Flux<String> source = Flux.just("first").concatWith(Flux.never());

        StepVerifier.create(policy.applyFlux(source, Duration.ofMillis(100)))
                .expectNext("first")
                .expectErrorMatches(
                        e ->
                                e instanceof TimeoutException
                                        || (e.getCause() != null
                                                && e.getCause() instanceof TimeoutException))
                .verify(Duration.ofSeconds(5));
    }

    // ---- Successful path ----

    @Test
    void succeedsWithoutRetryOnFirstAttempt() {
        RetryConfig config =
                RetryConfig.builder()
                        .maxAttempts(3)
                        .initialBackoff(Duration.ofMillis(10))
                        .maxBackoff(Duration.ofMillis(50))
                        .jitter(0)
                        .build();
        ReactiveRetryPolicy policy = new ReactiveRetryPolicy(config, "test");

        AtomicInteger attempts = new AtomicInteger();
        Mono<String> source =
                Mono.defer(
                        () -> {
                            attempts.incrementAndGet();
                            return Mono.just("ok");
                        });

        StepVerifier.create(policy.applyMono(source, Duration.ofSeconds(5)))
                .expectNext("ok")
                .verifyComplete();

        assertEquals(1, attempts.get());
    }

    @Test
    void succeedsAfterTransientFailure() {
        RetryConfig config =
                RetryConfig.builder()
                        .maxAttempts(3)
                        .initialBackoff(Duration.ofMillis(10))
                        .maxBackoff(Duration.ofMillis(50))
                        .jitter(0)
                        .retryOn(e -> e instanceof TimeoutException)
                        .build();
        ReactiveRetryPolicy policy = new ReactiveRetryPolicy(config, "test");

        AtomicInteger attempts = new AtomicInteger();
        Mono<String> source =
                Mono.defer(
                        () -> {
                            if (attempts.incrementAndGet() < 3) {
                                return Mono.error(new TimeoutException("transient"));
                            }
                            return Mono.just("recovered");
                        });

        StepVerifier.create(policy.applyMono(source, Duration.ofSeconds(5)))
                .expectNext("recovered")
                .verifyComplete();

        assertEquals(3, attempts.get());
    }

    // ---- Per-call config override via ProviderRetry.withConfigPolicy ----

    @Test
    void withConfigPolicyUsesModelConfigRetryConfig() {
        RetryConfig custom =
                RetryConfig.builder()
                        .maxAttempts(2) // 1 initial + 1 retry
                        .initialBackoff(Duration.ofMillis(10))
                        .maxBackoff(Duration.ofMillis(50))
                        .jitter(0)
                        .retryOn(e -> e instanceof TimeoutException)
                        .build();

        ModelConfig config =
                ModelConfig.builder()
                        .model("test-model")
                        .retryConfig(custom)
                        .timeout(Duration.ofSeconds(5))
                        .build();

        AtomicInteger attempts = new AtomicInteger();
        Mono<String> source =
                Mono.defer(
                        () -> {
                            attempts.incrementAndGet();
                            return Mono.error(new TimeoutException("boom"));
                        });

        StepVerifier.create(
                        ProviderRetry.withConfigPolicy(
                                source,
                                config,
                                "test",
                                ProviderRetry::isTransientProviderError,
                                Duration.ofSeconds(30)))
                .expectError()
                .verify(Duration.ofSeconds(10));

        // Custom config says maxAttempts=2, so 2 total attempts
        assertEquals(2, attempts.get());
    }

    @Test
    void withConfigPolicyFallsBackToLegacyDefaults() {
        ModelConfig config = ModelConfig.builder().model("test-model").build();

        AtomicInteger attempts = new AtomicInteger();
        Mono<String> source =
                Mono.defer(
                        () -> {
                            attempts.incrementAndGet();
                            return Mono.error(new TimeoutException("boom"));
                        });

        StepVerifier.create(
                        ProviderRetry.withConfigPolicy(
                                source,
                                config,
                                "test",
                                ProviderRetry::isTransientProviderError,
                                Duration.ofSeconds(30)))
                .expectError()
                .verify(Duration.ofSeconds(30));

        // Legacy defaults: maxAttempts=4 (3 retries + 1 initial)
        assertEquals(4, attempts.get());
    }

    // ---- isTransientProviderError ----

    @Test
    void isTransientProviderErrorCoversExpectedTypes() {
        assertTrue(ProviderRetry.isTransientProviderError(new TimeoutException("t")));
        assertTrue(
                ProviderRetry.isTransientProviderError(
                        new ModelProviderException.RateLimitException("429", null)));
        assertTrue(
                ProviderRetry.isTransientProviderError(
                        new ModelProviderException.ApiException("HTTP 503 server error")));
        assertFalse(
                ProviderRetry.isTransientProviderError(
                        new ModelProviderException.ApiException("HTTP 401 unauthorized")));
        assertFalse(ProviderRetry.isTransientProviderError(new IllegalArgumentException("bad")));
        assertFalse(ProviderRetry.isTransientProviderError(null));
    }

    // ---- RetryConfig on ModelConfig ----

    @Test
    void modelConfigRetryConfigAndTimeoutNullByDefault() {
        ModelConfig config = ModelConfig.builder().model("test").build();
        assertNull(config.retryConfig());
        assertNull(config.timeout());
    }

    @Test
    void modelConfigRetryConfigRoundTrips() {
        RetryConfig retryConfig =
                RetryConfig.builder().maxAttempts(5).initialBackoff(Duration.ofSeconds(3)).build();
        Duration timeout = Duration.ofSeconds(60);

        ModelConfig config =
                ModelConfig.builder()
                        .model("test")
                        .retryConfig(retryConfig)
                        .timeout(timeout)
                        .build();

        assertSame(retryConfig, config.retryConfig());
        assertEquals(timeout, config.timeout());
    }
}
