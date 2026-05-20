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
package io.kairo.expertteam.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.team.EvaluationVerdict;
import io.kairo.api.team.EvaluationVerdict.VerdictOutcome;
import io.kairo.api.team.RoleDefinition;
import io.kairo.api.team.TeamExecutionPlan;
import io.kairo.api.team.TeamResult;
import io.kairo.api.team.TeamResult.StepOutcome;
import io.kairo.api.team.TeamStatus;
import io.kairo.api.team.TeamStep;
import io.kairo.expertteam.strategy.PlanVerificationVerdict.VerificationOutcome;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DefaultPlanVerifierTest {

    private static final String GOAL = "Implement user authentication";
    private static final RoleDefinition CODER_ROLE =
            new RoleDefinition("coder", "Coder", "Write code", "coding", List.of());

    private final DefaultPlanVerifier verifier = new DefaultPlanVerifier();

    @Test
    void allStepsPassReturnsVerified() {
        TeamExecutionPlan plan = planWith("step-1", "step-2");
        TeamResult result =
                TeamResult.of(
                        "req-1",
                        TeamStatus.COMPLETED,
                        List.of(passOutcome("step-1"), passOutcome("step-2")),
                        "final output",
                        Duration.ofSeconds(10),
                        List.of());

        PlanVerificationVerdict verdict = verifier.verify(plan, result, GOAL);

        assertThat(verdict.outcome()).isEqualTo(VerificationOutcome.VERIFIED);
        assertThat(verdict.isSuccess()).isTrue();
        assertThat(verdict.reason()).contains("2 steps");
        assertThat(verdict.issues()).isEmpty();
    }

    @Test
    void singleStepPlanVerified() {
        TeamExecutionPlan plan = planWith("step-1");
        TeamResult result =
                TeamResult.of(
                        "req-1",
                        TeamStatus.COMPLETED,
                        List.of(passOutcome("step-1")),
                        "done",
                        Duration.ofSeconds(5),
                        List.of());

        PlanVerificationVerdict verdict = verifier.verify(plan, result, GOAL);

        assertThat(verdict.outcome()).isEqualTo(VerificationOutcome.VERIFIED);
        assertThat(verdict.reason()).contains("1 steps");
    }

    @Test
    void missingStepOutcomeReportsIssue() {
        TeamExecutionPlan plan = planWith("step-1", "step-2", "step-3");
        TeamResult result =
                TeamResult.of(
                        "req-1",
                        TeamStatus.COMPLETED,
                        List.of(passOutcome("step-1"), passOutcome("step-3")),
                        "partial output",
                        Duration.ofSeconds(10),
                        List.of());

        PlanVerificationVerdict verdict = verifier.verify(plan, result, GOAL);

        assertThat(verdict.outcome()).isEqualTo(VerificationOutcome.PARTIAL);
        assertThat(verdict.issues())
                .anyMatch(i -> i.contains("step-2") && i.contains("not executed"));
    }

    @Test
    void reviewExceededStepCausesFailed() {
        TeamExecutionPlan plan = planWith("step-1");
        StepOutcome exceeded = reviewExceededOutcome("step-1", 3);
        TeamResult result =
                TeamResult.of(
                        "req-1",
                        TeamStatus.COMPLETED,
                        List.of(exceeded),
                        "some output",
                        Duration.ofSeconds(10),
                        List.of());

        PlanVerificationVerdict verdict = verifier.verify(plan, result, GOAL);

        assertThat(verdict.outcome()).isEqualTo(VerificationOutcome.FAILED);
        assertThat(verdict.issues())
                .anyMatch(i -> i.contains("step-1") && i.contains("REVIEW_EXCEEDED"));
    }

    @Test
    void nonCompletedStatusReportsIssue() {
        TeamExecutionPlan plan = planWith("step-1");
        TeamResult result =
                TeamResult.of(
                        "req-1",
                        TeamStatus.FAILED,
                        List.of(passOutcome("step-1")),
                        "output",
                        Duration.ofSeconds(5),
                        List.of());

        PlanVerificationVerdict verdict = verifier.verify(plan, result, GOAL);

        assertThat(verdict.outcome()).isNotEqualTo(VerificationOutcome.VERIFIED);
        assertThat(verdict.issues()).anyMatch(i -> i.contains("FAILED") && i.contains("COMPLETED"));
    }

    @Test
    void missingFinalOutputReportsIssue() {
        TeamExecutionPlan plan = planWith("step-1");
        TeamResult result =
                TeamResult.withoutOutput(
                        "req-1",
                        TeamStatus.COMPLETED,
                        List.of(passOutcome("step-1")),
                        Duration.ofSeconds(5),
                        List.of());

        PlanVerificationVerdict verdict = verifier.verify(plan, result, GOAL);

        assertThat(verdict.outcome()).isEqualTo(VerificationOutcome.FAILED);
        assertThat(verdict.issues()).anyMatch(i -> i.contains("No final output"));
    }

    @Test
    void blankFinalOutputReportsIssue() {
        TeamExecutionPlan plan = planWith("step-1");
        TeamResult result =
                TeamResult.of(
                        "req-1",
                        TeamStatus.COMPLETED,
                        List.of(passOutcome("step-1")),
                        "   ",
                        Duration.ofSeconds(5),
                        List.of());

        PlanVerificationVerdict verdict = verifier.verify(plan, result, GOAL);

        assertThat(verdict.outcome()).isNotEqualTo(VerificationOutcome.VERIFIED);
        assertThat(verdict.issues()).anyMatch(i -> i.contains("blank"));
    }

    @Test
    void partialWhenStatusBadButHasOutputAndNoExceeded() {
        TeamExecutionPlan plan = planWith("step-1");
        TeamResult result =
                TeamResult.of(
                        "req-1",
                        TeamStatus.CANCELLED,
                        List.of(passOutcome("step-1")),
                        "some output",
                        Duration.ofSeconds(30),
                        List.of());

        PlanVerificationVerdict verdict = verifier.verify(plan, result, GOAL);

        assertThat(verdict.outcome()).isEqualTo(VerificationOutcome.PARTIAL);
        assertThat(verdict.reason()).contains("warnings");
    }

    @Test
    void multipleIssuesCombined() {
        TeamExecutionPlan plan = planWith("step-1", "step-2");
        StepOutcome exceeded = reviewExceededOutcome("step-1", 3);
        TeamResult result =
                TeamResult.withoutOutput(
                        "req-1",
                        TeamStatus.FAILED,
                        List.of(exceeded),
                        Duration.ofSeconds(10),
                        List.of());

        PlanVerificationVerdict verdict = verifier.verify(plan, result, GOAL);

        assertThat(verdict.outcome()).isEqualTo(VerificationOutcome.FAILED);
        assertThat(verdict.issues()).hasSizeGreaterThanOrEqualTo(3);
    }

    // ---------------------------------------------------------------------- helpers

    private static TeamExecutionPlan planWith(String... stepIds) {
        List<TeamStep> steps = new java.util.ArrayList<>();
        for (int i = 0; i < stepIds.length; i++) {
            steps.add(new TeamStep(stepIds[i], "Do " + stepIds[i], CODER_ROLE, List.of(), i));
        }
        return new TeamExecutionPlan("plan-1", steps, Instant.now());
    }

    private static StepOutcome passOutcome(String stepId) {
        EvaluationVerdict verdict =
                new EvaluationVerdict(
                        VerdictOutcome.PASS, 0.9, "Looks good", List.of(), Instant.now());
        return new StepOutcome(stepId, "generated output for " + stepId, verdict, 1);
    }

    private static StepOutcome reviewExceededOutcome(String stepId, int attempts) {
        EvaluationVerdict verdict =
                new EvaluationVerdict(
                        VerdictOutcome.REVIEW_EXCEEDED,
                        0.2,
                        "Too many revisions",
                        List.of("fix this"),
                        Instant.now());
        return new StepOutcome(stepId, "last attempt output", verdict, attempts);
    }
}
