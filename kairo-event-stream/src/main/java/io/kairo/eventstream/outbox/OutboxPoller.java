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

import io.kairo.api.event.KairoEvent;
import io.kairo.api.event.KairoEventBus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Background poller that retries PENDING outbox entries every 100 ms.
 *
 * <p>Each entry is attempted up to {@code maxRetries} times (default 3). After all retries are
 * exhausted the entry is marked {@link OutboxEntry.Status#FAILED} and abandoned.
 *
 * <p>Lifecycle: call {@link #start()} once to activate, {@link #stop()} to shut down. Implements
 * {@link AutoCloseable} so it can be used in try-with-resources.
 */
public final class OutboxPoller implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);
    private static final int DEFAULT_POLL_BATCH = 50;
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long POLL_INTERVAL_MS = 100;

    private final InMemoryOutboxStore store;
    private final KairoEventBus bus;
    private final int maxRetries;
    private final ScheduledExecutorService scheduler;

    public OutboxPoller(InMemoryOutboxStore store, KairoEventBus bus) {
        this(store, bus, DEFAULT_MAX_RETRIES);
    }

    public OutboxPoller(InMemoryOutboxStore store, KairoEventBus bus, int maxRetries) {
        this.store = Objects.requireNonNull(store, "store");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.maxRetries = maxRetries;
        this.scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "kairo-outbox-poller");
                            t.setDaemon(true);
                            return t;
                        });
    }

    public void start() {
        scheduler.scheduleWithFixedDelay(
                this::poll, POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        log.info(
                "OutboxPoller started (interval={}ms, maxRetries={})",
                POLL_INTERVAL_MS,
                maxRetries);
    }

    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException ex) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("OutboxPoller stopped");
    }

    @Override
    public void close() {
        stop();
    }

    private void poll() {
        List<OutboxEntry> pending = store.pollPending(DEFAULT_POLL_BATCH);
        for (OutboxEntry entry : pending) {
            try {
                KairoEvent event =
                        KairoEvent.of(
                                "outbox",
                                entry.eventType(),
                                Map.of("outboxId", entry.id().toString(), "redelivery", true));
                bus.publish(event);
                store.markDelivered(entry.id());
            } catch (Exception ex) {
                int newRetries = entry.retries() + 1;
                if (newRetries >= maxRetries) {
                    store.markFailed(entry.id(), ex.getMessage());
                    log.error(
                            "Outbox entry {} permanently failed after {} retries",
                            entry.id(),
                            maxRetries,
                            ex);
                } else {
                    store.update(entry.incrementRetries());
                    log.warn("Outbox entry {} retry {}/{}", entry.id(), newRetries, maxRetries);
                }
            }
        }
    }

    /** Exposed for testing: synchronously run one poll cycle. */
    public void pollNow() {
        poll();
    }

    /** Approximate timestamp visible for health checks. */
    public Instant startedAt() {
        return Instant.now();
    }
}
