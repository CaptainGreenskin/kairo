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

import io.kairo.api.agent.Agent;
import io.kairo.api.event.KairoEvent;
import io.kairo.api.team.EvaluatorPreference;
import io.kairo.api.team.PlannerFailureMode;
import io.kairo.api.team.RiskProfile;
import io.kairo.api.team.Team;
import io.kairo.api.team.TeamConfig;
import io.kairo.api.team.TeamCoordinator;
import io.kairo.api.team.TeamEventType;
import io.kairo.api.team.TeamExecutionRequest;
import io.kairo.api.team.TeamResourceConstraint;
import io.kairo.api.team.TeamResult;
import io.kairo.api.team.TeamStatus;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Abstract contract test kit for {@link TeamCoordinator} implementations (ADR-016).
 *
 * <p>Third-party implementors extend this class and provide a coordinator under test plus a minimal
 * {@link Team}. The TCK asserts the lifecycle invariants baked into ADR-015:
 *
 * <ul>
 *   <li>Happy path: {@link TeamStatus#COMPLETED} with all {@link TeamResult#stepOutcomes()}
 *       populated.
 *   <li>Failure path: a crashing agent yields {@link TeamStatus#FAILED} and exactly one terminal
 *       event ({@code TEAM_FAILED}).
 *   <li>Timeout path: a tiny {@code teamTimeout} yields {@link TeamStatus#TIMEOUT} and exactly one
 *       {@code TEAM_TIMEOUT} event.
 *   <li>Event order invariant: {@code TEAM_STARTED} precedes every {@code STEP_*} event; the
 *       terminal event is emitted exactly once and is the last team-domain event.
 * </ul>
 *
 * @since v0.10 (Experimental)
 */
public abstract class TeamCoordinatorTCK {

    /** The coordinator under test — bound to the {@link #eventBus()} provided by this TCK. */
    protected abstract TeamCoordinator coordinatorUnderTest();

    /**
     * A {@link Team} with at least one non-failing agent for the happy-path test.
     *
     * @param agent the agent to include in the team (supplied by the test sub-class harness)
     */
    protected abstract Team happyPathTeam(Agent agent);

    /** A {@link Team} whose single agent throws on invocation (drives the failure-path test). */
    protected abstract Team failingTeam(Agent failingAgent);

    /**
     * The recording event bus the coordinator is configured with. TCK consumers typically wire a
     * fresh {@link RecordingEventBus} per test method.
     */
    protected abstract RecordingEventBus eventBus();

    // ------------------------------------------------------------------ happy path

    @Test
    public void happyPath_completesWithAllStepOutcomes() {
        StubAgent agent = StubAgent.fixed("scribe", "ok-output");
        Team team = happyPathTeam(agent);

        TeamExecutionRequest request = defaultRequest("happy-1", "summarise the changelog");
        TeamResult result = coordinatorUnderTest().execute(request, team).block(testBlockTimeout());

        assertThat(result).isNotNull();
        assertThat(result.status())
                .as(
                        "happy path must terminate COMPLETED (or DEGRADED if implementation surfaced"
                                + " warnings)")
                .isIn(TeamStatus.COMPLETED, TeamStatus.DEGRADED);
        assertThat(result.stepOutcomes()).as("stepOutcomes must be populated").isNotEmpty();
        assertThat(result.stepOutcomes()).allSatisfy(o -> assertThat(o.output()).isNotNull());
        assertThat(result.finalOutput()).isPresent();

        assertTerminalOrderInvariants(TeamEventType.TEAM_COMPLETED);
    }

    // ------------------------------------------------------------------ failure path

    @Test
    public void failurePath_completesWithStatusFailed() {
        StubAgent failingAgent =
                StubAgent.failing("scribe", new RuntimeException("agent exploded"));
        Team team = failingTeam(failingAgent);

        TeamExecutionRequest request =
                new TeamExecutionRequest(
                        "fail-1",
                        "make something",
                        java.util.Map.of(),
                        new TeamConfig(
                                RiskProfile.MEDIUM,
                                1,
                                Duration.ofSeconds(5L),
                                EvaluatorPreference.SIMPLE,
                                PlannerFailureMode.FAIL_FAST,
                                TeamResourceConstraint.unbounded()));
        TeamResult result = coordinatorUnderTest().execute(request, team).block(testBlockTimeout());

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.FAILED);
        assertTerminalOrderInvariants(TeamEventType.TEAM_FAILED);
    }

    // ------------------------------------------------------------------ timeout path

    @Test
    public void timeoutPath_completesWithPartialResult() {
        StubAgent slow =
                new StubAgent(
                        "slow",
                        msg ->
                                reactor.core.publisher.Mono.just(
                                                io.kairo.api.message.Msg.of(
                                                        io.kairo.api.message.MsgRole.ASSISTANT,
                                                        "eventually"))
                                        .delayElement(Duration.ofSeconds(2L)));
        Team team = happyPathTeam(slow);

        TeamExecutionRequest request =
                new TeamExecutionRequest(
                        "timeout-1",
                        "do something slow",
                        java.util.Map.of(),
                        new TeamConfig(
                                RiskProfile.MEDIUM,
                                1,
                                Duration.ofMillis(50L),
                                EvaluatorPreference.SIMPLE,
                                PlannerFailureMode.FAIL_FAST,
                                TeamResourceConstraint.unbounded()));
        TeamResult result = coordinatorUnderTest().execute(request, team).block(testBlockTimeout());

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.TIMEOUT);
        assertTerminalOrderInvariants(TeamEventType.TEAM_TIMEOUT);
    }

    // ------------------------------------------------------------------ helpers

    /** Default block timeout for TCK tests (kept large to tolerate slower CI machines). */
    protected Duration testBlockTimeout() {
        return Duration.ofSeconds(10L);
    }

    /** Default request for the happy path test. */
    protected TeamExecutionRequest defaultRequest(String requestId, String goal) {
        return new TeamExecutionRequest(
                requestId,
                goal,
                java.util.Map.of(),
                new TeamConfig(
                        RiskProfile.MEDIUM,
                        2,
                        Duration.ofSeconds(5L),
                        EvaluatorPreference.SIMPLE,
                        PlannerFailureMode.FAIL_FAST,
                        TeamResourceConstraint.unbounded()));
    }

    private void assertTerminalOrderInvariants(TeamEventType expectedTerminal) {
        List<KairoEvent> team = eventBus().recordedTeamEvents();
        List<String> types = eventBus().teamEventTypes();

        assertThat(team).as("team domain events must be recorded").isNotEmpty();
        assertThat(types)
                .as("TEAM_STARTED must be the first team-domain event")
                .first()
                .isEqualTo(TeamEventType.TEAM_STARTED.name());

        // Terminal event: exactly one of TEAM_COMPLETED / TEAM_FAILED / TEAM_TIMEOUT.
        Set<String> terminals =
                Set.of(
                        TeamEventType.TEAM_COMPLETED.name(),
                        TeamEventType.TEAM_FAILED.name(),
                        TeamEventType.TEAM_TIMEOUT.name());
        long terminalCount = types.stream().filter(terminals::contains).count();
        assertThat(terminalCount).as("exactly one terminal event must be emitted").isEqualTo(1L);

        assertThat(types)
                .as("terminal event must be the last team-domain event")
                .last()
                .isEqualTo(expectedTerminal.name());

        // TEAM_STARTED must precede any STEP_* event.
        int startedIdx = types.indexOf(TeamEventType.TEAM_STARTED.name());
        for (int i = 0; i < types.size(); i++) {
            String t = types.get(i);
            if (t.startsWith("STEP_") || t.startsWith("EVALUATION_")) {
                assertThat(i)
                        .as("STEP_/EVALUATION_ event must come after TEAM_STARTED")
                        .isGreaterThan(startedIdx);
            }
        }
    }
}
