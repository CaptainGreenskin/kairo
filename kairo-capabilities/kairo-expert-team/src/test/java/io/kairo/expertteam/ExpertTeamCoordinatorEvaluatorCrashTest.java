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

import io.kairo.api.team.EvaluationStrategy;
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
import java.util.List;
import org.junit.jupiter.api.Test;

final class ExpertTeamCoordinatorEvaluatorCrashTest {

    @Test
    void mediumRiskEvaluatorCrashFailsTeam() {
        RecordingEventBus bus = new RecordingEventBus();
        EvaluationStrategy crashing =
                new AgentEvaluationStrategy(
                        ctx ->
                                reactor.core.publisher.Mono.error(
                                        new RuntimeException("judge died")));
        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(bus, crashing, null, new DefaultPlanner());

        StubAgent agent = StubAgent.fixed("scribe", "content");
        Team team = new Team("t", List.of(agent), new NoopMessageBus());

        TeamResult result =
                coordinator
                        .execute(
                                new TeamExecutionRequest(
                                        "req-crash-med",
                                        "ship it",
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
        assertThat(bus.teamEventTypes())
                .filteredOn(TeamEventType.EVALUATION_RESULT.name()::equals)
                .singleElement();
    }

    @Test
    void lowRiskEvaluatorCrashYieldsDegraded() {
        RecordingEventBus bus = new RecordingEventBus();
        EvaluationStrategy crashing =
                new AgentEvaluationStrategy(
                        ctx ->
                                reactor.core.publisher.Mono.error(
                                        new RuntimeException("judge died")));
        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(bus, crashing, null, new DefaultPlanner());

        StubAgent agent = StubAgent.fixed("scribe", "content");
        Team team = new Team("t", List.of(agent), new NoopMessageBus());

        TeamResult result =
                coordinator
                        .execute(
                                new TeamExecutionRequest(
                                        "req-crash-low",
                                        "ship it",
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
        assertThat(result.stepOutcomes()).hasSize(1);
        assertThat(result.stepOutcomes().get(0).finalVerdict().outcome())
                .isEqualTo(VerdictOutcome.REVIEW_EXCEEDED);
        assertThat(bus.teamEventTypes()).last().isEqualTo(TeamEventType.TEAM_COMPLETED.name());
    }
}
