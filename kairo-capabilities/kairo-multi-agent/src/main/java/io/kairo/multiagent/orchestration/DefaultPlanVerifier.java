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
package io.kairo.multiagent.orchestration;

import io.kairo.api.team.EvaluationVerdict;
import io.kairo.api.team.TeamExecutionPlan;
import io.kairo.api.team.TeamResult;
import io.kairo.api.team.TeamStatus;
import io.kairo.api.team.TeamStep;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic plan verifier that checks structural correctness of execution against the plan:
 *
 * <ul>
 *   <li>All planned steps have corresponding outcomes
 *   <li>No step ended with REVIEW_EXCEEDED verdict
 *   <li>Team status is COMPLETED (not FAILED/CANCELLED/TIMED_OUT)
 *   <li>Final output is present
 * </ul>
 */
public final class DefaultPlanVerifier implements PlanVerificationStrategy {

    @Override
    public PlanVerificationVerdict verify(
            TeamExecutionPlan plan, TeamResult result, String originalGoal) {
        List<String> issues = new ArrayList<>();

        checkTeamStatus(result, issues);
        checkStepCoverage(plan, result, issues);
        checkStepVerdicts(result, issues);
        checkFinalOutput(result, issues);

        if (issues.isEmpty()) {
            return PlanVerificationVerdict.verified(
                    "All " + plan.totalSteps() + " steps executed successfully");
        }

        long failedSteps =
                result.stepOutcomes().stream()
                        .filter(
                                so ->
                                        so.finalVerdict().outcome()
                                                == EvaluationVerdict.VerdictOutcome.REVIEW_EXCEEDED)
                        .count();
        boolean hasOutput = result.finalOutput().isPresent();

        if (failedSteps == 0 && hasOutput) {
            return PlanVerificationVerdict.partial(
                    "Plan executed with warnings: " + issues.size() + " issue(s)", issues);
        }

        return PlanVerificationVerdict.failed(
                "Plan verification failed: " + issues.size() + " issue(s)", issues);
    }

    private void checkTeamStatus(TeamResult result, List<String> issues) {
        if (result.status() != TeamStatus.COMPLETED) {
            issues.add("Team status is " + result.status() + ", expected COMPLETED");
        }
    }

    private void checkStepCoverage(TeamExecutionPlan plan, TeamResult result, List<String> issues) {
        Set<String> plannedStepIds = new HashSet<>();
        for (TeamStep step : plan.steps()) {
            plannedStepIds.add(step.stepId());
        }

        Set<String> executedStepIds = new HashSet<>();
        for (TeamResult.StepOutcome outcome : result.stepOutcomes()) {
            executedStepIds.add(outcome.stepId());
        }

        for (String plannedId : plannedStepIds) {
            if (!executedStepIds.contains(plannedId)) {
                issues.add("Planned step '" + plannedId + "' was not executed");
            }
        }
    }

    private void checkStepVerdicts(TeamResult result, List<String> issues) {
        for (TeamResult.StepOutcome outcome : result.stepOutcomes()) {
            if (outcome.finalVerdict().outcome()
                    == EvaluationVerdict.VerdictOutcome.REVIEW_EXCEEDED) {
                issues.add(
                        "Step '"
                                + outcome.stepId()
                                + "' ended with REVIEW_EXCEEDED after "
                                + outcome.attempts()
                                + " attempt(s)");
            }
        }
    }

    private void checkFinalOutput(TeamResult result, List<String> issues) {
        if (result.finalOutput().isEmpty()) {
            issues.add("No final output produced");
        } else if (result.finalOutput().get().isBlank()) {
            issues.add("Final output is blank");
        }
    }
}
