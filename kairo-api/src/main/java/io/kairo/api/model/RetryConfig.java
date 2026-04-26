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

import io.kairo.api.Stable;
import java.time.Duration;
import java.util.function.Predicate;

/**
 * Configuration for retry behavior on API calls and tool executions.
 *
 * <p>This provides a unified retry/timeout configuration that can be layered (per-tool, per-agent,
 * global defaults) with merge semantics.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * RetryConfig config = RetryConfig.builder()
 *     .maxAttempts(3)
 *     .initialBackoff(Duration.ofSeconds(1))
 *     .maxBackoff(Duration.ofSeconds(30))
 *     .jitter(0.25)
 *     .retryOn(RetryConfig.RETRYABLE_API_ERRORS)
 *     .build();
 * }</pre>
 */
@Stable(value = "Retry config record + Builder; shape frozen since v0.3 (ADR-007)", since = "1.0.0")
public record RetryConfig(
        int maxAttempts,
        Duration initialBackoff,
        Duration maxBackoff,
        double jitter,
        Predicate<Throwable> retryOn) {

    /**
     * Default predicate for retryable API errors. Retries on rate limits (429), server errors
     * (5xx), timeouts, and IO errors. Does NOT retry on 400 (bad request) or 401/403 (auth errors).
     */
    public static final Predicate<Throwable> RETRYABLE_API_ERRORS = RetryConfig::isRetryableError;

    private static boolean isRetryableError(Throwable error) {
        if (error instanceof java.util.concurrent.TimeoutException) return true;
        if (error instanceof java.io.IOException) return true;
        Throwable cause = error.getCause();
        if (cause != null && cause != error) {
            return isRetryableError(cause);
        }
        return false;
    }

    /** Defaults for model API calls: 3 attempts, 2s initial backoff, 30s max, 25% jitter. */
    public static final RetryConfig MODEL_DEFAULTS =
            new RetryConfig(
                    3, Duration.ofSeconds(2), Duration.ofSeconds(30), 0.25, RETRYABLE_API_ERRORS);

    /** Defaults for tool executions: 1 attempt (no retry). */
    public static final RetryConfig TOOL_DEFAULTS =
            new RetryConfig(1, Duration.ofSeconds(1), Duration.ofSeconds(10), 0.25, e -> false);

    /** Create a new builder. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Merge two configs with primary taking precedence for non-default values.
     *
     * @param primary higher priority config
     * @param fallback lower priority config
     * @return merged config
     */
    public static RetryConfig merge(RetryConfig primary, RetryConfig fallback) {
        if (primary == null) return fallback;
        if (fallback == null) return primary;
        return primary;
    }

    /**
     * Calculate the delay for a given retry attempt, respecting Retry-After header.
     *
     * @param attempt the attempt number (1-based)
     * @param retryAfterSeconds server-suggested retry delay in seconds, or null
     * @return the delay duration
     */
    public Duration getDelay(int attempt, Long retryAfterSeconds) {
        // Respect server-provided Retry-After header (like claude-code-best does)
        if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            return Duration.ofSeconds(retryAfterSeconds);
        }
        // Exponential backoff with jitter
        long baseMs = initialBackoff.toMillis() * (long) Math.pow(2, attempt - 1);
        long cappedMs = Math.min(baseMs, maxBackoff.toMillis());
        long jitterMs = (long) (cappedMs * jitter * Math.random());
        return Duration.ofMillis(cappedMs + jitterMs);
    }

    /** Builder for {@link RetryConfig}. */
    public static class Builder {
        private int maxAttempts = 3;
        private Duration initialBackoff = Duration.ofSeconds(1);
        private Duration maxBackoff = Duration.ofSeconds(30);
        private double jitter = 0.25;
        private Predicate<Throwable> retryOn = RETRYABLE_API_ERRORS;

        public Builder maxAttempts(int maxAttempts) {
            if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder initialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
            return this;
        }

        public Builder maxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
            return this;
        }

        public Builder jitter(double jitter) {
            if (jitter < 0 || jitter > 1)
                throw new IllegalArgumentException("jitter must be 0.0–1.0");
            this.jitter = jitter;
            return this;
        }

        public Builder retryOn(Predicate<Throwable> retryOn) {
            this.retryOn = retryOn;
            return this;
        }

        public RetryConfig build() {
            return new RetryConfig(maxAttempts, initialBackoff, maxBackoff, jitter, retryOn);
        }
    }
}
