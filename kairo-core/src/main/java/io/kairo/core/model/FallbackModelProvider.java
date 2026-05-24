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

import io.kairo.api.message.Msg;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import java.util.List;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * {@link ModelProvider} decorator that tries a primary provider first and falls back to a chain of
 * secondary providers when the primary fails. Each fallback is tried in order until one succeeds or
 * the chain is exhausted.
 *
 * <p>Use cases:
 *
 * <ul>
 *   <li>Primary rate-limited → fall back to secondary.
 *   <li>Primary down → switch to alternate vendor.
 *   <li>Cost optimization: cheap primary, expensive fallback only on validation failure.
 * </ul>
 *
 * <p>By default, this falls back on any {@link Throwable}. Pass a custom {@link Predicate
 * Predicate&lt;Throwable&gt;} via the constructor to scope fallback to specific error types
 * (typically rate-limit + server-error to avoid burning the secondary on legitimate prompt-too-long
 * errors). Each individual provider's own retry / circuit-breaker still runs first; this layer
 * activates only after that machinery gives up.
 *
 * <p>The decorator preserves {@link ModelProvider} semantics: same-name `call` returns Mono,
 * `stream` returns Flux, `name` reports a composite identifier so observability tools can
 * differentiate sessions wired through this decorator.
 *
 * @since M-D2' (Experimental)
 */
public final class FallbackModelProvider implements ModelProvider {

    private static final Logger log = LoggerFactory.getLogger(FallbackModelProvider.class);

    private final ModelProvider primary;
    private final List<ModelProvider> fallbacks;
    private final Predicate<Throwable> shouldFallback;

    /**
     * Build a fallback chain that triggers on any error. For tighter scoping use the {@link
     * #FallbackModelProvider(ModelProvider, List, Predicate)} constructor.
     */
    public FallbackModelProvider(ModelProvider primary, List<ModelProvider> fallbacks) {
        this(primary, fallbacks, FallbackModelProvider::defaultShouldFallback);
    }

    /**
     * @param primary the provider tried first
     * @param fallbacks ordered list of providers to try when primary fails (and predicate allows).
     *     Empty list = behave as the primary alone.
     * @param shouldFallback predicate inspecting the error from each provider; return true to try
     *     the next provider in the chain, false to surface the error immediately
     */
    public FallbackModelProvider(
            ModelProvider primary,
            List<ModelProvider> fallbacks,
            Predicate<Throwable> shouldFallback) {
        if (primary == null) throw new IllegalArgumentException("primary must not be null");
        this.primary = primary;
        this.fallbacks = fallbacks == null ? List.of() : List.copyOf(fallbacks);
        this.shouldFallback =
                shouldFallback == null
                        ? FallbackModelProvider::defaultShouldFallback
                        : shouldFallback;
    }

    /**
     * Default fallback predicate: retry on rate-limit and server errors (most likely to be
     * transient + cured by switching vendors). Skip on auth and prompt-too-long, since those are
     * unlikely to differ between providers.
     */
    public static boolean defaultShouldFallback(Throwable t) {
        if (t instanceof ModelProviderException.RateLimitException) return true;
        if (t instanceof ModelProviderException.ApiException ae) {
            String msg = ae.getMessage();
            // 5xx tends to surface as a generic ApiException with the status in the message.
            // Auth (401), prompt-too-long, and validation errors stay false because switching
            // vendor won't help.
            return msg != null && (msg.contains("5") || msg.contains("server"));
        }
        // Non-provider exceptions (timeouts, network, IOException) — worth a retry on a
        // different vendor.
        return true;
    }

    @Override
    public Mono<ModelResponse> call(List<Msg> messages, ModelConfig config) {
        Mono<ModelResponse> chain = primary.call(messages, config);
        for (ModelProvider fb : fallbacks) {
            chain =
                    chain.onErrorResume(
                            err -> {
                                if (!shouldFallback.test(err)) {
                                    return Mono.error(err);
                                }
                                log.warn(
                                        "Primary provider {} failed ({}); falling back to {}",
                                        primary.name(),
                                        err.toString(),
                                        fb.name());
                                return fb.call(messages, config);
                            });
        }
        return chain;
    }

    @Override
    public Flux<ModelResponse> stream(List<Msg> messages, ModelConfig config) {
        Flux<ModelResponse> chain = primary.stream(messages, config);
        for (ModelProvider fb : fallbacks) {
            chain =
                    chain.onErrorResume(
                            err -> {
                                if (!shouldFallback.test(err)) {
                                    return Flux.error(err);
                                }
                                log.warn(
                                        "Primary provider {} streaming failed ({}); falling back"
                                                + " to {}",
                                        primary.name(),
                                        err.toString(),
                                        fb.name());
                                return fb.stream(messages, config);
                            });
        }
        return chain;
    }

    @Override
    public String name() {
        if (fallbacks.isEmpty()) return primary.name();
        StringBuilder sb = new StringBuilder("fallback[");
        sb.append(primary.name());
        for (ModelProvider fb : fallbacks) {
            sb.append("→").append(fb.name());
        }
        return sb.append("]").toString();
    }
}
