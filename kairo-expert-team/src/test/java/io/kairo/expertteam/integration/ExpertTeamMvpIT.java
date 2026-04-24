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
package io.kairo.expertteam.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.event.KairoEvent;
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
import io.kairo.expertteam.AgentEvaluationStrategy;
import io.kairo.expertteam.ExpertTeamCoordinator;
import io.kairo.expertteam.SimpleEvaluationStrategy;
import io.kairo.expertteam.internal.DefaultPlanner;
import io.kairo.expertteam.tck.NoopMessageBus;
import io.kairo.expertteam.tck.RecordingEventBus;
import io.kairo.expertteam.tck.StubAgent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * End-to-end MVP integration tests for the v0.10 Expert Team orchestration pipeline (ADR-015).
 *
 * <p>These exercise the full {@link ExpertTeamCoordinator} pipeline against multi-agent teams,
 * realistic planner output (default roles derived from agents), and the {@link RecordingEventBus}
 * to validate end-to-end event ordering, state transitions, and terminal-status mapping across the
 * six MVP scenarios enumerated in the v0.10 kickoff plan:
 *
 * <ol>
 *   <li>Happy path — multi-agent sequential plan runs to {@link TeamStatus#COMPLETED}.
 *   <li>Feedback loop — REVISE → REVISE → PASS terminates within budget.
 *   <li>Evaluator crash — MEDIUM risk → FAILED; LOW risk → DEGRADED + warning.
 *   <li>Team timeout — {@link TeamConfig#teamTimeout()} breach yields {@link TeamStatus#TIMEOUT}
 *       with partial outcomes preserved.
 *   <li>Event domain isolation — every event emitted sits on {@link KairoEvent#DOMAIN_TEAM}.
 *   <li>Module boundary — the {@code io.kairo.expertteam} package only references {@code
 *       io.kairo.api.*}, {@code io.kairo.expertteam.*}, {@code java.*}, {@code javax.*}, {@code
 *       org.slf4j.*} and {@code reactor.*} — no leaks from {@code io.kairo.multiagent}, {@code
 *       io.kairo.mcp}, etc.
 * </ol>
 *
 * <p>These live under the {@code integration} JUnit tag so the Surefire default excludes them; they
 * run via {@code mvn verify -Pintegration-tests} or the Failsafe phase.
 *
 * @since v0.10 (Experimental)
 */
@Tag("integration")
final class ExpertTeamMvpIT {

    private static TeamConfig config(
            RiskProfile risk, int maxRounds, Duration timeout, EvaluatorPreference pref) {
        return new TeamConfig(
                risk,
                maxRounds,
                timeout,
                pref,
                PlannerFailureMode.FAIL_FAST,
                TeamResourceConstraint.unbounded());
    }

    // ------------------------------------------------------------------ #1 happy path

    @Test
    void happyPath_multiAgentPlanReachesCompleted() {
        RecordingEventBus bus = new RecordingEventBus();
        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(
                        bus, new SimpleEvaluationStrategy(), null, new DefaultPlanner());

        StubAgent scribe = StubAgent.fixed("scribe", "outline: introduction + 3 sections");
        StubAgent critic = StubAgent.fixed("critic", "verdict: structure is sound");
        StubAgent editor = StubAgent.fixed("editor", "polished: final copy v1");
        Team team = new Team("content-team", List.of(scribe, critic, editor), new NoopMessageBus());

        TeamResult result =
                coordinator
                        .execute(
                                new TeamExecutionRequest(
                                        "req-happy",
                                        "draft and polish a v0.10 release announcement",
                                        Map.of(),
                                        config(
                                                RiskProfile.MEDIUM,
                                                3,
                                                Duration.ofSeconds(10L),
                                                EvaluatorPreference.SIMPLE)),
                                team)
                        .block(Duration.ofSeconds(15L));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.COMPLETED);
        assertThat(result.stepOutcomes()).hasSize(3);
        assertThat(result.stepOutcomes())
                .allSatisfy(
                        s ->
                                assertThat(s.finalVerdict().outcome())
                                        .isIn(
                                                VerdictOutcome.PASS,
                                                VerdictOutcome.AUTO_PASS_WITH_WARNING));
        // Each agent used exactly once in a fresh happy path (no retries).
        assertThat(scribe.invocationCount()).isEqualTo(1);
        assertThat(critic.invocationCount()).isEqualTo(1);
        assertThat(editor.invocationCount()).isEqualTo(1);

        List<String> types = bus.teamEventTypes();
        assertThat(types).first().isEqualTo(TeamEventType.TEAM_STARTED.name());
        assertThat(types).last().isEqualTo(TeamEventType.TEAM_COMPLETED.name());
        // Three steps ⇒ three STEP_ASSIGNED + three STEP_COMPLETED + three EVALUATION_RESULT.
        assertThat(types.stream().filter(TeamEventType.STEP_ASSIGNED.name()::equals).count())
                .isEqualTo(3);
        assertThat(types.stream().filter(TeamEventType.STEP_COMPLETED.name()::equals).count())
                .isEqualTo(3);
        assertThat(types.stream().filter(TeamEventType.EVALUATION_RESULT.name()::equals).count())
                .isEqualTo(3);
    }

    // ------------------------------------------------------------------ #2 feedback loop

    @Test
    void feedbackLoop_reviseTwiceThenPassRetainsArtifactAndAttempts() {
        RecordingEventBus bus = new RecordingEventBus();
        AtomicInteger calls = new AtomicInteger();
        EvaluationStrategy sequenced =
                ctx ->
                        Mono.fromSupplier(
                                () -> {
                                    int n = calls.incrementAndGet();
                                    if (n < 3) {
                                        return new EvaluationVerdict(
                                                VerdictOutcome.REVISE,
                                                0.4,
                                                "tighten argument",
                                                List.of("add citation"),
                                                Instant.now());
                                    }
                                    return new EvaluationVerdict(
                                            VerdictOutcome.PASS,
                                            0.95,
                                            "landed",
                                            List.of(),
                                            Instant.now());
                                });

        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(bus, sequenced, null, new DefaultPlanner());

        StubAgent scribe = StubAgent.fixed("scribe", "draft-v1");
        Team team = new Team("loop-team", List.of(scribe), new NoopMessageBus());

        TeamResult result =
                coordinator
                        .execute(
                                new TeamExecutionRequest(
                                        "req-loop",
                                        "iterate a tight draft",
                                        Map.of(),
                                        config(
                                                RiskProfile.MEDIUM,
                                                3,
                                                Duration.ofSeconds(10L),
                                                EvaluatorPreference.SIMPLE)),
                                team)
                        .block(Duration.ofSeconds(15L));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.COMPLETED);
        assertThat(result.stepOutcomes()).hasSize(1);
        assertThat(result.stepOutcomes().get(0).attempts()).isEqualTo(3);
        assertThat(scribe.invocationCount()).isEqualTo(3);

        List<String> types = bus.teamEventTypes();
        // Three attempts ⇒ three EVALUATION_STARTED + three EVALUATION_RESULT.
        assertThat(types.stream().filter(TeamEventType.EVALUATION_STARTED.name()::equals).count())
                .isEqualTo(3);
        assertThat(types.stream().filter(TeamEventType.EVALUATION_RESULT.name()::equals).count())
                .isEqualTo(3);
        assertThat(types).last().isEqualTo(TeamEventType.TEAM_COMPLETED.name());
    }

    // ------------------------------------------------------------------ #3 evaluator crash

    @Test
    void evaluatorCrash_mediumRiskTerminatesFailed() {
        RecordingEventBus bus = new RecordingEventBus();
        EvaluationStrategy crashing =
                new AgentEvaluationStrategy(
                        ctx -> Mono.error(new RuntimeException("judge exploded")));

        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(bus, crashing, null, new DefaultPlanner());

        Team team =
                new Team(
                        "crash-medium",
                        List.of(StubAgent.fixed("scribe", "draft")),
                        new NoopMessageBus());

        TeamResult result =
                coordinator
                        .execute(
                                new TeamExecutionRequest(
                                        "req-crash-medium",
                                        "goal",
                                        Map.of(),
                                        config(
                                                RiskProfile.MEDIUM,
                                                2,
                                                Duration.ofSeconds(10L),
                                                EvaluatorPreference.SIMPLE)),
                                team)
                        .block(Duration.ofSeconds(15L));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.FAILED);
        assertThat(bus.teamEventTypes()).last().isEqualTo(TeamEventType.TEAM_FAILED.name());
    }

    @Test
    void evaluatorCrash_lowRiskDegradesWithWarning() {
        RecordingEventBus bus = new RecordingEventBus();
        EvaluationStrategy crashing =
                new AgentEvaluationStrategy(
                        ctx -> Mono.error(new RuntimeException("judge exploded")));

        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(bus, crashing, null, new DefaultPlanner());

        Team team =
                new Team(
                        "crash-low",
                        List.of(StubAgent.fixed("scribe", "draft")),
                        new NoopMessageBus());

        TeamResult result =
                coordinator
                        .execute(
                                new TeamExecutionRequest(
                                        "req-crash-low",
                                        "goal",
                                        Map.of(),
                                        config(
                                                RiskProfile.LOW,
                                                2,
                                                Duration.ofSeconds(10L),
                                                EvaluatorPreference.SIMPLE)),
                                team)
                        .block(Duration.ofSeconds(15L));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.DEGRADED);
        assertThat(result.warnings()).isNotEmpty();
        assertThat(bus.teamEventTypes()).last().isEqualTo(TeamEventType.TEAM_COMPLETED.name());
    }

    // ------------------------------------------------------------------ #4 team timeout

    @Test
    void teamTimeout_slowAgentYieldsTimeoutStatusWithPartialOutcomes() {
        RecordingEventBus bus = new RecordingEventBus();

        // Agent that sleeps longer than teamTimeout, forcing Mono#timeout to kick in.
        StubAgent slow =
                new StubAgent(
                        "slow-writer",
                        msg ->
                                Mono.delay(Duration.ofSeconds(5L))
                                        .map(
                                                tick ->
                                                        io.kairo.api.message.Msg.of(
                                                                io.kairo.api.message.MsgRole
                                                                        .ASSISTANT,
                                                                "never-reaches")));
        Team team = new Team("timeout-team", List.of(slow), new NoopMessageBus());

        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(
                        bus, new SimpleEvaluationStrategy(), null, new DefaultPlanner());

        TeamResult result =
                coordinator
                        .execute(
                                new TeamExecutionRequest(
                                        "req-timeout",
                                        "goal",
                                        Map.of(),
                                        config(
                                                RiskProfile.MEDIUM,
                                                3,
                                                Duration.ofMillis(200L),
                                                EvaluatorPreference.SIMPLE)),
                                team)
                        .block(Duration.ofSeconds(10L));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.TIMEOUT);
        assertThat(result.warnings()).isNotEmpty();
        assertThat(result.finalOutput()).isEmpty();
        assertThat(bus.teamEventTypes()).last().isEqualTo(TeamEventType.TEAM_TIMEOUT.name());
    }

    // ------------------------------------------------------------------ #5 event domain isolation

    @Test
    void eventDomainIsolation_onlyTeamDomainEventsEmitted() {
        RecordingEventBus bus = new RecordingEventBus();
        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(
                        bus, new SimpleEvaluationStrategy(), null, new DefaultPlanner());

        Team team =
                new Team(
                        "iso-team",
                        List.of(StubAgent.fixed("scribe", "draft")),
                        new NoopMessageBus());

        coordinator
                .execute(
                        new TeamExecutionRequest(
                                "req-iso",
                                "goal",
                                Map.of(),
                                config(
                                        RiskProfile.MEDIUM,
                                        1,
                                        Duration.ofSeconds(10L),
                                        EvaluatorPreference.SIMPLE)),
                        team)
                .block(Duration.ofSeconds(15L));

        List<KairoEvent> recorded = bus.recorded();
        assertThat(recorded).isNotEmpty();
        assertThat(recorded)
                .as("coordinator must only publish on the team domain")
                .allSatisfy(e -> assertThat(e.domain()).isEqualTo(KairoEvent.DOMAIN_TEAM));
        assertThat(recorded)
                .as("no event should accidentally leak into execution/evolution/security domains")
                .noneSatisfy(
                        e ->
                                assertThat(e.domain())
                                        .isIn(
                                                KairoEvent.DOMAIN_EXECUTION,
                                                KairoEvent.DOMAIN_EVOLUTION,
                                                KairoEvent.DOMAIN_SECURITY));
    }

    // ------------------------------------------------------------------ #6 module boundary

    @Test
    void moduleBoundary_expertTeamDoesNotReferenceForeignModules() throws Exception {
        java.nio.file.Path src =
                java.nio.file.Paths.get("src/main/java/io/kairo/expertteam").toAbsolutePath();
        assertThat(java.nio.file.Files.isDirectory(src))
                .as("expert-team source root must exist for boundary check")
                .isTrue();

        List<String> forbidden =
                List.of(
                        "io.kairo.multiagent",
                        "io.kairo.mcp",
                        "io.kairo.evolution",
                        "io.kairo.observability",
                        "io.kairo.tools",
                        "io.kairo.spring");

        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(src)) {
            walk.filter(p -> p.toString().endsWith(".java"))
                    .forEach(
                            path -> {
                                try {
                                    String body = java.nio.file.Files.readString(path);
                                    for (String pkg : forbidden) {
                                        assertThat(body)
                                                .as(
                                                        "file %s must not reference forbidden"
                                                                + " package %s",
                                                        path, pkg)
                                                .doesNotContain(pkg);
                                    }
                                } catch (java.io.IOException ioe) {
                                    throw new AssertionError("Unable to read " + path, ioe);
                                }
                            });
        }
    }
}
