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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory {@link OutboxStore} backed by a {@link ConcurrentLinkedDeque} for ordering and a {@link
 * ConcurrentHashMap} for O(1) status updates.
 *
 * <p>Thread-safe: concurrent producers and the poller can operate without external locking. State
 * transitions (PENDING → DELIVERED/FAILED) are atomic within the map.
 */
public final class InMemoryOutboxStore implements OutboxStore {

    private final ConcurrentLinkedDeque<UUID> queue = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<UUID, OutboxEntry> store = new ConcurrentHashMap<>();

    @Override
    public void save(OutboxEntry entry) {
        store.put(entry.id(), entry);
        queue.addLast(entry.id());
    }

    @Override
    public List<OutboxEntry> pollPending(int limit) {
        List<OutboxEntry> result = new ArrayList<>(limit);
        for (UUID id : queue) {
            if (result.size() >= limit) break;
            OutboxEntry e = store.get(id);
            if (e != null && e.status() == OutboxEntry.Status.PENDING) {
                result.add(e);
            }
        }
        return result;
    }

    @Override
    public void markDelivered(UUID id) {
        store.computeIfPresent(id, (k, e) -> e.withStatus(OutboxEntry.Status.DELIVERED));
        queue.remove(id);
    }

    @Override
    public void markFailed(UUID id, String reason) {
        store.computeIfPresent(id, (k, e) -> e.withStatus(OutboxEntry.Status.FAILED));
        queue.remove(id);
    }

    /** Updates the entry in-place (e.g., to increment retry count). */
    void update(OutboxEntry entry) {
        store.put(entry.id(), entry);
    }

    /** Returns the number of entries currently in the store (any status). */
    public int size() {
        return store.size();
    }
}
