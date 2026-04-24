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
package io.kairo.api.team;

import io.kairo.api.Experimental;
import io.kairo.api.event.KairoEvent;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Strongly-typed domain event emitted during team orchestration.
 *
 * <p>Parallel to {@code io.kairo.api.execution.ExecutionEvent} and the evolution module's event
 * record: each domain owns its own record and publishes through the unified {@link
 * io.kairo.api.event.KairoEventBus KairoEventBus} facade by wrapping itself in a {@link KairoEvent}
 * (ADR-015 §"Event domain", ADR-018).
 *
 * <p>Publishers should use {@link #toKairoEvent()} to bridge into the bus; strongly-typed
 * subscribers recover the original record from {@link KairoEvent#payload()}.
 *
 * @param type discriminator within the team domain; non-null
 * @param teamId identifier of the {@link Team} that emitted the event; non-null, non-blank
 * @param requestId correlation id copied from the originating {@link
 *     TeamExecutionRequest#requestId()}; non-null, non-blank
 * @param timestamp when the event was recorded; non-null
 * @param attributes free-form observability attributes; defensively copied, never {@code null}
 * @since v0.10 (Experimental)
 */
@Experimental("TeamEvent — contract may change in v0.11")
public record TeamEvent(
        TeamEventType type,
        String teamId,
        String requestId,
        Instant timestamp,
        Map<String, Object> attributes) {

    public TeamEvent {
        Objects.requireNonNull(type, "type must not be null");
        requireNonBlank(teamId, "teamId");
        requireNonBlank(requestId, "requestId");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /**
     * Bridge this domain event into a {@link KairoEvent} envelope tagged with the team domain. The
     * original record is retained as the envelope payload so strongly-typed subscribers can recover
     * it via {@link KairoEvent#payload()}.
     */
    public KairoEvent toKairoEvent() {
        return KairoEvent.wrap(KairoEvent.DOMAIN_TEAM, type.name(), this);
    }

    private static void requireNonBlank(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(paramName + " must not be null or blank");
        }
    }
}
