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
package io.kairo.core.model.anthropic;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Fires a cancel callback if no stream chunk arrives within idleTimeoutMs.
 *
 * <p>Reset on every chunk; cancel on idle expiry. Env-configurable via {@code
 * KAIRO_STREAM_IDLE_TIMEOUT_MS} (default 60 000 ms).
 *
 * <p>Typical usage in an SSE processing loop:
 *
 * <pre>{@code
 * StreamIdleWatchdog watchdog = new StreamIdleWatchdog(() -> call.cancel());
 * try {
 *     watchdog.reset();
 *     while (hasMoreEvents()) {
 *         SseEvent event = readNextEvent();
 *         watchdog.reset();
 *         process(event);
 *     }
 *     watchdog.disarm();
 * } finally {
 *     watchdog.close();
 * }
 * }</pre>
 *
 * <p>For Reactor-based streams, prefer {@code Flux.timeout(Duration)} which provides equivalent
 * idle detection without manual timer management. This class exists for imperative SSE loops and as
 * a testable unit for timeout semantics.
 */
public final class StreamIdleWatchdog implements AutoCloseable {

    private static final long DEFAULT_IDLE_MS = 60_000L;

    /**
     * Resolved idle timeout in milliseconds from {@code KAIRO_STREAM_IDLE_TIMEOUT_MS}. Invalid or
     * zero/negative values fall back to the default.
     */
    public static final long IDLE_TIMEOUT_MS =
            resolveEnv("KAIRO_STREAM_IDLE_TIMEOUT_MS", DEFAULT_IDLE_MS);

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread t = new Thread(r, "kairo-stream-watchdog");
                        t.setDaemon(true);
                        return t;
                    });

    private final Runnable onTimeout;
    private volatile ScheduledFuture<?> pending;

    public StreamIdleWatchdog(Runnable onTimeout) {
        this.onTimeout = onTimeout;
    }

    /** Call on every received SSE chunk to reset the idle timer. */
    public void reset() {
        cancel();
        pending = SCHEDULER.schedule(onTimeout, IDLE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    /** Permanently disarm (call when stream completes normally). */
    public void disarm() {
        cancel();
    }

    @Override
    public void close() {
        disarm();
    }

    private void cancel() {
        ScheduledFuture<?> f = pending;
        if (f != null) f.cancel(false);
    }

    private static long resolveEnv(String name, long defaultValue) {
        String val = System.getenv(name);
        if (val == null || val.isBlank()) return defaultValue;
        try {
            long parsed = Long.parseLong(val.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
