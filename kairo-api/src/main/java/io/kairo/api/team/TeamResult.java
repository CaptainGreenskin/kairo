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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Terminal result of a {@link TeamCoordinator#execute(TeamExecutionRequest, Team) team execution}.
 *
 * <p>A result is always produced, even for failure / timeout / cancellation paths — the coordinator
 * never surfaces a bare {@link reactor.core.publisher.Mono#empty()}. Callers read {@link #status()}
 * to classify the terminal state and {@link #finalOutput()} for the user-consumable artifact
 * (absent on hard failures).
 *
 * @param requestId echo of {@link TeamExecutionRequest#requestId()}; non-null, non-blank
 * @param status terminal status; non-null
 * @param stepOutcomes per-step outcomes in plan order; defensively copied, never {@code null}
 * @param finalOutput the assembled output artifact, if any
 * @param totalDuration total wall-clock spent on the team; non-null
 * @param warnings human-readable warnings surfaced during execution; defensively copied, never
 *     {@code null}
 * @since v0.10 (Experimental)
 */
@Experimental("Team terminal result; introduced in v0.10, targeting stabilization in v1.1")
public record TeamResult(
        String requestId,
        TeamStatus status,
        List<StepOutcome> stepOutcomes,
        Optional<String> finalOutput,
        Duration totalDuration,
        List<String> warnings) {

    public TeamResult {
        requireNonBlank(requestId, "requestId");
        Objects.requireNonNull(status, "status must not be null");
        stepOutcomes = stepOutcomes == null ? List.of() : List.copyOf(stepOutcomes);
        Objects.requireNonNull(finalOutput, "finalOutput must not be null");
        Objects.requireNonNull(totalDuration, "totalDuration must not be null");
        if (totalDuration.isNegative()) {
            throw new IllegalArgumentException(
                    "totalDuration must not be negative, got " + totalDuration);
        }
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /**
     * Outcome of a single step within a {@link TeamResult}.
     *
     * @param stepId the step id this outcome refers to; non-null, non-blank
     * @param output the step's last generated artifact; non-null
     * @param finalVerdict the verdict that terminated the step's feedback loop; non-null
     * @param attempts total number of attempts spent on this step; must be {@code >= 1}
     */
    public record StepOutcome(
            String stepId, String output, EvaluationVerdict finalVerdict, int attempts) {

        public StepOutcome {
            requireNonBlank(stepId, "stepId");
            Objects.requireNonNull(output, "output must not be null");
            Objects.requireNonNull(finalVerdict, "finalVerdict must not be null");
            if (attempts < 1) {
                throw new IllegalArgumentException("attempts must be >= 1, got " + attempts);
            }
        }
    }

    /**
     * Builds a {@link TeamResult} when no final output artifact is available (for example, on hard
     * failure or early cancellation).
     *
     * @param requestId echo of the originating request id
     * @param status the terminal status
     * @param stepOutcomes per-step outcomes accumulated so far (may be empty)
     * @param totalDuration wall-clock duration spent
     * @param warnings human-readable warnings (may be empty)
     */
    public static TeamResult withoutOutput(
            String requestId,
            TeamStatus status,
            List<StepOutcome> stepOutcomes,
            Duration totalDuration,
            List<String> warnings) {
        return new TeamResult(
                requestId, status, stepOutcomes, Optional.empty(), totalDuration, warnings);
    }

    /**
     * Convenience constructor for the happy path. The final output is wrapped in an {@link
     * Optional} internally so {@link #finalOutput()} keeps the project's nullable-free discipline.
     */
    public static TeamResult of(
            String requestId,
            TeamStatus status,
            List<StepOutcome> stepOutcomes,
            String finalOutput,
            Duration totalDuration,
            List<String> warnings) {
        Objects.requireNonNull(finalOutput, "finalOutput must not be null");
        return new TeamResult(
                requestId, status, stepOutcomes, Optional.of(finalOutput), totalDuration, warnings);
    }

    private static void requireNonBlank(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(paramName + " must not be null or blank");
        }
    }

    /** Timestamp helper for callers that need a reproducible "now" semantics. */
    public static Instant now() {
        return Instant.now();
    }
}
