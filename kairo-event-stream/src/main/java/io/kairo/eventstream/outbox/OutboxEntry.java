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

import java.time.Instant;
import java.util.UUID;

/**
 * A durable record of a {@link io.kairo.api.event.KairoEvent} pending delivery.
 *
 * <p>The payload is a raw byte array so the outbox layer stays codec-agnostic.
 */
public record OutboxEntry(
        UUID id,
        String eventType,
        byte[] payload,
        Instant createdAt,
        OutboxEntry.Status status,
        int retries) {

    public enum Status {
        PENDING,
        DELIVERED,
        FAILED
    }

    public static OutboxEntry pending(String eventType, byte[] payload) {
        return new OutboxEntry(
                UUID.randomUUID(), eventType, payload, Instant.now(), Status.PENDING, 0);
    }

    public OutboxEntry withStatus(Status newStatus) {
        return new OutboxEntry(id, eventType, payload, createdAt, newStatus, retries);
    }

    public OutboxEntry incrementRetries() {
        return new OutboxEntry(id, eventType, payload, createdAt, status, retries + 1);
    }
}
