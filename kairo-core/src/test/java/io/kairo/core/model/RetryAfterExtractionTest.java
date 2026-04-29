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

import io.kairo.api.model.RetryConfig;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Tests for Retry-After header extraction and dynamic backoff in {@link ReactiveRetryPolicy}. */
class RetryAfterExtractionTest {

    // ---- ModelProviderUtils.parseRetryAfter ----

    @Test
    void parseRetryAfterSeconds() {
        Long result = ModelProviderUtils.parseRetryAfter("30", null);
        assertNotNull(result);
        assertEquals(30_000L, result);
    }

    @Test
    void parseXRateLimitResetAfterDecimal() {
        Long result = ModelProviderUtils.parseRetryAfter(null, "1.5");
        assertNotNull(result);
        assertEquals(1_500L, result);
    }

    @Test
    void parseRetryAfterPreferredOverXRateLimitResetAfter() {
        Long result = ModelProviderUtils.parseRetryAfter("10", "60");
        assertNotNull(result);
        assertEquals(10_000L, result);
    }

    @Test
    void parseRetryAfterCappedAtFiveMinutes() {
        Long result = ModelProviderUtils.parseRetryAfter("999", null);
        assertNotNull(result);
        assertEquals(300_000L, result); // 5 min cap
    }

    @Test
    void parseXRateLimitResetAfterCappedAtFiveMinutes() {
        Long result = ModelProviderUtils.parseRetryAfter(null, "600");
        assertNotNull(result);
        assertEquals(300_000L, result); // 5 min cap
    }

    @Test
    void parseRetryAfterNullReturnsNull() {
        assertNull(ModelProviderUtils.parseRetryAfter(null, null));
    }

    @Test
    void parseRetryAfterBlankReturnsNull() {
        assertNull(ModelProviderUtils.parseRetryAfter("", null));
        assertNull(ModelProviderUtils.parseRetryAfter("  ", null));
    }

    @Test
    void parseRetryAfterInvalidFormatReturnsNull() {
        // HTTP-date format (not integer seconds)
        assertNull(ModelProviderUtils.parseRetryAfter("Sat, 29 Apr 2026 00:00:00 GMT", null));
    }

    @Test
    void parseRetryAfterXRateLimitResetInvalidReturnsNull() {
        assertNull(ModelProviderUtils.parseRetryAfter(null, "not-a-number"));
    }

    // ---- ReactiveRetryPolicy.computeRetryDelay with server-specified delay ----

    @Test
    void usesServerSpecifiedDelayForRateLimitException() {
        RetryConfig config =
                RetryConfig.builder()
                        .maxAttempts(3)
                        .initialBackoff(Duration.ofMillis(10))
                        .maxBackoff(Duration.ofMillis(50))
                        .jitter(0)
                        .build();
        ReactiveRetryPolicy policy = new ReactiveRetryPolicy(config, "test");

        // 30s retry-after = 30,000ms
        var exception = new ModelProviderException.RateLimitException("rate limited", 30_000L);
        Duration delay = policy.computeRetryDelay(1, exception);

        // Should be >= 30s (with 10% jitter, so between 30s and 33s)
        assertTrue(
                delay.toMillis() >= 30_000 && delay.toMillis() <= 33_000,
                "Delay should be ~30s with 10% jitter, was " + delay.toMillis() + "ms");
    }

    @Test
    void usesServerSpecifiedDelayWithJitter() {
        RetryConfig config =
                RetryConfig.builder()
                        .maxAttempts(3)
                        .initialBackoff(Duration.ofMillis(10))
                        .maxBackoff(Duration.ofMillis(50))
                        .jitter(0)
                        .build();
        ReactiveRetryPolicy policy = new ReactiveRetryPolicy(config, "test");

        // 5s retry-after = 5,000ms
        var exception = new ModelProviderException.RateLimitException("rate limited", 5_000L);
        Duration delay = policy.computeRetryDelay(1, exception);

        // With 10% jitter: base 5000ms + 0..500ms jitter = 5000..5500ms
        assertTrue(
                delay.toMillis() >= 5_000 && delay.toMillis() <= 5_500,
                "Delay should be ~5s with 10% jitter, was " + delay.toMillis() + "ms");
    }

    @Test
    void fallsBackToExponentialBackoffWhenNoRetryAfter() {
        RetryConfig config =
                RetryConfig.builder()
                        .maxAttempts(3)
                        .initialBackoff(Duration.ofMillis(100))
                        .maxBackoff(Duration.ofMillis(500))
                        .jitter(0)
                        .build();
        ReactiveRetryPolicy policy = new ReactiveRetryPolicy(config, "test");

        // TimeoutException has no retry-after info
        var exception = new TimeoutException("timeout");
        Duration delay = policy.computeRetryDelay(1, exception);

        // Exponential: 100 * 2^(1-1) = 100ms
        assertEquals(100L, delay.toMillis());

        Duration delay2 = policy.computeRetryDelay(2, exception);
        // Exponential: 100 * 2^(2-1) = 200ms
        assertEquals(200L, delay2.toMillis());

        Duration delay3 = policy.computeRetryDelay(3, exception);
        // Exponential: 100 * 2^(3-1) = 400ms (under max 500ms)
        assertEquals(400L, delay3.toMillis());
    }

    @Test
    void rateLimitExceptionWithoutRetryAfterUsesExponentialBackoff() {
        RetryConfig config =
                RetryConfig.builder()
                        .maxAttempts(3)
                        .initialBackoff(Duration.ofMillis(100))
                        .maxBackoff(Duration.ofMillis(500))
                        .jitter(0)
                        .build();
        ReactiveRetryPolicy policy = new ReactiveRetryPolicy(config, "test");

        // RateLimitException with null retryAfterSeconds
        var exception = new ModelProviderException.RateLimitException("rate limited", null);
        Duration delay = policy.computeRetryDelay(1, exception);

        // Should fall back to exponential backoff: 100ms
        assertEquals(100L, delay.toMillis());
    }

    @Test
    void cappedRetryAfterRespected() {
        RetryConfig config =
                RetryConfig.builder()
                        .maxAttempts(3)
                        .initialBackoff(Duration.ofMillis(10))
                        .maxBackoff(Duration.ofMillis(50))
                        .jitter(0)
                        .build();
        ReactiveRetryPolicy policy = new ReactiveRetryPolicy(config, "test");

        // 300s (5 min cap) = 300,000ms
        var exception = new ModelProviderException.RateLimitException("rate limited", 300_000L);
        Duration delay = policy.computeRetryDelay(1, exception);

        // Should be >= 300s with 10% jitter
        assertTrue(
                delay.toMillis() >= 300_000 && delay.toMillis() <= 330_000,
                "Delay should be ~300s with 10% jitter, was " + delay.toMillis() + "ms");
    }

    // ---- End-to-end: retry actually waits with server-specified delay ----

    @Test
    void retryWaitsWithServerSpecifiedDelay() {
        RetryConfig config =
                RetryConfig.builder()
                        .maxAttempts(2) // 1 initial + 1 retry
                        .initialBackoff(Duration.ofMillis(10))
                        .maxBackoff(Duration.ofMillis(50))
                        .jitter(0)
                        .retryOn(e -> e instanceof ModelProviderException.RateLimitException)
                        .build();
        ReactiveRetryPolicy policy = new ReactiveRetryPolicy(config, "test");

        AtomicInteger attempts = new AtomicInteger();
        long serverRetryMs = 5_000; // 5s (virtual time makes this instant)
        Mono<String> source =
                Mono.defer(
                        () -> {
                            if (attempts.incrementAndGet() == 1) {
                                return Mono.error(
                                        new ModelProviderException.RateLimitException(
                                                "rate limited", serverRetryMs));
                            }
                            return Mono.just("ok");
                        });

        StepVerifier.withVirtualTime(() -> policy.applyMono(source, Duration.ofSeconds(60)))
                .thenAwait(Duration.ofMillis(serverRetryMs + 100))
                .expectNext("ok")
                .verifyComplete();

        assertEquals(2, attempts.get());
    }
}
