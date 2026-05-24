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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory {@link OutboxEntry} store backed by a {@link ConcurrentLinkedDeque} (FIFO order) plus a
 * {@link ConcurrentHashMap} for O(1) status updates.
 *
 * <p>Suitable for single-JVM deployments and testing. A multi-node deployment should replace this
 * with a JDBC or Redis-backed store.
 */
public final class InMemoryOutboxStore {

    private final ConcurrentLinkedDeque<UUID> queue = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<UUID, OutboxEntry> store = new ConcurrentHashMap<>();

    /** Persist a new entry. The entry must be in {@code PENDING} status. */
    public void save(OutboxEntry entry) {
        store.put(entry.id(), entry);
        queue.addLast(entry.id());
    }

    /**
     * Return up to {@code limit} PENDING entries in insertion order without removing them from the
     * store.
     */
    public List<OutboxEntry> pollPending(int limit) {
        List<OutboxEntry> result = new ArrayList<>(limit);
        Iterator<UUID> it = queue.iterator();
        while (it.hasNext() && result.size() < limit) {
            UUID id = it.next();
            OutboxEntry entry = store.get(id);
            if (entry != null && entry.status() == OutboxEntry.Status.PENDING) {
                result.add(entry);
            }
        }
        return result;
    }

    /** Mark an entry as DELIVERED and remove it from the pending queue. */
    public void markDelivered(UUID id) {
        store.compute(id, (k, e) -> e == null ? null : e.withStatus(OutboxEntry.Status.DELIVERED));
        queue.remove(id);
    }

    /** Mark an entry as FAILED and remove it from the pending queue. */
    public void markFailed(UUID id) {
        store.compute(id, (k, e) -> e == null ? null : e.withStatus(OutboxEntry.Status.FAILED));
        queue.remove(id);
    }

    /** Update retry count for an entry (keeps it PENDING). */
    public void incrementRetries(UUID id) {
        store.compute(id, (k, e) -> e == null ? null : e.incrementRetries());
    }

    public int retries(UUID id) {
        OutboxEntry entry = store.get(id);
        return entry == null ? 0 : entry.retries();
    }

    public int pendingCount() {
        return (int)
                queue.stream()
                        .map(store::get)
                        .filter(e -> e != null && e.status() == OutboxEntry.Status.PENDING)
                        .count();
    }
}
