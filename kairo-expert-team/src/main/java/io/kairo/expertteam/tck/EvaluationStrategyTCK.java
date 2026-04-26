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
package io.kairo.expertteam.tck;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.team.EvaluationContext;
import io.kairo.api.team.EvaluationStrategy;
import io.kairo.api.team.EvaluationVerdict;
import io.kairo.api.team.EvaluatorPreference;
import io.kairo.api.team.PlannerFailureMode;
import io.kairo.api.team.RiskProfile;
import io.kairo.api.team.RoleDefinition;
import io.kairo.api.team.TeamConfig;
import io.kairo.api.team.TeamResourceConstraint;
import io.kairo.api.team.TeamStep;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Abstract contract test kit for {@link EvaluationStrategy} implementations (ADR-016).
 *
 * <p>Asserts the invariants ADR-015 §"Failure semantics" imposes on every evaluator:
 *
 * <ul>
 *   <li>The returned verdict is never {@code null}; {@code feedback} and {@code suggestions} are
 *       never {@code null} either (empty values acceptable).
 *   <li>The {@code score} is in {@code [0.0, 1.0]}.
 *   <li>A crash inside the rubric / judge maps to {@link
 *       EvaluationVerdict.VerdictOutcome#REVIEW_EXCEEDED} — never a silent {@code PASS}.
 * </ul>
 *
 * <p>The crash test uses {@link #crashingStrategyUnderTest()} so concrete tests can inject a
 * strategy configured to throw from its internal hook; strategies without a crashable hook can
 * return the same instance as {@link #strategyUnderTest()} and the test will be skipped only for
 * that assertion.
 *
 * @since v0.10 (Experimental)
 */
public abstract class EvaluationStrategyTCK {

    /** The evaluation strategy under test, wired with a non-crashing rubric / judge. */
    protected abstract EvaluationStrategy strategyUnderTest();

    /**
     * A variant of {@link #strategyUnderTest()} whose rubric / judge throws when invoked. Concrete
     * tests return {@code null} only if the strategy has no crashable hook at all — in which case
     * the crash-maps-to-REVIEW_EXCEEDED test is skipped with a contract warning.
     */
    protected abstract EvaluationStrategy crashingStrategyUnderTest();

    @Test
    public void nonNullContract_returnsNonNullVerdictWithNonNullFields() {
        EvaluationVerdict verdict =
                strategyUnderTest().evaluate(sampleContext("non-empty")).block(testBlockTimeout());
        assertThat(verdict).as("verdict must never be null").isNotNull();
        assertThat(verdict.outcome()).isNotNull();
        assertThat(verdict.feedback()).isNotNull();
        assertThat(verdict.suggestions()).isNotNull();
    }

    @Test
    public void scoreInRange_everyVerdictHasScoreBetweenZeroAndOneInclusive() {
        for (String artifact : List.of("hello", "", "  \n\t ", "some longer artifact text")) {
            EvaluationVerdict verdict =
                    strategyUnderTest().evaluate(sampleContext(artifact)).block(testBlockTimeout());
            assertThat(verdict).isNotNull();
            assertThat(verdict.score())
                    .as("score must be in [0.0, 1.0] for artifact: '%s'", artifact)
                    .isBetween(0.0, 1.0);
        }
    }

    @Test
    public void crashMapsToReviewExceeded() {
        EvaluationStrategy crashing = crashingStrategyUnderTest();
        if (crashing == null) {
            // Strategy has no crashable hook — skip. The contract still holds trivially because
            // there is no failure path to test.
            return;
        }
        EvaluationVerdict verdict =
                crashing.evaluate(sampleContext("anything")).block(testBlockTimeout());
        assertThat(verdict).isNotNull();
        assertThat(verdict.outcome())
                .as("crash must map to REVIEW_EXCEEDED (never silent PASS)")
                .isEqualTo(EvaluationVerdict.VerdictOutcome.REVIEW_EXCEEDED);
    }

    /** Default block timeout for TCK tests. */
    protected Duration testBlockTimeout() {
        return Duration.ofSeconds(5L);
    }

    /** Build a sample {@link EvaluationContext} around the supplied artifact text. */
    protected EvaluationContext sampleContext(String artifact) {
        RoleDefinition role =
                new RoleDefinition(
                        "scribe", "Scribe", "Write a short summary.", "text.write", List.of());
        TeamStep step = new TeamStep("step-1-scribe", "Write the summary", role, List.of(), 0);
        TeamConfig config =
                new TeamConfig(
                        RiskProfile.MEDIUM,
                        2,
                        Duration.ofSeconds(5L),
                        EvaluatorPreference.SIMPLE,
                        PlannerFailureMode.FAIL_FAST,
                        TeamResourceConstraint.unbounded());
        return new EvaluationContext(step, artifact, 1, List.of(), config);
    }
}
