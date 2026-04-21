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

import io.kairo.api.agent.CancellationSignal;
import io.kairo.api.exception.AgentInterruptedException;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.RetryConfig;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.RetryBackoffSpec;

/**
 * Shared retry/backoff policy for model providers.
 *
 * <p>Concentrates retry semantics so providers don't duplicate backoff/filter/logging logic. All
 * retry behavior is driven by {@link RetryConfig} via {@link ReactiveRetryPolicy}. The static
 * {@code withPolicy} helpers remain for backward compatibility but now delegate to {@link
 * ReactiveRetryPolicy} internally.
 *
 * <p>Providers should prefer creating a {@link ReactiveRetryPolicy} from the per-call {@link
 * ModelConfig#retryConfig()} and using its {@code applyMono}/{@code applyFlux} methods directly.
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

    /**
     * Legacy default {@link RetryConfig} that mirrors the old hardcoded constants. Providers that
     * don't receive a per-call {@link RetryConfig} via {@link ModelConfig} use this.
     */
    static final RetryConfig LEGACY_DEFAULTS =
            RetryConfig.builder()
                    .maxAttempts(4) // 3 retries + 1 initial = Retry.backoff(3, ...)
                    .initialBackoff(DEFAULT_MIN_BACKOFF)
                    .maxBackoff(DEFAULT_MAX_BACKOFF)
                    .jitter(DEFAULT_JITTER)
                    .retryOn(RetryConfig.RETRYABLE_API_ERRORS)
                    .build();

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
        return new ReactiveRetryPolicy(LEGACY_DEFAULTS, providerName, shouldRetry).buildRetrySpec();
    }

    /** Apply Kairo's default retry+timeout policy to a provider {@link Mono} call path. */
    public static <T> Mono<T> withPolicy(
            Mono<T> source,
            String providerName,
            Predicate<Throwable> shouldRetry,
            Duration timeout) {
        ReactiveRetryPolicy policy =
                new ReactiveRetryPolicy(LEGACY_DEFAULTS, providerName, shouldRetry);
        return withCooperativeCancellation(policy.applyMono(source, timeout));
    }

    /** Apply Kairo's default retry+timeout policy to a provider {@link Flux} streaming path. */
    public static <T> Flux<T> withPolicy(
            Flux<T> source,
            String providerName,
            Predicate<Throwable> shouldRetry,
            Duration idleTimeout) {
        ReactiveRetryPolicy policy =
                new ReactiveRetryPolicy(LEGACY_DEFAULTS, providerName, shouldRetry);
        return withCooperativeCancellation(policy.applyFlux(source, idleTimeout));
    }

    /**
     * Apply retry+timeout from a per-call {@link ModelConfig}, falling back to defaults.
     *
     * @param source the source Mono
     * @param config the per-call model config (may contain a custom retryConfig/timeout)
     * @param providerName short provider identifier
     * @param shouldRetry provider-specific retry predicate
     * @param defaultTimeout fallback timeout when config.timeout() is null
     * @return the source with retry, timeout, and cooperative cancellation applied
     */
    public static <T> Mono<T> withConfigPolicy(
            Mono<T> source,
            ModelConfig config,
            String providerName,
            Predicate<Throwable> shouldRetry,
            Duration defaultTimeout) {
        ReactiveRetryPolicy policy = resolvePolicy(config, providerName, shouldRetry);
        Duration timeout = config.timeout() != null ? config.timeout() : defaultTimeout;
        return withCooperativeCancellation(policy.applyMono(source, timeout));
    }

    /**
     * Apply retry+timeout from a per-call {@link ModelConfig}, falling back to defaults.
     *
     * @param source the source Flux
     * @param config the per-call model config (may contain a custom retryConfig/timeout)
     * @param providerName short provider identifier
     * @param shouldRetry provider-specific retry predicate
     * @param defaultIdleTimeout fallback idle timeout when config.timeout() is null
     * @return the source with retry, timeout, and cooperative cancellation applied
     */
    public static <T> Flux<T> withConfigPolicy(
            Flux<T> source,
            ModelConfig config,
            String providerName,
            Predicate<Throwable> shouldRetry,
            Duration defaultIdleTimeout) {
        ReactiveRetryPolicy policy = resolvePolicy(config, providerName, shouldRetry);
        Duration idleTimeout = config.timeout() != null ? config.timeout() : defaultIdleTimeout;
        return withCooperativeCancellation(policy.applyFlux(source, idleTimeout));
    }

    /**
     * Resolve a {@link ReactiveRetryPolicy} from a {@link ModelConfig}, falling back to legacy
     * defaults.
     */
    static ReactiveRetryPolicy resolvePolicy(
            ModelConfig config, String providerName, Predicate<Throwable> shouldRetry) {
        RetryConfig retryConfig =
                config != null && config.retryConfig() != null
                        ? config.retryConfig()
                        : LEGACY_DEFAULTS;
        return new ReactiveRetryPolicy(retryConfig, providerName, shouldRetry);
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

    private static <T> Mono<T> withCooperativeCancellation(Mono<T> source) {
        return Mono.deferContextual(
                ctx -> {
                    CancellationSignal signal = resolveSignal(ctx);
                    if (signal == null) {
                        return source;
                    }
                    return source.takeUntilOther(cancellationTrigger(signal))
                            .switchIfEmpty(interruptedError(signal));
                });
    }

    private static <T> Flux<T> withCooperativeCancellation(Flux<T> source) {
        return Flux.deferContextual(
                ctx -> {
                    CancellationSignal signal = resolveSignal(ctx);
                    if (signal == null) {
                        return source;
                    }
                    return source.takeUntilOther(cancellationTrigger(signal))
                            .concatWith(ProviderRetry.<T>interruptedError(signal).flux());
                });
    }

    private static Mono<Long> cancellationTrigger(CancellationSignal signal) {
        if (signal.isCancelled()) {
            return Mono.just(0L);
        }
        return Flux.interval(Duration.ofMillis(50)).filter(tick -> signal.isCancelled()).next();
    }

    private static <T> Mono<T> interruptedError(CancellationSignal signal) {
        return signal.isCancelled()
                ? Mono.error(new AgentInterruptedException("Provider execution cancelled"))
                : Mono.empty();
    }

    private static CancellationSignal resolveSignal(reactor.util.context.ContextView ctx) {
        if (!ctx.hasKey(CancellationSignal.CONTEXT_KEY)) {
            return null;
        }
        Object value = ctx.get(CancellationSignal.CONTEXT_KEY);
        return value instanceof CancellationSignal signal ? signal : null;
    }
}
