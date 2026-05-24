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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Write-ahead publisher: persists every {@link KairoEvent} to the {@link InMemoryOutboxStore}
 * before attempting delivery to the {@link KairoEventBus}.
 *
 * <p>If the bus call succeeds synchronously the entry is immediately marked DELIVERED. If the call
 * throws the entry remains PENDING for the {@link OutboxPoller} to retry.
 */
public final class TransactionalOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(TransactionalOutboxPublisher.class);

    private final KairoEventBus delegate;
    private final InMemoryOutboxStore store;

    public TransactionalOutboxPublisher(KairoEventBus delegate, InMemoryOutboxStore store) {
        this.delegate = delegate;
        this.store = store;
    }

    /**
     * Persist the event to the outbox store, then attempt immediate delivery.
     *
     * <p>Never throws — bus failures are swallowed and the entry remains for poller retry.
     */
    public void publish(KairoEvent event) {
        OutboxEntry entry = OutboxEntry.pending(event);
        store.save(entry);
        try {
            delegate.publish(event);
            store.markDelivered(entry.id());
        } catch (Exception ex) {
            log.warn(
                    "Outbox: immediate delivery failed for event {}; will retry via poller",
                    entry.id(),
                    ex);
        }
    }
}
