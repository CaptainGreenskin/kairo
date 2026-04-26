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
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Structured message exchanged when the coordinator hands off control from one role to another.
 *
 * <p>Emitted alongside a {@link TeamEventType#HANDOFF} event so observability consumers can
 * reconstruct inter-role communication independently of the agent-to-agent {@code MessageBus}.
 *
 * @param fromRoleId role that is releasing control; non-null, non-blank
 * @param toRoleId role that is receiving control; non-null, non-blank
 * @param payload opaque message body (typically the previous step's artifact); non-null
 * @param metadata free-form attribute map for observability; defensively copied, never {@code null}
 * @param sentAt when the handoff occurred; non-null
 * @since v0.10 (Experimental)
 */
@Experimental("Team handoff message; introduced in v0.10, targeting stabilization in v1.1")
public record HandoffMessage(
        String fromRoleId,
        String toRoleId,
        String payload,
        Map<String, Object> metadata,
        Instant sentAt) {

    public HandoffMessage {
        requireNonBlank(fromRoleId, "fromRoleId");
        requireNonBlank(toRoleId, "toRoleId");
        Objects.requireNonNull(payload, "payload must not be null");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        Objects.requireNonNull(sentAt, "sentAt must not be null");
    }

    private static void requireNonBlank(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(paramName + " must not be null or blank");
        }
    }
}
