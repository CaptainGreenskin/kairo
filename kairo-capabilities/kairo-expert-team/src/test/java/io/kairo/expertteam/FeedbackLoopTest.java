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
import io.kairo.api.team.MessageBus;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Tests verifying the observable feedback loop via MessageBus (Task #56 / D2).
 *
 * <p>Validates that:
 *
 * <ul>
 *   <li>REVISE verdicts trigger MessageBus.send() with feedback
 *   <li>Round counter increments correctly in events
 *   <li>maxRounds exhausted produces HANDOFF with proper attributes
 *   <li>Null MessageBus doesn't cause NPE, events still emitted
 *   <li>PASS verdict doesn't send feedback via MessageBus
 * </ul>
 */
final class FeedbackLoopTest {

    // ── Test 1: REVISE verdict → MessageBus.send() called with feedback ──

    @Test
    void reviseVerdictSendsFeedbackViaMessageBus() {
        RecordingEventBus eventBus = new RecordingEventBus();
        RecordingMessageBus messageBus = new RecordingMessageBus();
        AtomicInteger calls = new AtomicInteger();

        EvaluationStrategy reviseThenPass =
                ctx ->
                        Mono.fromSupplier(
                                () -> {
                                    int n = calls.incrementAndGet();
                                    if (n == 1) {
                                        return new EvaluationVerdict(
                                                VerdictOutcome.REVISE,
                                                0.3,
                                                "Please add error handling",
                                                List.of("wrap in try-catch"),
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
                        eventBus, reviseThenPass, null, new DefaultPlanner(), null, messageBus);

        StubAgent agent =
                new StubAgent(
                        "scribe", "scribe", msg -> Mono.just(Msg.of(MsgRole.ASSISTANT, "draft")));
        Team team = new Team("t", List.of(agent), new NoopMessageBus());

        TeamResult result =
                coordinator
                        .execute(buildRequest("req-fb1", 3), team)
                        .block(Duration.ofSeconds(10L));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.COMPLETED);

        // MessageBus should have received exactly one feedback message (the REVISE round)
        assertThat(messageBus.sentMessages()).hasSize(1);
        RecordingMessageBus.SentMessage sent = messageBus.sentMessages().get(0);
        assertThat(sent.fromAgentId()).isEqualTo("evaluator");
        assertThat(sent.toAgentId()).isEqualTo("scribe");
        assertThat(sent.message().text()).contains("REVISE round 1/3");
        assertThat(sent.message().text()).contains("Please add error handling");
        assertThat(sent.message().metadata()).containsEntry("feedbackType", "evaluation_revise");
        assertThat(sent.message().metadata()).containsEntry("round", 1);
        assertThat(sent.message().metadata()).containsEntry("maxRounds", 3);
    }

    // ── Test 2: Multiple rounds: round counter increments correctly in events ──

    @Test
    void multipleRoundsIncrementRoundCounterInEvents() {
        RecordingEventBus eventBus = new RecordingEventBus();
        RecordingMessageBus messageBus = new RecordingMessageBus();
        AtomicInteger calls = new AtomicInteger();

        EvaluationStrategy reviseRevisePass =
                ctx ->
                        Mono.fromSupplier(
                                () -> {
                                    int n = calls.incrementAndGet();
                                    if (n < 3) {
                                        return new EvaluationVerdict(
                                                VerdictOutcome.REVISE,
                                                0.2 + (n * 0.1),
                                                "feedback round " + n,
                                                List.of("suggestion-" + n),
                                                Instant.now());
                                    }
                                    return new EvaluationVerdict(
                                            VerdictOutcome.PASS,
                                            1.0,
                                            "looks great",
                                            List.of(),
                                            Instant.now());
                                });

        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(
                        eventBus, reviseRevisePass, null, new DefaultPlanner(), null, messageBus);

        StubAgent agent =
                new StubAgent(
                        "scribe", "scribe", msg -> Mono.just(Msg.of(MsgRole.ASSISTANT, "draft")));
        Team team = new Team("t", List.of(agent), new NoopMessageBus());

        TeamResult result =
                coordinator
                        .execute(buildRequest("req-rounds", 5), team)
                        .block(Duration.ofSeconds(10L));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.COMPLETED);

        // Verify MessageBus got 2 feedback messages (round 1 and round 2)
        assertThat(messageBus.sentMessages()).hasSize(2);
        assertThat(messageBus.sentMessages().get(0).message().metadata()).containsEntry("round", 1);
        assertThat(messageBus.sentMessages().get(1).message().metadata()).containsEntry("round", 2);

        // Verify EVALUATION_RESULT events have correct round attributes
        List<KairoEvent> evalResults =
                eventBus.recordedTeamEvents().stream()
                        .filter(e -> TeamEventType.EVALUATION_RESULT.name().equals(e.eventType()))
                        .toList();

        assertThat(evalResults).hasSize(3); // round 1, 2, 3
        // Check the payload attributes for round numbers
        for (int i = 0; i < evalResults.size(); i++) {
            KairoEvent event = evalResults.get(i);
            io.kairo.api.team.TeamEvent teamEvent = (io.kairo.api.team.TeamEvent) event.payload();
            assertThat(teamEvent.attributes()).containsEntry("round", i + 1);
            assertThat(teamEvent.attributes()).containsEntry("maxRounds", 5);
        }
    }

    // ── Test 3: maxRounds exhausted → HANDOFF event with proper attributes ──

    @Test
    void maxRoundsExhaustedEmitsHandoffWithFeedbackLoopExhaustedReason() {
        RecordingEventBus eventBus = new RecordingEventBus();
        RecordingMessageBus messageBus = new RecordingMessageBus();

        EvaluationStrategy alwaysRevise =
                ctx ->
                        Mono.just(
                                new EvaluationVerdict(
                                        VerdictOutcome.REVISE,
                                        0.1,
                                        "still not good enough",
                                        List.of("try again"),
                                        Instant.now()));

        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(
                        eventBus, alwaysRevise, null, new DefaultPlanner(), null, messageBus);

        StubAgent agent =
                new StubAgent(
                        "scribe", "scribe", msg -> Mono.just(Msg.of(MsgRole.ASSISTANT, "draft")));
        Team team = new Team("t", List.of(agent), new NoopMessageBus());

        TeamResult result =
                coordinator
                        .execute(buildRequest("req-exhausted", 2, RiskProfile.MEDIUM), team)
                        .block(Duration.ofSeconds(10L));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.FAILED);

        // Verify HANDOFF event was emitted with correct attributes
        List<KairoEvent> handoffEvents =
                eventBus.recordedTeamEvents().stream()
                        .filter(e -> TeamEventType.HANDOFF.name().equals(e.eventType()))
                        .toList();
        assertThat(handoffEvents).hasSize(1);

        io.kairo.api.team.TeamEvent handoff =
                (io.kairo.api.team.TeamEvent) handoffEvents.get(0).payload();
        assertThat(handoff.attributes()).containsEntry("reason", "feedback_loop_exhausted");
        assertThat(handoff.attributes()).containsKey("lastFeedback");
        assertThat((String) handoff.attributes().get("lastFeedback")).isNotBlank();

        // MessageBus should have received feedback for each REVISE round
        assertThat(messageBus.sentMessages()).hasSize(2); // maxRounds = 2
    }

    // ── Test 4: Null MessageBus → no send(), no NPE, events still emitted ──

    @Test
    void nullMessageBusDoesNotCauseNpeAndEventsStillEmitted() {
        RecordingEventBus eventBus = new RecordingEventBus();
        AtomicInteger calls = new AtomicInteger();

        EvaluationStrategy reviseThenPass =
                ctx ->
                        Mono.fromSupplier(
                                () -> {
                                    int n = calls.incrementAndGet();
                                    if (n == 1) {
                                        return new EvaluationVerdict(
                                                VerdictOutcome.REVISE,
                                                0.3,
                                                "needs work",
                                                List.of("fix it"),
                                                Instant.now());
                                    }
                                    return new EvaluationVerdict(
                                            VerdictOutcome.PASS,
                                            1.0,
                                            "done",
                                            List.of(),
                                            Instant.now());
                                });

        // messageBus = null
        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(
                        eventBus, reviseThenPass, null, new DefaultPlanner(), null, null);

        StubAgent agent =
                new StubAgent(
                        "scribe", "scribe", msg -> Mono.just(Msg.of(MsgRole.ASSISTANT, "output")));
        Team team = new Team("t", List.of(agent), new NoopMessageBus());

        TeamResult result =
                coordinator
                        .execute(buildRequest("req-null-bus", 3), team)
                        .block(Duration.ofSeconds(10L));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.COMPLETED);

        // Events should still have been emitted with feedback attributes
        List<KairoEvent> evalResults =
                eventBus.recordedTeamEvents().stream()
                        .filter(e -> TeamEventType.EVALUATION_RESULT.name().equals(e.eventType()))
                        .toList();
        assertThat(evalResults).isNotEmpty();

        io.kairo.api.team.TeamEvent firstEval =
                (io.kairo.api.team.TeamEvent) evalResults.get(0).payload();
        assertThat(firstEval.attributes()).containsEntry("round", 1);
        assertThat(firstEval.attributes()).containsEntry("maxRounds", 3);
        assertThat(firstEval.attributes()).containsEntry("feedback", "needs work");
    }

    // ── Test 5: PASS verdict → no feedback message sent ──

    @Test
    void passVerdictDoesNotSendFeedbackViaMessageBus() {
        RecordingEventBus eventBus = new RecordingEventBus();
        RecordingMessageBus messageBus = new RecordingMessageBus();

        EvaluationStrategy alwaysPass =
                ctx ->
                        Mono.just(
                                new EvaluationVerdict(
                                        VerdictOutcome.PASS,
                                        1.0,
                                        "perfect",
                                        List.of(),
                                        Instant.now()));

        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(
                        eventBus, alwaysPass, null, new DefaultPlanner(), null, messageBus);

        StubAgent agent =
                new StubAgent(
                        "scribe", "scribe", msg -> Mono.just(Msg.of(MsgRole.ASSISTANT, "output")));
        Team team = new Team("t", List.of(agent), new NoopMessageBus());

        TeamResult result =
                coordinator
                        .execute(buildRequest("req-pass", 3), team)
                        .block(Duration.ofSeconds(10L));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.COMPLETED);

        // No feedback messages should have been sent
        assertThat(messageBus.sentMessages()).isEmpty();
    }

    // ── Helpers ──

    private static TeamExecutionRequest buildRequest(String requestId, int maxFeedbackRounds) {
        return buildRequest(requestId, maxFeedbackRounds, RiskProfile.MEDIUM);
    }

    private static TeamExecutionRequest buildRequest(
            String requestId, int maxFeedbackRounds, RiskProfile risk) {
        return new TeamExecutionRequest(
                requestId,
                "implement feature",
                Map.of(),
                new TeamConfig(
                        risk,
                        maxFeedbackRounds,
                        Duration.ofSeconds(5L),
                        EvaluatorPreference.SIMPLE,
                        PlannerFailureMode.FAIL_FAST,
                        TeamResourceConstraint.unbounded()));
    }

    /** In-memory recording MessageBus for test assertions. */
    private static final class RecordingMessageBus implements MessageBus {

        private final List<SentMessage> sent = Collections.synchronizedList(new ArrayList<>());

        @Override
        public Mono<Void> send(String fromAgentId, String toAgentId, Msg message) {
            sent.add(new SentMessage(fromAgentId, toAgentId, message));
            return Mono.empty();
        }

        @Override
        public Flux<Msg> receive(String agentId) {
            return Flux.empty();
        }

        @Override
        public Mono<Void> broadcast(String fromAgentId, Msg message) {
            return Mono.empty();
        }

        List<SentMessage> sentMessages() {
            return List.copyOf(sent);
        }

        record SentMessage(String fromAgentId, String toAgentId, Msg message) {}
    }
}
