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
package io.kairo.api.event;

import io.kairo.api.Experimental;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Unified event envelope published on the {@link KairoEventBus}.
 *
 * <p>Rather than forcing every emitting subsystem into a sealed hierarchy, Kairo uses a
 * domain-tagged envelope. Each domain keeps its own strongly-typed event record (for example {@code
 * ExecutionEvent} or {@code SecurityEvent}) and publishes to the bus by wrapping itself in a {@code
 * KairoEvent}. Subscribers can filter by {@link #domain()} or {@link #eventType()}.
 *
 * <p>Domains currently in use (reserved):
 *
 * <ul>
 *   <li>{@code execution} — execution-log lifecycle (model / tool / compaction / iteration)
 *   <li>{@code evolution} — self-evolution skill governance lifecycle
 *   <li>{@code security} — guardrail decision lifecycle
 *   <li>{@code team} — multi-agent orchestration lifecycle (Expert Team, v0.10+)
 * </ul>
 *
 * @param eventId unique id for the envelope; caller may reuse the underlying domain event id
 * @param timestamp envelope publication timestamp (UTC)
 * @param domain domain tag, lowercase ASCII, no spaces
 * @param eventType type discriminator within the domain (usually enum name)
 * @param payload optional original domain event (for strongly-typed subscribers)
 * @param attributes free-form attribute map for observability backends
 * @since v0.10 (Experimental)
 */
@Experimental("KairoEventBus — contract may change in v0.11")
public record KairoEvent(
        String eventId,
        Instant timestamp,
        String domain,
        String eventType,
        @Nullable Object payload,
        Map<String, Object> attributes) {

    /** Execution domain tag. */
    public static final String DOMAIN_EXECUTION = "execution";

    /** Evolution domain tag. */
    public static final String DOMAIN_EVOLUTION = "evolution";

    /** Security domain tag. */
    public static final String DOMAIN_SECURITY = "security";

    /** Team orchestration domain tag. */
    public static final String DOMAIN_TEAM = "team";

    public KairoEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(domain, "domain must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /**
     * Convenience constructor for simple publishers that do not need to retain a strongly-typed
     * payload.
     */
    public static KairoEvent of(String domain, String eventType, Map<String, Object> attributes) {
        return new KairoEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                domain,
                eventType,
                null,
                attributes == null ? Map.of() : attributes);
    }

    /**
     * Convenience constructor used when bridging an existing domain event into the bus.
     *
     * @param domain domain tag
     * @param eventType type discriminator (enum name is common)
     * @param payload original domain event object (kept by reference)
     */
    public static KairoEvent wrap(String domain, String eventType, Object payload) {
        return new KairoEvent(
                UUID.randomUUID().toString(), Instant.now(), domain, eventType, payload, Map.of());
    }
}
