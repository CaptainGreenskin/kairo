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
import java.util.List;
import java.util.Objects;

/**
 * A single step within a {@link TeamExecutionPlan}.
 *
 * <p>Each step is assigned a {@link RoleDefinition} at plan time and may declare dependencies on
 * other step IDs. Steps with no unresolved dependencies are eligible for generation.
 *
 * @param stepId stable identifier for the step; non-null, non-blank
 * @param description human-readable description of the work; non-null, non-blank
 * @param assignedRole the role bound to this step; non-null
 * @param dependsOn step IDs that must complete first; defensively copied, never {@code null}
 * @param stepIndex the zero-based position of this step in the plan; must be {@code >= 0}
 * @since v0.10 (Experimental)
 */
@Experimental("Team plan step; introduced in v0.10, targeting stabilization in v1.1")
public record TeamStep(
        String stepId,
        String description,
        RoleDefinition assignedRole,
        List<String> dependsOn,
        int stepIndex) {

    public TeamStep {
        requireNonBlank(stepId, "stepId");
        requireNonBlank(description, "description");
        Objects.requireNonNull(assignedRole, "assignedRole must not be null");
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        if (stepIndex < 0) {
            throw new IllegalArgumentException("stepIndex must be >= 0, got " + stepIndex);
        }
    }

    private static void requireNonBlank(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(paramName + " must not be null or blank");
        }
    }
}
