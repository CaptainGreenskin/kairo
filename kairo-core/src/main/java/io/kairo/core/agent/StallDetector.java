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
package io.kairo.core.agent;

import io.kairo.api.agent.AgentDiagnostics;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Detects agent stalls by polling {@link AgentDiagnostics#msSinceLastEvent()}.
 *
 * <p>Uses a simple interval-based check rather than reactive timeout operators. This avoids Sinks
 * multicast edge cases (unbounded buffer before subscription, replay resets) while being trivially
 * testable.
 *
 * @since 1.2.0
 */
public class StallDetector {
    private static final Logger log = LoggerFactory.getLogger(StallDetector.class);
    private static final long DEFAULT_IDLE_MS = 300_000L;
    private static final Duration CHECK_INTERVAL = Duration.ofSeconds(5);

    private final long idleThresholdMs;
    private final AgentDiagnostics diagnostics;
    private final Sinks.One<Void> stallSignal = Sinks.one();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private static final long PAUSE_HARD_LIMIT_MS = 600_000L; // 10 minutes
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private volatile long pausedAtMs = 0;
    private volatile Disposable poller;

    public StallDetector(AgentDiagnostics diagnostics) {
        this(diagnostics, resolveIdleThreshold());
    }

    public StallDetector(AgentDiagnostics diagnostics, long idleThresholdMs) {
        this.diagnostics = diagnostics;
        this.idleThresholdMs = idleThresholdMs;
    }

    /**
     * Start the polling loop. Call exactly once at agent run start.
     *
     * @throws IllegalStateException if called more than once
     */
    public void start() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("StallDetector.start() called twice");
        }
        poller =
                Flux.interval(CHECK_INTERVAL)
                        .subscribe(
                                tick -> {
                                    if (!diagnostics.running()) {
                                        dispose();
                                        return;
                                    }
                                    boolean isPaused = paused.get();
                                    if (isPaused
                                            && pausedAtMs > 0
                                            && System.currentTimeMillis() - pausedAtMs
                                                    > PAUSE_HARD_LIMIT_MS) {
                                        log.warn(
                                                "Stall detector pause exceeded hard limit ({}ms),"
                                                        + " force-resuming",
                                                PAUSE_HARD_LIMIT_MS);
                                        paused.set(false);
                                        isPaused = false;
                                    }
                                    if (isPaused
                                            || diagnostics.activeToolInvocation().isPresent()
                                            || (diagnostics instanceof DefaultAgentDiagnostics dad
                                                    && dad.isActiveModelCall())) {
                                        return;
                                    }
                                    long idle = diagnostics.msSinceLastEvent();
                                    if (idle >= idleThresholdMs) {
                                        log.warn(
                                                "Agent stall detected: {}ms since last event"
                                                        + " (threshold: {}ms)",
                                                idle,
                                                idleThresholdMs);
                                        stallSignal.tryEmitEmpty();
                                        dispose();
                                    }
                                });
    }

    /**
     * Returns a Mono that completes when a stall is detected. Never completes on the happy path
     * (agent finishes normally).
     */
    public Mono<Void> stalled() {
        return stallSignal.asMono();
    }

    /** Returns the configured idle threshold in milliseconds. */
    public long idleThresholdMs() {
        return idleThresholdMs;
    }

    /** Pause stall detection (e.g., while waiting for user approval). */
    public void pause() {
        paused.set(true);
        pausedAtMs = System.currentTimeMillis();
    }

    /** Resume stall detection. */
    public void resume() {
        paused.set(false);
    }

    /** Dispose the internal poller. Safe to call multiple times. */
    public void dispose() {
        Disposable d = poller;
        if (d != null && !d.isDisposed()) {
            d.dispose();
        }
    }

    private static long resolveIdleThreshold() {
        String env = System.getenv("KAIRO_AGENT_STALL_IDLE_MS");
        if (env != null && !env.isBlank()) {
            try {
                return Long.parseLong(env.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_IDLE_MS;
    }
}
