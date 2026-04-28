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
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a {@link KairoEventBus} to guarantee at-least-once delivery via the outbox pattern.
 *
 * <p>Every call to {@link #publish} first persists the event in the {@link OutboxStore} as PENDING,
 * then attempts an immediate in-process publish. If the immediate publish succeeds, the entry is
 * marked DELIVERED. If it throws, the entry stays PENDING for the {@link OutboxPoller} to retry
 * later.
 */
public final class TransactionalOutboxPublisher implements KairoEventBus {

    private static final Logger log = LoggerFactory.getLogger(TransactionalOutboxPublisher.class);

    private final KairoEventBus delegate;
    private final InMemoryOutboxStore store;

    public TransactionalOutboxPublisher(KairoEventBus delegate, InMemoryOutboxStore store) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public void publish(KairoEvent event) {
        OutboxEntry entry = OutboxEntry.pending(event.eventType(), new byte[0]);
        store.save(entry);
        try {
            delegate.publish(event);
            store.markDelivered(entry.id());
        } catch (Exception ex) {
            log.warn(
                    "Immediate publish failed for outbox entry {}; will retry via poller",
                    entry.id(),
                    ex);
        }
    }

    @Override
    public reactor.core.publisher.Flux<KairoEvent> subscribe() {
        return delegate.subscribe();
    }

    @Override
    public reactor.core.publisher.Flux<KairoEvent> subscribe(String domain) {
        return delegate.subscribe(domain);
    }

    /** Visible for {@link OutboxPoller} to re-publish a pending entry. */
    KairoEventBus delegate() {
        return delegate;
    }

    /** Visible for {@link OutboxPoller} to access the store. */
    InMemoryOutboxStore store() {
        return store;
    }
}
