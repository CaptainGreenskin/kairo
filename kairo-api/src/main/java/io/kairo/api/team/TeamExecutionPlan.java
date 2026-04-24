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
import java.util.List;
import java.util.Objects;

/**
 * An ordered collection of {@link TeamStep}s produced by the coordinator's planner phase.
 *
 * @param planId stable identifier for the plan; non-null, non-blank
 * @param steps the steps in execution order; defensively copied, never {@code null}, never empty
 * @param createdAt when the plan was produced; non-null
 * @since v0.10 (Experimental)
 */
@Experimental("Team execution plan; introduced in v0.10, targeting stabilization in v1.1")
public record TeamExecutionPlan(String planId, List<TeamStep> steps, Instant createdAt) {

    public TeamExecutionPlan {
        requireNonBlank(planId, "planId");
        Objects.requireNonNull(steps, "steps must not be null");
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("steps must not be empty");
        }
        steps = List.copyOf(steps);
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /** Total number of steps in this plan. */
    public int totalSteps() {
        return steps.size();
    }

    private static void requireNonBlank(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(paramName + " must not be null or blank");
        }
    }
}
