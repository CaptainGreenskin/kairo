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
import java.util.Objects;

/**
 * Caller-provided configuration bundle for a single {@link TeamExecutionRequest}.
 *
 * <p>Collects the knobs that ADR-015 exposes as policy-configurable: risk posture, feedback-loop
 * cap, team timeout, evaluator preference, planner failure mode, and team-level resource budget.
 *
 * @param riskProfile risk posture governing evaluator-crash semantics; non-null
 * @param maxFeedbackRounds maximum revise loops per step; must be {@code >= 1}
 * @param teamTimeout overall wall-clock cap for the whole team execution; must be positive
 * @param evaluatorPreference hint for picking between simple / agent evaluators; non-null
 * @param plannerFailureMode how to react to a failed planning phase; non-null
 * @param resourceConstraint team-level resource budget; non-null
 * @since v0.10 (Experimental)
 */
@Experimental("Team config record; introduced in v0.10, targeting stabilization in v1.1")
public record TeamConfig(
        RiskProfile riskProfile,
        int maxFeedbackRounds,
        Duration teamTimeout,
        EvaluatorPreference evaluatorPreference,
        PlannerFailureMode plannerFailureMode,
        TeamResourceConstraint resourceConstraint) {

    public TeamConfig {
        Objects.requireNonNull(riskProfile, "riskProfile must not be null");
        if (maxFeedbackRounds < 1) {
            throw new IllegalArgumentException(
                    "maxFeedbackRounds must be >= 1, got " + maxFeedbackRounds);
        }
        Objects.requireNonNull(teamTimeout, "teamTimeout must not be null");
        if (teamTimeout.toMillis() <= 0) {
            throw new IllegalArgumentException("teamTimeout must be > 0ms, got " + teamTimeout);
        }
        Objects.requireNonNull(evaluatorPreference, "evaluatorPreference must not be null");
        Objects.requireNonNull(plannerFailureMode, "plannerFailureMode must not be null");
        Objects.requireNonNull(resourceConstraint, "resourceConstraint must not be null");
    }

    /**
     * Sensible default configuration for exploratory usage: {@link RiskProfile#MEDIUM} risk, three
     * feedback rounds, a ten-minute team timeout, {@link EvaluatorPreference#AUTO}, {@link
     * PlannerFailureMode#FAIL_FAST}, and an {@link TeamResourceConstraint#unbounded()} resource
     * budget.
     */
    public static TeamConfig defaults() {
        return new TeamConfig(
                RiskProfile.MEDIUM,
                3,
                Duration.ofMinutes(10L),
                EvaluatorPreference.AUTO,
                PlannerFailureMode.FAIL_FAST,
                TeamResourceConstraint.unbounded());
    }
}
