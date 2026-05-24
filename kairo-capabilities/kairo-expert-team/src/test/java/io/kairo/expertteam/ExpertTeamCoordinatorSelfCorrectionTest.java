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

import io.kairo.api.event.KairoEvent;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.team.EvaluationStrategy;
import io.kairo.api.team.EvaluationVerdict;
import io.kairo.api.team.EvaluationVerdict.VerdictOutcome;
import io.kairo.api.team.EvaluatorPreference;
import io.kairo.api.team.PlannerFailureMode;
import io.kairo.api.team.RiskProfile;
import io.kairo.api.team.RoleDefinition;
import io.kairo.api.team.Team;
import io.kairo.api.team.TeamConfig;
import io.kairo.api.team.TeamEvent;
import io.kairo.api.team.TeamEventType;
import io.kairo.api.team.TeamExecutionRequest;
import io.kairo.api.team.TeamResourceConstraint;
import io.kairo.api.team.TeamResult;
import io.kairo.api.team.TeamStatus;
import io.kairo.expertteam.internal.DefaultPlanner;
import io.kairo.expertteam.role.ExpertProfile;
import io.kairo.expertteam.role.ExpertRoleRegistry;
import io.kairo.expertteam.tck.NoopMessageBus;
import io.kairo.expertteam.tck.RecordingEventBus;
import io.kairo.expertteam.tck.StubAgent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * Tests for B7 self-correction escalation: model escalation and human handoff after feedback loop
 * exhaustion.
 */
final class ExpertTeamCoordinatorSelfCorrectionTest {

    private static final String AGENT_ID = "agent-scribe-001";
    private static final String ROLE_ID = AGENT_ID; // DefaultPlanner derives roleId from agent.id()
    private static final String SENIOR_MODEL = "gpt-4-turbo-senior";

    private ExpertRoleRegistry registryWithModelOverride() {
        ExpertRoleRegistry registry = new ExpertRoleRegistry();
        RoleDefinition roleDef =
                new RoleDefinition(
                        ROLE_ID, "Scribe", "You write content", "agent.default", List.of());
        ExpertProfile profile =
                new ExpertProfile(ROLE_ID, roleDef, "default", List.of(), ROLE_ID, SENIOR_MODEL);
        registry.register(ROLE_ID, profile);
        return registry;
    }

    private ExpertRoleRegistry registryWithoutModelOverride() {
        ExpertRoleRegistry registry = new ExpertRoleRegistry();
        RoleDefinition roleDef =
                new RoleDefinition(
                        ROLE_ID, "Scribe", "You write content", "agent.default", List.of());
        ExpertProfile profile =
                new ExpertProfile(ROLE_ID, roleDef, "default", List.of(), ROLE_ID, null);
        registry.register(ROLE_ID, profile);
        return registry;
    }

    private TeamConfig config(RiskProfile risk, int maxFeedbackRounds) {
        return new TeamConfig(
                risk,
                maxFeedbackRounds,
                Duration.ofSeconds(10L),
                EvaluatorPreference.SIMPLE,
                PlannerFailureMode.FAIL_FAST,
                TeamResourceConstraint.unbounded());
    }

    // ---------------------------------------------------------------
    // Test 1: maxFeedbackRounds exhausted + modelOverride set → senior model retry → PASS
    // ---------------------------------------------------------------
    @Test
    void seniorModelRetrySucceedsAfterFeedbackExhaustion() {
        RecordingEventBus bus = new RecordingEventBus();
        AtomicInteger evalCalls = new AtomicInteger();

        // Evaluation: always REVISE for first 2 calls (normal rounds), then PASS on 3rd (senior)
        EvaluationStrategy strategy =
                ctx ->
                        Mono.fromSupplier(
                                () -> {
                                    int n = evalCalls.incrementAndGet();
                                    if (n <= 2) {
                                        return new EvaluationVerdict(
                                                VerdictOutcome.REVISE,
                                                0.2,
                                                "needs improvement",
                                                List.of("fix it"),
                                                Instant.now());
                                    }
                                    // Senior model attempt passes
                                    return new EvaluationVerdict(
                                            VerdictOutcome.PASS,
                                            0.95,
                                            "excellent after escalation",
                                            List.of(),
                                            Instant.now());
                                });

        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(
                        bus, strategy, null, new DefaultPlanner(), registryWithModelOverride());

        StubAgent agent =
                new StubAgent(
                        AGENT_ID, "scribe", msg -> Mono.just(Msg.of(MsgRole.ASSISTANT, "output")));
        Team team = new Team("t", List.of(agent), new NoopMessageBus());

        TeamResult result =
                coordinator
                        .execute(
                                new TeamExecutionRequest(
                                        "req-escalate",
                                        "build feature",
                                        Map.of(),
                                        config(RiskProfile.MEDIUM, 2)),
                                team)
                        .block(Duration.ofSeconds(10L));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.COMPLETED);
        assertThat(result.stepOutcomes()).hasSize(1);
        // Senior model was attempt 3 (2 normal + 1 escalation)
        assertThat(result.stepOutcomes().get(0).attempts()).isEqualTo(3);
        assertThat(result.stepOutcomes().get(0).finalVerdict().outcome())
                .isEqualTo(VerdictOutcome.PASS);

        // Agent called 3 times: 2 normal + 1 senior
        assertThat(agent.invocationCount()).isEqualTo(3);

        // No HANDOFF event emitted since senior model succeeded
        assertThat(bus.teamEventTypes()).doesNotContain(TeamEventType.HANDOFF.name());

        // STEP_COMPLETED should be present
        assertThat(bus.teamEventTypes()).contains(TeamEventType.STEP_COMPLETED.name());
    }

    // ---------------------------------------------------------------
    // Test 2: Senior model fails → HANDOFF event emitted
    // ---------------------------------------------------------------
    @Test
    void seniorModelFailureEmitsHandoffEvent() {
        RecordingEventBus bus = new RecordingEventBus();

        // All evaluations return REVISE (including the senior model attempt)
        EvaluationStrategy alwaysRevise =
                ctx ->
                        Mono.just(
                                new EvaluationVerdict(
                                        VerdictOutcome.REVISE,
                                        0.1,
                                        "still not good enough",
                                        List.of("try harder"),
                                        Instant.now()));

        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(
                        bus, alwaysRevise, null, new DefaultPlanner(), registryWithModelOverride());

        StubAgent agent =
                new StubAgent(
                        AGENT_ID, "scribe", msg -> Mono.just(Msg.of(MsgRole.ASSISTANT, "output")));
        Team team = new Team("t", List.of(agent), new NoopMessageBus());

        TeamResult result =
                coordinator
                        .execute(
                                new TeamExecutionRequest(
                                        "req-handoff",
                                        "goal",
                                        Map.of(),
                                        config(RiskProfile.MEDIUM, 2)),
                                team)
                        .block(Duration.ofSeconds(10L));

        assertThat(result).isNotNull();
        // MEDIUM risk: should fail after escalation fails
        assertThat(result.status()).isEqualTo(TeamStatus.FAILED);

        // HANDOFF event must be present
        assertThat(bus.teamEventTypes()).contains(TeamEventType.HANDOFF.name());

        // Verify HANDOFF event attributes
        KairoEvent handoffEvent =
                bus.recordedTeamEvents().stream()
                        .filter(e -> TeamEventType.HANDOFF.name().equals(e.eventType()))
                        .findFirst()
                        .orElseThrow();
        TeamEvent teamEvent = (TeamEvent) handoffEvent.payload();
        assertThat(teamEvent.attributes()).containsEntry("requiresHuman", Boolean.TRUE);
        assertThat(teamEvent.attributes()).containsKey("feedback");
        assertThat(teamEvent.attributes()).containsEntry("stepId", "step-1-" + ROLE_ID);
        assertThat(teamEvent.attributes()).containsEntry("roleId", ROLE_ID);
        assertThat(teamEvent.attributes()).containsKey("attempts");
    }

    // ---------------------------------------------------------------
    // Test 3: No modelOverride → HANDOFF emitted immediately after maxFeedbackRounds
    // ---------------------------------------------------------------
    @Test
    void noModelOverrideEmitsHandoffImmediately() {
        RecordingEventBus bus = new RecordingEventBus();

        EvaluationStrategy alwaysRevise =
                ctx ->
                        Mono.just(
                                new EvaluationVerdict(
                                        VerdictOutcome.REVISE,
                                        0.1,
                                        "not acceptable",
                                        List.of("rewrite"),
                                        Instant.now()));

        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(
                        bus,
                        alwaysRevise,
                        null,
                        new DefaultPlanner(),
                        registryWithoutModelOverride());

        StubAgent agent =
                new StubAgent(
                        AGENT_ID, "scribe", msg -> Mono.just(Msg.of(MsgRole.ASSISTANT, "output")));
        Team team = new Team("t", List.of(agent), new NoopMessageBus());

        TeamResult result =
                coordinator
                        .execute(
                                new TeamExecutionRequest(
                                        "req-no-override",
                                        "goal",
                                        Map.of(),
                                        config(RiskProfile.MEDIUM, 2)),
                                team)
                        .block(Duration.ofSeconds(10L));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.FAILED);

        // HANDOFF should be emitted
        assertThat(bus.teamEventTypes()).contains(TeamEventType.HANDOFF.name());

        // Agent should only be called for normal feedback rounds (no senior retry)
        assertThat(agent.invocationCount()).isEqualTo(2);

        // Verify HANDOFF attributes
        KairoEvent handoffEvent =
                bus.recordedTeamEvents().stream()
                        .filter(e -> TeamEventType.HANDOFF.name().equals(e.eventType()))
                        .findFirst()
                        .orElseThrow();
        TeamEvent teamEvent = (TeamEvent) handoffEvent.payload();
        assertThat(teamEvent.attributes()).containsEntry("requiresHuman", Boolean.TRUE);
        assertThat(teamEvent.attributes())
                .containsEntry(
                        "feedback",
                        "Review budget exhausted after 2 attempt(s); last feedback: not acceptable");
        assertThat(teamEvent.attributes()).containsEntry("attempts", 2);
    }

    // ---------------------------------------------------------------
    // Test 4: No roleRegistry configured → HANDOFF emitted (same as no override)
    // ---------------------------------------------------------------
    @Test
    void noRoleRegistryConfiguredEmitsHandoffOnExhaustion() {
        RecordingEventBus bus = new RecordingEventBus();

        EvaluationStrategy alwaysRevise =
                ctx ->
                        Mono.just(
                                new EvaluationVerdict(
                                        VerdictOutcome.REVISE,
                                        0.1,
                                        "unacceptable",
                                        List.of(),
                                        Instant.now()));

        // No roleRegistry (null) — use the existing 4-arg constructor
        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(bus, alwaysRevise, null, new DefaultPlanner(), null);

        StubAgent agent =
                new StubAgent(
                        AGENT_ID, "scribe", msg -> Mono.just(Msg.of(MsgRole.ASSISTANT, "output")));
        Team team = new Team("t", List.of(agent), new NoopMessageBus());

        TeamResult result =
                coordinator
                        .execute(
                                new TeamExecutionRequest(
                                        "req-no-reg",
                                        "goal",
                                        Map.of(),
                                        config(RiskProfile.MEDIUM, 2)),
                                team)
                        .block(Duration.ofSeconds(10L));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.FAILED);
        assertThat(bus.teamEventTypes()).contains(TeamEventType.HANDOFF.name());
    }

    // ---------------------------------------------------------------
    // Test 5: LOW risk + no override → HANDOFF emitted then DEGRADED (not FAILED)
    // ---------------------------------------------------------------
    @Test
    void lowRiskEmitsHandoffThenDegradedResult() {
        RecordingEventBus bus = new RecordingEventBus();

        EvaluationStrategy alwaysRevise =
                ctx ->
                        Mono.just(
                                new EvaluationVerdict(
                                        VerdictOutcome.REVISE,
                                        0.1,
                                        "meh",
                                        List.of(),
                                        Instant.now()));

        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(
                        bus,
                        alwaysRevise,
                        null,
                        new DefaultPlanner(),
                        registryWithoutModelOverride());

        StubAgent agent =
                new StubAgent(
                        AGENT_ID, "scribe", msg -> Mono.just(Msg.of(MsgRole.ASSISTANT, "output")));
        Team team = new Team("t", List.of(agent), new NoopMessageBus());

        TeamResult result =
                coordinator
                        .execute(
                                new TeamExecutionRequest(
                                        "req-low-risk",
                                        "goal",
                                        Map.of(),
                                        config(RiskProfile.LOW, 2)),
                                team)
                        .block(Duration.ofSeconds(10L));

        assertThat(result).isNotNull();
        // LOW risk auto-passes with DEGRADED
        assertThat(result.status()).isEqualTo(TeamStatus.DEGRADED);
        assertThat(result.warnings()).isNotEmpty();

        // HANDOFF should still be emitted before the degraded auto-pass
        assertThat(bus.teamEventTypes()).contains(TeamEventType.HANDOFF.name());
    }

    // ---------------------------------------------------------------
    // Test 6: Existing feedback loop behavior preserved (rounds 1-3 without escalation)
    // ---------------------------------------------------------------
    @Test
    void existingFeedbackLoopBehaviorPreservedWhenStepPassesWithinBudget() {
        RecordingEventBus bus = new RecordingEventBus();
        AtomicInteger evalCalls = new AtomicInteger();

        EvaluationStrategy reviseThenPass =
                ctx ->
                        Mono.fromSupplier(
                                () -> {
                                    int n = evalCalls.incrementAndGet();
                                    if (n < 3) {
                                        return new EvaluationVerdict(
                                                VerdictOutcome.REVISE,
                                                0.3,
                                                "improve",
                                                List.of(),
                                                Instant.now());
                                    }
                                    return new EvaluationVerdict(
                                            VerdictOutcome.PASS,
                                            1.0,
                                            "good",
                                            List.of(),
                                            Instant.now());
                                });

        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(
                        bus,
                        reviseThenPass,
                        null,
                        new DefaultPlanner(),
                        registryWithModelOverride());

        StubAgent agent =
                new StubAgent(
                        AGENT_ID, "scribe", msg -> Mono.just(Msg.of(MsgRole.ASSISTANT, "output")));
        Team team = new Team("t", List.of(agent), new NoopMessageBus());

        TeamResult result =
                coordinator
                        .execute(
                                new TeamExecutionRequest(
                                        "req-normal",
                                        "goal",
                                        Map.of(),
                                        config(RiskProfile.MEDIUM, 3)),
                                team)
                        .block(Duration.ofSeconds(10L));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.COMPLETED);
        // Step passed within budget (attempt 3) - no escalation needed
        assertThat(result.stepOutcomes().get(0).attempts()).isEqualTo(3);
        assertThat(result.stepOutcomes().get(0).finalVerdict().outcome())
                .isEqualTo(VerdictOutcome.PASS);

        // No HANDOFF since it passed within budget
        assertThat(bus.teamEventTypes()).doesNotContain(TeamEventType.HANDOFF.name());
    }

    // ---------------------------------------------------------------
    // Test 7: HANDOFF event attributes correctness
    // ---------------------------------------------------------------
    @Test
    void handoffEventContainsAllRequiredAttributes() {
        RecordingEventBus bus = new RecordingEventBus();

        EvaluationStrategy alwaysRevise =
                ctx ->
                        Mono.just(
                                new EvaluationVerdict(
                                        VerdictOutcome.REVISE,
                                        0.15,
                                        "completely wrong approach",
                                        List.of("start over"),
                                        Instant.now()));

        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(
                        bus, alwaysRevise, null, new DefaultPlanner(), registryWithModelOverride());

        StubAgent agent =
                new StubAgent(
                        AGENT_ID, "scribe", msg -> Mono.just(Msg.of(MsgRole.ASSISTANT, "output")));
        Team team = new Team("t", List.of(agent), new NoopMessageBus());

        coordinator
                .execute(
                        new TeamExecutionRequest(
                                "req-attrs", "goal", Map.of(), config(RiskProfile.MEDIUM, 2)),
                        team)
                .block(Duration.ofSeconds(10L));

        // Find the HANDOFF event
        KairoEvent handoffEvent =
                bus.recordedTeamEvents().stream()
                        .filter(e -> TeamEventType.HANDOFF.name().equals(e.eventType()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("HANDOFF event not found"));

        TeamEvent teamEvent = (TeamEvent) handoffEvent.payload();
        Map<String, Object> attrs = teamEvent.attributes();

        // All required attributes must be present
        assertThat(attrs).containsKey("requiresHuman");
        assertThat(attrs).containsKey("feedback");
        assertThat(attrs).containsKey("stepId");
        assertThat(attrs).containsKey("roleId");
        assertThat(attrs).containsKey("attempts");

        // Values
        assertThat(attrs.get("requiresHuman")).isEqualTo(Boolean.TRUE);
        assertThat(attrs.get("roleId")).isEqualTo(ROLE_ID);
        assertThat(attrs.get("stepId")).asString().contains(ROLE_ID);
        // Senior model had one bonus attempt, so total = maxFeedbackRounds + 1
        assertThat((Integer) attrs.get("attempts")).isEqualTo(3);
        assertThat(attrs.get("feedback")).asString().isNotBlank();
    }
}
