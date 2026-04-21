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

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

/**
 * Shared retry/backoff policy for model providers.
 *
 * <p>Before this helper, each provider ({@code AnthropicProvider}, {@code OpenAIProvider}, …)
 * redeclared its own {@code MAX_RETRY_ATTEMPTS}, backoff constants and {@code isRetryableError}
 * predicate. That duplication made it easy for providers to drift apart on retry semantics. This
 * class concentrates:
 *
 * <ul>
 *   <li>the default retry budget (3 attempts, 1–4s exponential backoff, 25% jitter);
 *   <li>the canonical "transient error" predicate (timeouts, 429 rate limits, 5xx); and
 *   <li>the hook that logs each retry with the server-suggested {@code Retry-After} seconds
 *       surfaced by {@link ModelProviderException.RateLimitException}.
 * </ul>
 *
 * <p>The backoff spec is non-blocking (delays are executed on the Reactor scheduler). Provider
 * callers pass the returned spec to {@link reactor.core.publisher.Mono#retryWhen(Retry)}.
 */
public final class ProviderRetry {

    private static final Logger log = LoggerFactory.getLogger(ProviderRetry.class);

    /** Default number of retry attempts (excluding the initial try). */
    public static final long DEFAULT_MAX_ATTEMPTS = 3;

    /** Default minimum backoff between retries. */
    public static final Duration DEFAULT_MIN_BACKOFF = Duration.ofSeconds(1);

    /** Default cap on backoff growth. */
    public static final Duration DEFAULT_MAX_BACKOFF = Duration.ofSeconds(4);

    /** Default jitter fraction (0.0–1.0) applied to computed backoff. */
    public static final double DEFAULT_JITTER = 0.25;

    private ProviderRetry() {}

    /**
     * Build a {@link RetryBackoffSpec} with Kairo's default provider retry policy and a
     * provider-supplied retry predicate.
     *
     * @param providerName short provider identifier used in log messages (e.g. {@code "anthropic"})
     * @param shouldRetry predicate returning {@code true} for errors deemed transient
     * @return a configured {@link RetryBackoffSpec}
     */
    public static RetryBackoffSpec defaultSpec(
            String providerName, Predicate<Throwable> shouldRetry) {
        return Retry.backoff(DEFAULT_MAX_ATTEMPTS, DEFAULT_MIN_BACKOFF)
                .maxBackoff(DEFAULT_MAX_BACKOFF)
                .jitter(DEFAULT_JITTER)
                .filter(shouldRetry)
                .doBeforeRetry(signal -> logBeforeRetry(providerName, signal));
    }

    /**
     * Canonical "is this error worth retrying" predicate shared by providers. Handles the
     * intersection of failure modes that Anthropic and OpenAI both emit:
     *
     * <ul>
     *   <li>{@link TimeoutException} (including Reactor's timeout wrappers);
     *   <li>{@link ModelProviderException.RateLimitException} (HTTP 429); and
     *   <li>{@link ModelProviderException.ApiException} whose message indicates HTTP 5xx.
     * </ul>
     */
    public static boolean isTransientProviderError(Throwable t) {
        if (t == null) return false;
        if (t instanceof TimeoutException) return true;
        if (t instanceof ModelProviderException.RateLimitException) return true;
        if (t instanceof ModelProviderException.ApiException ae) {
            String msg = ae.getMessage();
            if (msg == null) return false;
            return msg.contains("HTTP 500")
                    || msg.contains("HTTP 502")
                    || msg.contains("HTTP 503")
                    || msg.contains("server error");
        }
        return false;
    }

    private static void logBeforeRetry(String providerName, Retry.RetrySignal signal) {
        Throwable failure = signal.failure();
        long attempt = signal.totalRetries() + 1;
        if (failure instanceof ModelProviderException.RateLimitException rle
                && rle.getRetryAfterSeconds() != null) {
            log.warn(
                    "[{}] Rate limited, retry {} (server suggests {}s wait)",
                    providerName,
                    attempt,
                    rle.getRetryAfterSeconds());
        } else {
            log.warn(
                    "[{}] Retrying API call, attempt {}: {}",
                    providerName,
                    attempt,
                    failure.getMessage());
        }
    }
}
