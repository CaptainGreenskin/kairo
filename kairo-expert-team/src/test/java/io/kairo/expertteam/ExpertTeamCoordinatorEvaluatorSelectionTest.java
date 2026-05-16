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
import io.kairo.api.team.EvaluationVerdict;
import io.kairo.api.team.EvaluationVerdict.VerdictOutcome;
import io.kairo.api.team.EvaluatorPreference;
import io.kairo.api.team.PlannerFailureMode;
import io.kairo.api.team.RiskProfile;
import io.kairo.api.team.Team;
import io.kairo.api.team.TeamConfig;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * Verifies the evaluator-selection logic in {@link ExpertTeamCoordinator}: AUTO preference uses the
 * agent (LLM-judge) strategy when wired, falls back to the simple strategy otherwise, and respects
 * HIGH risk override.
 */
final class ExpertTeamCoordinatorEvaluatorSelectionTest {

    @Test
    void autoPreferenceWithAgentStrategyUsesAgentEvaluator() {
        RecordingEventBus bus = new RecordingEventBus();
        AtomicBoolean agentInvoked = new AtomicBoolean(false);

        EvaluationStrategy agentStrategy =
                ctx -> {
                    agentInvoked.set(true);
                    return Mono.just(
                            new EvaluationVerdict(
                                    VerdictOutcome.PASS,
                                    0.9,
                                    "agent-judge approved",
                                    List.of(),
                                    Instant.now()));
                };

        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(
                        bus, new SimpleEvaluationStrategy(), agentStrategy, new DefaultPlanner());

        StubAgent agent = StubAgent.fixed("coder", "implementation");
        Team team = new Team("t", List.of(agent), new NoopMessageBus());

        TeamResult result =
                coordinator
                        .execute(
                                new TeamExecutionRequest(
                                        "req-auto-agent",
                                        "implement feature",
                                        Map.of(),
                                        new TeamConfig(
                                                RiskProfile.MEDIUM,
                                                3,
                                                Duration.ofSeconds(5L),
                                                EvaluatorPreference.AUTO,
                                                PlannerFailureMode.FAIL_FAST,
                                                TeamResourceConstraint.unbounded())),
                                team)
                        .block(Duration.ofSeconds(10L));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.COMPLETED);
        assertThat(agentInvoked).isTrue();
    }

    @Test
    void autoPreferenceWithoutAgentStrategyFallsBackToSimple() {
        RecordingEventBus bus = new RecordingEventBus();

        // No agent strategy wired (null).
        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(
                        bus, new SimpleEvaluationStrategy(), null, new DefaultPlanner());

        StubAgent agent = StubAgent.fixed("coder", "implementation");
        Team team = new Team("t", List.of(agent), new NoopMessageBus());

        TeamResult result =
                coordinator
                        .execute(
                                new TeamExecutionRequest(
                                        "req-auto-fallback",
                                        "implement feature",
                                        Map.of(),
                                        new TeamConfig(
                                                RiskProfile.MEDIUM,
                                                3,
                                                Duration.ofSeconds(5L),
                                                EvaluatorPreference.AUTO,
                                                PlannerFailureMode.FAIL_FAST,
                                                TeamResourceConstraint.unbounded())),
                                team)
                        .block(Duration.ofSeconds(10L));

        // Simple strategy with non-blank artifact always passes.
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.COMPLETED);
        assertThat(result.stepOutcomes()).hasSize(1);
        assertThat(result.stepOutcomes().get(0).finalVerdict().outcome())
                .isEqualTo(VerdictOutcome.PASS);
        assertThat(result.stepOutcomes().get(0).finalVerdict().score()).isEqualTo(1.0);
    }

    @Test
    void autoPreferenceWithHighRiskForcesSimpleEvenIfAgentWired() {
        RecordingEventBus bus = new RecordingEventBus();
        AtomicBoolean agentInvoked = new AtomicBoolean(false);

        EvaluationStrategy agentStrategy =
                ctx -> {
                    agentInvoked.set(true);
                    return Mono.just(
                            new EvaluationVerdict(
                                    VerdictOutcome.PASS,
                                    0.9,
                                    "agent-judge approved",
                                    List.of(),
                                    Instant.now()));
                };

        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(
                        bus, new SimpleEvaluationStrategy(), agentStrategy, new DefaultPlanner());

        StubAgent agent = StubAgent.fixed("coder", "implementation");
        Team team = new Team("t", List.of(agent), new NoopMessageBus());

        TeamResult result =
                coordinator
                        .execute(
                                new TeamExecutionRequest(
                                        "req-auto-high-risk",
                                        "implement feature",
                                        Map.of(),
                                        new TeamConfig(
                                                RiskProfile.HIGH,
                                                3,
                                                Duration.ofSeconds(5L),
                                                EvaluatorPreference.AUTO,
                                                PlannerFailureMode.FAIL_FAST,
                                                TeamResourceConstraint.unbounded())),
                                team)
                        .block(Duration.ofSeconds(10L));

        // HIGH risk forces simple evaluator even if agent strategy is available.
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.COMPLETED);
        assertThat(agentInvoked).isFalse();
        // Simple strategy yields score 1.0 for non-blank artifacts.
        assertThat(result.stepOutcomes().get(0).finalVerdict().score()).isEqualTo(1.0);
    }

    @Test
    void defaultTeamConfigUsesAutoPreference() {
        TeamConfig defaults = TeamConfig.defaults();
        assertThat(defaults.evaluatorPreference()).isEqualTo(EvaluatorPreference.AUTO);
    }
}
