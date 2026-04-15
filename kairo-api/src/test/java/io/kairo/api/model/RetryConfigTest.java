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
package io.kairo.api.model;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class RetryConfigTest {

    @Test
    void builderDefaults() {
        RetryConfig config = RetryConfig.builder().build();
        assertEquals(3, config.maxAttempts());
        assertEquals(Duration.ofSeconds(1), config.initialBackoff());
        assertEquals(Duration.ofSeconds(30), config.maxBackoff());
        assertEquals(0.25, config.jitter(), 0.001);
        assertNotNull(config.retryOn());
    }

    @Test
    void builderCustomValues() {
        RetryConfig config =
                RetryConfig.builder()
                        .maxAttempts(5)
                        .initialBackoff(Duration.ofMillis(500))
                        .maxBackoff(Duration.ofSeconds(60))
                        .jitter(0.5)
                        .retryOn(e -> false)
                        .build();

        assertEquals(5, config.maxAttempts());
        assertEquals(Duration.ofMillis(500), config.initialBackoff());
        assertEquals(Duration.ofSeconds(60), config.maxBackoff());
        assertEquals(0.5, config.jitter(), 0.001);
    }

    @Test
    void maxAttemptsValidation() {
        assertThrows(
                IllegalArgumentException.class, () -> RetryConfig.builder().maxAttempts(0).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> RetryConfig.builder().maxAttempts(-1).build());
    }

    @Test
    void jitterValidation() {
        assertThrows(
                IllegalArgumentException.class, () -> RetryConfig.builder().jitter(-0.1).build());
        assertThrows(
                IllegalArgumentException.class, () -> RetryConfig.builder().jitter(1.1).build());
    }

    @Test
    void modelDefaultsPreset() {
        RetryConfig defaults = RetryConfig.MODEL_DEFAULTS;
        assertEquals(3, defaults.maxAttempts());
        assertEquals(Duration.ofSeconds(2), defaults.initialBackoff());
        assertEquals(Duration.ofSeconds(30), defaults.maxBackoff());
        assertEquals(0.25, defaults.jitter(), 0.001);
    }

    @Test
    void toolDefaultsPreset() {
        RetryConfig defaults = RetryConfig.TOOL_DEFAULTS;
        assertEquals(1, defaults.maxAttempts());
    }

    @Test
    void retryableApiErrorsPredicateAcceptsTimeoutException() {
        assertTrue(RetryConfig.RETRYABLE_API_ERRORS.test(new TimeoutException("timed out")));
    }

    @Test
    void retryableApiErrorsPredicateAcceptsIOException() {
        assertTrue(RetryConfig.RETRYABLE_API_ERRORS.test(new IOException("connection reset")));
    }

    @Test
    void retryableApiErrorsPredicateRejectsGenericException() {
        assertFalse(RetryConfig.RETRYABLE_API_ERRORS.test(new IllegalArgumentException("bad")));
    }

    @Test
    void retryableApiErrorsPredicateChecksWrappedCause() {
        RuntimeException wrapped = new RuntimeException("wrapper", new IOException("io"));
        assertTrue(RetryConfig.RETRYABLE_API_ERRORS.test(wrapped));
    }

    @Test
    void getDelayRespectsRetryAfterHeader() {
        RetryConfig config = RetryConfig.builder().initialBackoff(Duration.ofSeconds(1)).build();
        Duration delay = config.getDelay(1, 10L);
        assertEquals(Duration.ofSeconds(10), delay);
    }

    @Test
    void getDelayUsesExponentialBackoffWhenNoRetryAfter() {
        RetryConfig config =
                RetryConfig.builder()
                        .initialBackoff(Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(60))
                        .jitter(0.0)
                        .build();

        // attempt 1: 1s * 2^0 = 1s
        Duration d1 = config.getDelay(1, null);
        assertEquals(1000, d1.toMillis());

        // attempt 2: 1s * 2^1 = 2s
        Duration d2 = config.getDelay(2, null);
        assertEquals(2000, d2.toMillis());

        // attempt 3: 1s * 2^2 = 4s
        Duration d3 = config.getDelay(3, null);
        assertEquals(4000, d3.toMillis());
    }

    @Test
    void getDelayCapsAtMaxBackoff() {
        RetryConfig config =
                RetryConfig.builder()
                        .initialBackoff(Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(5))
                        .jitter(0.0)
                        .build();

        // attempt 10: 1s * 2^9 = 512s, capped at 5s
        Duration d = config.getDelay(10, null);
        assertEquals(5000, d.toMillis());
    }

    @Test
    void mergeReturnsNonNullWhenOneIsNull() {
        RetryConfig config = RetryConfig.builder().maxAttempts(5).build();
        assertSame(config, RetryConfig.merge(config, null));
        assertSame(config, RetryConfig.merge(null, config));
    }

    @Test
    void mergeReturnsPrimaryWhenBothPresent() {
        RetryConfig primary = RetryConfig.builder().maxAttempts(5).build();
        RetryConfig fallback = RetryConfig.builder().maxAttempts(10).build();
        RetryConfig merged = RetryConfig.merge(primary, fallback);
        assertEquals(5, merged.maxAttempts());
    }

    @Test
    void mergeReturnsNullWhenBothNull() {
        assertNull(RetryConfig.merge(null, null));
    }
}
