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
package io.kairo.observability;

import io.kairo.core.health.HookChainObserver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;

/**
 * Micrometer metrics for Kairo hook chain firings.
 *
 * <p>Implements {@link HookChainObserver} to record per-phase / per-decision metrics. Registers the
 * following meters lazily on first observation (so the registry stays empty until hooks actually
 * fire):
 *
 * <ul>
 *   <li>{@code kairo.hooks.fired.total} — counter: in-process hook firings (tags: phase, decision)
 *   <li>{@code kairo.hooks.failures.total} — counter: hook failures (tags: phase, kind= {@code
 *       in_process} | {@code external}, errorType)
 *   <li>{@code kairo.hooks.duration} — timer: hook handler wall-clock duration (tags: phase,
 *       outcome={@code success} | {@code failure})
 * </ul>
 *
 * <p>Bridging is push-based: register a single instance via {@code
 * HookChainObserver.setGlobal(metrics)} at app startup. The instance is thread-safe (delegates to
 * Micrometer's thread-safe registry).
 */
public final class HookChainMetrics implements HookChainObserver {

    static final String FIRED_TOTAL = "kairo.hooks.fired.total";
    static final String FAILURES_TOTAL = "kairo.hooks.failures.total";
    static final String DURATION = "kairo.hooks.duration";

    private final MeterRegistry registry;

    public HookChainMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Convenience: instantiate and install as the process-wide {@link HookChainObserver}. Returns
     * the instance so callers can keep a reference for unit tests or shutdown.
     */
    public static HookChainMetrics install(MeterRegistry registry) {
        HookChainMetrics metrics = new HookChainMetrics(registry);
        HookChainObserver.setGlobal(metrics);
        return metrics;
    }

    @Override
    public void onHookFired(String phase, String decision, Duration duration) {
        Counter.builder(FIRED_TOTAL)
                .tag("phase", nullSafe(phase))
                .tag("decision", nullSafe(decision))
                .register(registry)
                .increment();
        Timer.builder(DURATION)
                .tag("phase", nullSafe(phase))
                .tag("outcome", "success")
                .register(registry)
                .record(duration);
    }

    @Override
    public void onHookFailed(String phase, Throwable error, Duration duration) {
        Counter.builder(FAILURES_TOTAL)
                .tag("phase", nullSafe(phase))
                .tag("kind", "in_process")
                .tag("errorType", error == null ? "unknown" : error.getClass().getSimpleName())
                .register(registry)
                .increment();
        Timer.builder(DURATION)
                .tag("phase", nullSafe(phase))
                .tag("outcome", "failure")
                .register(registry)
                .record(duration);
    }

    @Override
    public void onExternalHookFailure(String phase, String hookId, Throwable error) {
        Counter.builder(FAILURES_TOTAL)
                .tag("phase", nullSafe(phase))
                .tag("kind", "external")
                .tag("errorType", error == null ? "unknown" : error.getClass().getSimpleName())
                .register(registry)
                .increment();
        // No duration tag for external failures — the chain doesn't have an authoritative
        // wall-clock
        // figure when the failure surfaces (the executor may have timed out internally).
    }

    private static String nullSafe(String s) {
        return s == null || s.isEmpty() ? "unknown" : s;
    }
}
