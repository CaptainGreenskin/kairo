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
import io.kairo.api.team.EvaluatorPreference;
import io.kairo.api.team.PlannerFailureMode;
import io.kairo.api.team.RiskProfile;
import io.kairo.api.team.Team;
import io.kairo.api.team.TeamConfig;
import io.kairo.api.team.TeamEvent;
import io.kairo.api.team.TeamEventType;
import io.kairo.api.team.TeamExecutionRequest;
import io.kairo.api.team.TeamResourceConstraint;
import io.kairo.api.team.TeamResult;
import io.kairo.api.team.TeamStatus;
import io.kairo.expertteam.internal.DefaultPlanner;
import io.kairo.expertteam.strategy.SynthesizerStep;
import io.kairo.expertteam.tck.NoopMessageBus;
import io.kairo.expertteam.tck.RecordingEventBus;
import io.kairo.expertteam.tck.StubAgent;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests that STEP_ARTIFACT_CHUNK is emitted, that all events carry a monotonically increasing seq,
 * and that SynthesizerStep is wired correctly.
 *
 * <p>Note: STEP_THINKING and STEP_TOOL_CALL are no longer synthesized by the coordinator — real
 * per-tool STEP_TOOL_CALL events now stream from the worker agent's ToolPhase via {@code
 * ToolCallSink}, which a {@link StubAgent} (a fixed-response stub, not a real ReAct agent) does not
 * exercise. Those are covered by the worker/tool path and end-to-end tests.
 */
final class ExpertTeamCoordinatorStreamingEventsTest {

    private static final TeamConfig DEFAULT_CONFIG =
            new TeamConfig(
                    RiskProfile.MEDIUM,
                    3,
                    Duration.ofSeconds(5L),
                    EvaluatorPreference.SIMPLE,
                    PlannerFailureMode.FAIL_FAST,
                    TeamResourceConstraint.unbounded());

    @Test
    void stepArtifactChunkEventIsEmitted() {
        RecordingEventBus bus = new RecordingEventBus();
        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(
                        bus, new SimpleEvaluationStrategy(), null, new DefaultPlanner());

        StubAgent agent = StubAgent.fixed("scribe", "artifact-content");
        Team team = new Team("t", List.of(agent), new NoopMessageBus());

        coordinator
                .execute(
                        new TeamExecutionRequest(
                                "req-3", "produce artifact", Map.of(), DEFAULT_CONFIG),
                        team)
                .block(Duration.ofSeconds(10L));

        List<String> types = bus.teamEventTypes();
        assertThat(types).contains(TeamEventType.STEP_ARTIFACT_CHUNK.name());

        // Verify the chunk contains the artifact text
        List<KairoEvent> teamEvents = bus.recordedTeamEvents();
        KairoEvent artifactEvent =
                teamEvents.stream()
                        .filter(e -> TeamEventType.STEP_ARTIFACT_CHUNK.name().equals(e.eventType()))
                        .findFirst()
                        .orElseThrow();
        TeamEvent te = (TeamEvent) artifactEvent.payload();
        assertThat(te.attributes().get("chunk")).isEqualTo("artifact-content");
    }

    @Test
    void allEventsContainMonotonicallyIncreasingSeq() {
        RecordingEventBus bus = new RecordingEventBus();
        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(
                        bus, new SimpleEvaluationStrategy(), null, new DefaultPlanner());

        StubAgent agent = StubAgent.fixed("scribe", "draft");
        Team team = new Team("t", List.of(agent), new NoopMessageBus());

        coordinator
                .execute(
                        new TeamExecutionRequest("req-4", "do stuff", Map.of(), DEFAULT_CONFIG),
                        team)
                .block(Duration.ofSeconds(10L));

        List<KairoEvent> teamEvents = bus.recordedTeamEvents();
        assertThat(teamEvents).hasSizeGreaterThan(2);

        long previousSeq = 0;
        for (KairoEvent event : teamEvents) {
            TeamEvent te = (TeamEvent) event.payload();
            assertThat(te.attributes()).containsKey("seq");
            long seq = ((Number) te.attributes().get("seq")).longValue();
            assertThat(seq).isGreaterThan(previousSeq);
            previousSeq = seq;
        }
    }

    @Test
    void synthesizerIsCalledWhenPresent() {
        RecordingEventBus bus = new RecordingEventBus();
        StubAgent synthesisAgent =
                StubAgent.fixed("synthesizer", "## Synthesized Report\nAll done.");
        SynthesizerStep synthesizer = new SynthesizerStep(synthesisAgent);

        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(
                        bus,
                        new SimpleEvaluationStrategy(),
                        null,
                        new DefaultPlanner(),
                        null,
                        null,
                        null,
                        synthesizer);

        StubAgent agent = StubAgent.fixed("scribe", "step-output");
        Team team = new Team("t", List.of(agent), new NoopMessageBus());

        TeamResult result =
                coordinator
                        .execute(
                                new TeamExecutionRequest(
                                        "req-5", "synthesize", Map.of(), DEFAULT_CONFIG),
                                team)
                        .block(Duration.ofSeconds(10L));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.COMPLETED);
        assertThat(result.finalOutput()).isPresent();
        assertThat(result.finalOutput().get()).isEqualTo("## Synthesized Report\nAll done.");
        // Verify the synthesis agent was actually called
        assertThat(synthesisAgent.invocationCount()).isEqualTo(1);
    }

    @Test
    void synthesizerNullFallsBackToJoinedOutput() {
        RecordingEventBus bus = new RecordingEventBus();

        // No synthesizer (null)
        ExpertTeamCoordinator coordinator =
                new ExpertTeamCoordinator(
                        bus, new SimpleEvaluationStrategy(), null, new DefaultPlanner());

        StubAgent agent = StubAgent.fixed("scribe", "joined-output");
        Team team = new Team("t", List.of(agent), new NoopMessageBus());

        TeamResult result =
                coordinator
                        .execute(
                                new TeamExecutionRequest(
                                        "req-6", "plain join", Map.of(), DEFAULT_CONFIG),
                                team)
                        .block(Duration.ofSeconds(10L));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TeamStatus.COMPLETED);
        assertThat(result.finalOutput()).isPresent();
        // Default assembly: [stepId]\noutput
        assertThat(result.finalOutput().get()).contains("joined-output");
    }
}
