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
import io.kairo.expertteam.tck.NoopMessageBus;
import io.kairo.expertteam.tck.RecordingEventBus;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exercises the two {@link PlannerFailureMode} branches by feeding an empty team (no agents, no
 * configured roles) to {@link ExpertTeamCoordinator}.
 */
final class ExpertTeamCoordinatorPlannerFailureTest {

    @Test
    void failFastModeReturnsFailedWhenPlannerThrows() {
        RecordingEventBus bus = new RecordingEventBus();
        ExpertTeamCoordinator coordinator = new ExpertTeamCoordinator(bus);

        Team empty = new Team("empty", List.of(), new NoopMessageBus());
        TeamResult result =
                coordinator
                        .execute(
                                new TeamExecutionRequest(
                                        "req-plan-fail",
                                        "do a thing",
                                        java.util.Map.of(),
                                        new TeamConfig(
                                                RiskProfile.MEDIUM,
                                                2,
                                                Duration.ofSeconds(5L),
                                                EvaluatorPreference.SIMPLE,
                                                PlannerFailureMode.FAIL_FAST,
                                                TeamResourceConstraint.unbounded())),
                                empty)
                        .block(Duration.ofSeconds(10L));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.FAILED);
        assertThat(bus.teamEventTypes()).last().isEqualTo(TeamEventType.TEAM_FAILED.name());
    }

    @Test
    void singleStepFallbackStillFailsWhenNoAgentsAtAll() {
        RecordingEventBus bus = new RecordingEventBus();
        ExpertTeamCoordinator coordinator = new ExpertTeamCoordinator(bus);

        Team empty = new Team("empty", List.of(), new NoopMessageBus());
        TeamResult result =
                coordinator
                        .execute(
                                new TeamExecutionRequest(
                                        "req-plan-fallback",
                                        "do a thing",
                                        java.util.Map.of(),
                                        new TeamConfig(
                                                RiskProfile.MEDIUM,
                                                2,
                                                Duration.ofSeconds(5L),
                                                EvaluatorPreference.SIMPLE,
                                                PlannerFailureMode.SINGLE_STEP_FALLBACK,
                                                TeamResourceConstraint.unbounded())),
                                empty)
                        .block(Duration.ofSeconds(10L));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.FAILED);
        assertThat(result.warnings()).anyMatch(w -> w.toLowerCase().contains("planner"));
        assertThat(bus.teamEventTypes()).last().isEqualTo(TeamEventType.TEAM_FAILED.name());
    }
}
