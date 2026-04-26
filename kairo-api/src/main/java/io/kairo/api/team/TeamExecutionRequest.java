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
import java.util.Map;
import java.util.Objects;

/**
 * Request envelope handed to a {@link TeamCoordinator} to start a single team execution.
 *
 * @param requestId caller-chosen correlation id surfaced in every emitted event; non-null,
 *     non-blank
 * @param goal user-facing goal statement; non-null, non-blank
 * @param context free-form attribute map (prior conversation state, domain hints, etc.);
 *     defensively copied, never {@code null}
 * @param config configuration bundle driving this execution; non-null
 * @since v0.10 (Experimental)
 */
@Experimental(
        "Team execution request envelope; introduced in v0.10, targeting stabilization in v1.1")
public record TeamExecutionRequest(
        String requestId, String goal, Map<String, Object> context, TeamConfig config) {

    public TeamExecutionRequest {
        requireNonBlank(requestId, "requestId");
        requireNonBlank(goal, "goal");
        context = context == null ? Map.of() : Map.copyOf(context);
        Objects.requireNonNull(config, "config must not be null");
    }

    private static void requireNonBlank(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(paramName + " must not be null or blank");
        }
    }
}
