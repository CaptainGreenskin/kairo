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
package io.kairo.api.execution;

import io.kairo.api.Stable;
import java.time.Instant;
import java.util.Objects;

/**
 * An immutable event envelope recorded in a durable execution's event log.
 *
 * <p>Each event captures a discrete step (model call, tool call, compaction, etc.) with a hash for
 * integrity verification during recovery.
 *
 * @param eventId unique identifier for this event
 * @param eventType the type of event
 * @param timestamp when the event was recorded
 * @param payloadJson canonical JSON payload
 * @param eventHash SHA-256 hash for event-chain integrity verification
 * @param schemaVersion schema version of the payload format
 * @since v0.8 (promoted to @Stable in v1.0.0)
 */
@Stable(
        value = "Execution event envelope; shape frozen since v0.8, promoted post-v0.9 GA",
        since = "1.0.0")
public record ExecutionEvent(
        String eventId,
        ExecutionEventType eventType,
        Instant timestamp,
        String payloadJson,
        String eventHash,
        int schemaVersion) {

    public ExecutionEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(payloadJson, "payloadJson must not be null");
        Objects.requireNonNull(eventHash, "eventHash must not be null");
    }
}
