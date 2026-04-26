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
import java.util.Map;
import java.util.Objects;

/**
 * Immutable result of a {@link ResourceConstraint} validation.
 *
 * @param violated whether the constraint is violated
 * @param reason human-readable explanation (empty string when not violated)
 * @param metrics constraint-specific telemetry (e.g., tokens remaining, cost accrued)
 */
@Stable(value = "Resource validation result; shape frozen since v0.8", since = "1.0.0")
public record ResourceValidation(boolean violated, String reason, Map<String, Object> metrics) {

    public ResourceValidation {
        Objects.requireNonNull(reason, "reason must not be null");
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
    }

    /** Create a non-violated validation result. */
    public static ResourceValidation ok() {
        return new ResourceValidation(false, "", Map.of());
    }

    /**
     * Create a violated validation result.
     *
     * @param reason human-readable explanation
     * @param metrics constraint-specific telemetry
     * @return a violated validation result
     */
    public static ResourceValidation violated(String reason, Map<String, Object> metrics) {
        return new ResourceValidation(true, reason, metrics);
    }
}
