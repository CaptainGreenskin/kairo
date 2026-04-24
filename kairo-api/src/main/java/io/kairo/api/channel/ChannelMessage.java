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
package io.kairo.api.channel;

import io.kairo.api.Experimental;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Transport-agnostic envelope for a message crossing a {@link Channel}. Carries just enough
 * structure for Kairo to dispatch to an agent / team without assuming a specific IM platform's data
 * model.
 *
 * <p>Binary attachments, reactions, and reply threading are out of scope for v0.9 — adapters render
 * those into {@link #attributes()} if they need to preserve them round-trip.
 *
 * @param id unique message id, defaults to a UUID if the adapter does not supply one
 * @param identity originator + return address (see {@link ChannelIdentity})
 * @param content raw textual payload (UTF-8); adapters that ingest rich content flatten it here
 * @param timestamp when the message was observed by the adapter
 * @param attributes opaque metadata the adapter captured (e.g. IM message id, reply-to id, tenant);
 *     immutable
 * @since v0.9 (Experimental)
 */
@Experimental("Channel SPI — contract may change in v0.10")
public record ChannelMessage(
        String id,
        ChannelIdentity identity,
        String content,
        Instant timestamp,
        Map<String, String> attributes) {

    public ChannelMessage {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(timestamp, "timestamp");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /** Shorthand that generates a UUID and {@code Instant.now()} for the non-identity fields. */
    public static ChannelMessage of(ChannelIdentity identity, String content) {
        return new ChannelMessage(
                UUID.randomUUID().toString(), identity, content, Instant.now(), Map.of());
    }
}
