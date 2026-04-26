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
package io.kairo.expertteam;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.team.EvaluationContext;
import io.kairo.api.team.EvaluationStrategy;
import io.kairo.api.team.EvaluationVerdict;
import io.kairo.api.team.EvaluationVerdict.VerdictOutcome;
import io.kairo.api.team.EvaluatorPreference;
import io.kairo.api.team.PlannerFailureMode;
import io.kairo.api.team.RiskProfile;
import io.kairo.api.team.Team;
import io.kairo.api.team.TeamConfig;
import io.kairo.api.team.TeamEventType;
import io.kairo.api.team.TeamExecutionRequest;
import io.kairo.api.team.TeamResourceConstraint;
import io.kairo.api.team.TeamResult;
import io.kairo.api.team.TeamStatus;
import io.kairo.expertteam.internal.DefaultPlanner;
import io.kairo.expertteam.tck.NoopMessageBus;
import io.kairo.expertteam.tck.RecordingEventBus;
import io.kairo.expertteam.tck.StubAgent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

final class ExpertTeamCoordinatorFeedbackLoopTest {

    @Test
    void reviseRevisePassWithinBudgetTerminatesCompletedWithThreeAttempts() {
        RecordingEventBus bus = new RecordingEventBus();
        AtomicInteger calls = new AtomicInteger();

        EvaluationStrategy sequencedStrategy =
                ctx ->
                        Mono.fromSupplier(
                                () -> {
                                    int n = calls.incrementAndGet();
                                    if (n < 3) {
                                        return new EvaluationVerdict(
                                                VerdictOutcome.REVISE,
                                                0.3,
                                                "needs more detail",
                                                List.of("expand"),
                                                Instant.now());
                                    }
                                    return new EvaluationVerdict(
                                            VerdictOutcome.PASS,
                                            1.0,
                                            "good now",
                                            List.of(),
                                            Instant.now());
                                });

        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(bus, sequencedStrategy, null, new DefaultPlanner());

        StubAgent agent = StubAgent.fixed("scribe", "draft-output");
        Team team = new Team("t", List.of(agent), new NoopMessageBus());

        TeamResult result =
                coordinator
                        .execute(
                                new TeamExecutionRequest(
                                        "req-loop",
                                        "ship a feature",
                                        java.util.Map.of(),
                                        new TeamConfig(
                                                RiskProfile.MEDIUM,
                                                3,
                                                Duration.ofSeconds(5L),
                                                EvaluatorPreference.SIMPLE,
                                                PlannerFailureMode.FAIL_FAST,
                                                TeamResourceConstraint.unbounded())),
                                team)
                        .block(Duration.ofSeconds(10L));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.COMPLETED);
        assertThat(result.stepOutcomes()).hasSize(1);
        assertThat(result.stepOutcomes().get(0).attempts()).isEqualTo(3);
        assertThat(result.stepOutcomes().get(0).finalVerdict().outcome())
                .isEqualTo(VerdictOutcome.PASS);
        // Agent should have been called once per attempt.
        assertThat(agent.invocationCount()).isEqualTo(3);

        assertThat(bus.teamEventTypes())
                .contains(TeamEventType.EVALUATION_RESULT.name())
                .last()
                .isEqualTo(TeamEventType.TEAM_COMPLETED.name());
    }

    @Test
    void reviseExhaustsBudgetUnderMediumYieldsFailed() {
        RecordingEventBus bus = new RecordingEventBus();
        EvaluationStrategy alwaysRevise =
                ctx ->
                        Mono.just(
                                new EvaluationVerdict(
                                        VerdictOutcome.REVISE,
                                        0.1,
                                        "not good enough",
                                        List.of("try harder"),
                                        Instant.now()));

        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(bus, alwaysRevise, null, new DefaultPlanner());

        StubAgent agent = StubAgent.fixed("scribe", "draft");
        Team team = new Team("t", List.of(agent), new NoopMessageBus());

        TeamResult result =
                coordinator
                        .execute(
                                new TeamExecutionRequest(
                                        "req-fail",
                                        "goal",
                                        java.util.Map.of(),
                                        new TeamConfig(
                                                RiskProfile.MEDIUM,
                                                2,
                                                Duration.ofSeconds(5L),
                                                EvaluatorPreference.SIMPLE,
                                                PlannerFailureMode.FAIL_FAST,
                                                TeamResourceConstraint.unbounded())),
                                team)
                        .block(Duration.ofSeconds(10L));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.FAILED);
        assertThat(bus.teamEventTypes()).last().isEqualTo(TeamEventType.TEAM_FAILED.name());
    }

    @Test
    void reviseExhaustsBudgetUnderLowRiskYieldsDegraded() {
        RecordingEventBus bus = new RecordingEventBus();
        EvaluationStrategy alwaysRevise =
                ctx ->
                        Mono.just(
                                new EvaluationVerdict(
                                        VerdictOutcome.REVISE,
                                        0.1,
                                        "not good enough",
                                        List.of("try harder"),
                                        Instant.now()));

        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(bus, alwaysRevise, null, new DefaultPlanner());

        StubAgent agent = StubAgent.fixed("scribe", "draft");
        Team team = new Team("t", List.of(agent), new NoopMessageBus());

        TeamResult result =
                coordinator
                        .execute(
                                new TeamExecutionRequest(
                                        "req-degraded",
                                        "goal",
                                        java.util.Map.of(),
                                        new TeamConfig(
                                                RiskProfile.LOW,
                                                2,
                                                Duration.ofSeconds(5L),
                                                EvaluatorPreference.SIMPLE,
                                                PlannerFailureMode.FAIL_FAST,
                                                TeamResourceConstraint.unbounded())),
                                team)
                        .block(Duration.ofSeconds(10L));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.DEGRADED);
        assertThat(result.warnings()).isNotEmpty();
        assertThat(bus.teamEventTypes()).last().isEqualTo(TeamEventType.TEAM_COMPLETED.name());
    }

    @Test
    void generatorReceivesPriorVerdictsOnRevise() {
        RecordingEventBus bus = new RecordingEventBus();
        AtomicInteger attemptsSeen = new AtomicInteger();

        EvaluationStrategy reviseThenPass =
                ctx -> {
                    EvaluationContext snapshot = ctx;
                    if (snapshot.attemptNumber() == 1) {
                        assertThat(snapshot.priorVerdicts()).isEmpty();
                        return Mono.just(
                                new EvaluationVerdict(
                                        VerdictOutcome.REVISE,
                                        0.2,
                                        "add concrete examples",
                                        List.of("cite figures"),
                                        Instant.now()));
                    }
                    attemptsSeen.incrementAndGet();
                    assertThat(snapshot.priorVerdicts()).hasSize(1);
                    assertThat(snapshot.priorVerdicts().get(0).outcome())
                            .isEqualTo(VerdictOutcome.REVISE);
                    return Mono.just(
                            new EvaluationVerdict(
                                    VerdictOutcome.PASS, 1.0, "ok", List.of(), Instant.now()));
                };

        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(bus, reviseThenPass, null, new DefaultPlanner());

        StubAgent agent = StubAgent.fixed("scribe", "draft");
        Team team = new Team("t", List.of(agent), new NoopMessageBus());

        TeamResult result =
                coordinator
                        .execute(
                                new TeamExecutionRequest(
                                        "req-prior",
                                        "goal",
                                        java.util.Map.of(),
                                        new TeamConfig(
                                                RiskProfile.MEDIUM,
                                                3,
                                                Duration.ofSeconds(5L),
                                                EvaluatorPreference.SIMPLE,
                                                PlannerFailureMode.FAIL_FAST,
                                                TeamResourceConstraint.unbounded())),
                                team)
                        .block(Duration.ofSeconds(10L));

        assertThat(result.status()).isEqualTo(TeamStatus.COMPLETED);
        assertThat(attemptsSeen.get()).isEqualTo(1);
    }
}
