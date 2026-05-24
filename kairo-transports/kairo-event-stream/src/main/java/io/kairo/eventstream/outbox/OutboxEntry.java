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
import java.time.Instant;
import java.util.UUID;

/**
 * Durable outbox record wrapping a {@link KairoEvent}.
 *
 * <p>Immutable — use {@link #withStatus(Status)} / {@link #incrementRetries()} to produce updated
 * copies.
 */
public record OutboxEntry(
        UUID id, KairoEvent event, Status status, int retries, Instant createdAt) {

    public enum Status {
        PENDING,
        DELIVERED,
        FAILED
    }

    public static OutboxEntry pending(KairoEvent event) {
        return new OutboxEntry(UUID.randomUUID(), event, Status.PENDING, 0, Instant.now());
    }

    public OutboxEntry withStatus(Status newStatus) {
        return new OutboxEntry(id, event, newStatus, retries, createdAt);
    }

    public OutboxEntry incrementRetries() {
        return new OutboxEntry(id, event, status, retries + 1, createdAt);
    }
}
