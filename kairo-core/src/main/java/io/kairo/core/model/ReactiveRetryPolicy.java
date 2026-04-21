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

import io.kairo.api.model.RetryConfig;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

/**
 * Applies {@link RetryConfig} retry/timeout semantics to reactive {@link Mono} and {@link Flux}
 * pipelines.
 *
 * <p>This is the single source of truth for retry+timeout behavior across all model providers.
 * Providers create a policy from the per-call {@link io.kairo.api.model.ModelConfig#retryConfig()}
 * (falling back to {@link RetryConfig#MODEL_DEFAULTS}) and delegate to {@link #applyMono} / {@link
 * #applyFlux} instead of inlining their own {@code retryWhen}/{@code timeout} operators.
 *
 * <p>Provider-specific retry eligibility (e.g. Anthropic overload detection) is supported via the
 * {@code additionalRetryPredicate} hook.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * RetryConfig cfg = config.retryConfig() != null ? config.retryConfig() : RetryConfig.MODEL_DEFAULTS;
 * ReactiveRetryPolicy policy = new ReactiveRetryPolicy(cfg, "anthropic",
 *         ProviderRetry::isTransientProviderError);
 * return policy.applyMono(callMono, Duration.ofSeconds(30));
 * }</pre>
 */
public final class ReactiveRetryPolicy {

    private static final Logger log = LoggerFactory.getLogger(ReactiveRetryPolicy.class);

    private final RetryConfig retryConfig;
    private final String providerName;
    private final Predicate<Throwable> retryPredicate;

    /**
     * Create a retry policy.
     *
     * @param retryConfig the retry configuration (max attempts, backoff, etc.)
     * @param providerName short provider identifier used in log messages (e.g. {@code "anthropic"})
     * @param additionalRetryPredicate provider-specific predicate for retry-eligible errors;
     *     combined with {@link RetryConfig#retryOn()} via OR logic
     */
    public ReactiveRetryPolicy(
            RetryConfig retryConfig,
            String providerName,
            Predicate<Throwable> additionalRetryPredicate) {
        this.retryConfig = Objects.requireNonNull(retryConfig, "retryConfig");
        this.providerName = Objects.requireNonNull(providerName, "providerName");
        // Combine the RetryConfig's built-in predicate with the provider-specific one
        Predicate<Throwable> configPredicate = retryConfig.retryOn();
        if (additionalRetryPredicate != null) {
            this.retryPredicate = configPredicate.or(additionalRetryPredicate);
        } else {
            this.retryPredicate = configPredicate;
        }
    }

    /**
     * Create a retry policy using only the {@link RetryConfig} predicate (no provider-specific
     * hook).
     *
     * @param retryConfig the retry configuration
     * @param providerName short provider identifier
     */
    public ReactiveRetryPolicy(RetryConfig retryConfig, String providerName) {
        this(retryConfig, providerName, null);
    }

    /**
     * Apply retry + timeout to a {@link Mono} call path.
     *
     * <p>The timeout is applied <em>after</em> the retry spec so that each individual attempt
     * (including retries) is bound by the total timeout.
     *
     * @param source the source Mono
     * @param timeout the overall timeout duration
     * @return the source with retry and timeout applied
     */
    public <T> Mono<T> applyMono(Mono<T> source, Duration timeout) {
        return source.retryWhen(buildRetrySpec()).timeout(timeout);
    }

    /**
     * Apply retry + timeout to a {@link Flux} streaming path.
     *
     * <p>For streams, the idle timeout is applied <em>before</em> the retry spec so that idle
     * timeouts trigger retries (reconnection).
     *
     * @param source the source Flux
     * @param idleTimeout the idle timeout duration (time between elements)
     * @return the source with timeout and retry applied
     */
    public <T> Flux<T> applyFlux(Flux<T> source, Duration idleTimeout) {
        return source.timeout(idleTimeout).retryWhen(buildRetrySpec());
    }

    /**
     * Build the Reactor {@link RetryBackoffSpec} from the {@link RetryConfig}.
     *
     * @return the configured retry spec
     */
    RetryBackoffSpec buildRetrySpec() {
        // maxAttempts in RetryConfig = total attempts (including initial).
        // Reactor's Retry.backoff wants max *retries* (excluding initial try).
        long maxRetries = Math.max(0, retryConfig.maxAttempts() - 1);
        return Retry.backoff(maxRetries, retryConfig.initialBackoff())
                .maxBackoff(retryConfig.maxBackoff())
                .jitter(retryConfig.jitter())
                .filter(retryPredicate)
                .doBeforeRetry(
                        signal -> {
                            Throwable failure = signal.failure();
                            long attempt = signal.totalRetries() + 1;
                            if (failure
                                            instanceof
                                            io.kairo.core.model.ModelProviderException
                                                            .RateLimitException
                                                    rle
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
                        });
    }

    /** Returns the effective retry predicate (config + provider-specific combined). */
    Predicate<Throwable> retryPredicate() {
        return retryPredicate;
    }

    /** Returns the underlying retry config. */
    public RetryConfig retryConfig() {
        return retryConfig;
    }
}
