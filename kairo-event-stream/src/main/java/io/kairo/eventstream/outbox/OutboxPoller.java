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
package io.kairo.eventstream.outbox;

import io.kairo.api.event.KairoEventBus;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Polls {@link InMemoryOutboxStore} at a fixed rate and re-delivers PENDING entries.
 *
 * <p>Lifecycle: call {@link #start()} to begin polling; call {@link #stop()} (or use
 * try-with-resources) to shut down. The poller is idempotent — calling {@code start()} twice is
 * safe.
 */
public final class OutboxPoller implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private static final int DEFAULT_BATCH_SIZE = 50;
    private static final long POLL_INTERVAL_MS = 100L;

    private final InMemoryOutboxStore store;
    private final KairoEventBus bus;
    private final int maxRetries;
    private final int batchSize;

    private final ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> task;
    private volatile boolean started;

    public OutboxPoller(InMemoryOutboxStore store, KairoEventBus bus) {
        this(store, bus, 3, DEFAULT_BATCH_SIZE);
    }

    public OutboxPoller(
            InMemoryOutboxStore store, KairoEventBus bus, int maxRetries, int batchSize) {
        this.store = store;
        this.bus = bus;
        this.maxRetries = maxRetries;
        this.batchSize = batchSize;
        this.scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "kairo-outbox-poller");
                            t.setDaemon(true);
                            return t;
                        });
    }

    public synchronized void start() {
        if (started) return;
        started = true;
        task =
                scheduler.scheduleWithFixedDelay(
                        this::pollNow, POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        log.info(
                "OutboxPoller started (pollInterval={}ms, batchSize={})",
                POLL_INTERVAL_MS,
                batchSize);
    }

    public synchronized void stop() {
        if (!started) return;
        started = false;
        ScheduledFuture<?> t = task;
        if (t != null) t.cancel(false);
        scheduler.shutdown();
        log.info("OutboxPoller stopped");
    }

    @Override
    public void close() {
        stop();
    }

    /** Exposed for testing — runs one poll cycle synchronously. */
    public void pollNow() {
        List<OutboxEntry> pending = store.pollPending(batchSize);
        for (OutboxEntry entry : pending) {
            try {
                bus.publish(entry.event());
                store.markDelivered(entry.id());
            } catch (Exception ex) {
                store.incrementRetries(entry.id());
                int retries = store.retries(entry.id());
                if (retries >= maxRetries) {
                    log.error(
                            "Outbox: max retries ({}) exceeded for event {}; marking FAILED",
                            maxRetries,
                            entry.id(),
                            ex);
                    store.markFailed(entry.id());
                } else {
                    log.warn(
                            "Outbox: delivery attempt {} failed for event {}; will retry",
                            retries,
                            entry.id(),
                            ex);
                }
            }
        }
    }
}
