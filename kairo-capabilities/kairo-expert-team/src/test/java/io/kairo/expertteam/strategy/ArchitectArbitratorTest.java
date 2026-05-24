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
package io.kairo.expertteam.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentState;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.team.EvaluationVerdict;
import io.kairo.api.team.EvaluationVerdict.VerdictOutcome;
import io.kairo.api.team.TeamResult.StepOutcome;
import io.kairo.expertteam.role.ExpertRoleRegistry;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Tests for {@link ArchitectArbitrator}.
 *
 * <p>Covers both trigger conditions (feedback exhaustion and score divergence), the threshold
 * check, and graceful fallback when no architect agent is available.
 */
final class ArchitectArbitratorTest {

    private static final String GOAL = "Implement user authentication module";

    private final ExpertRoleRegistry registry = new ExpertRoleRegistry();

    // ---------------------------------------------------------------------- shouldArbitrate

    @Test
    void shouldArbitrate_returnsTrueWhenScoreVarianceExceedsThreshold() {
        ArchitectArbitrator arbitrator = new ArchitectArbitrator(registry, null);

        List<ScoredArtifact> artifacts =
                List.of(
                        scoredArtifact("step-1", "expert:coder", "output1", 0.9),
                        scoredArtifact("step-2", "expert:researcher", "output2", 0.4));

        assertThat(arbitrator.shouldArbitrate(artifacts)).isTrue();
    }

    @Test
    void shouldArbitrate_returnsFalseWhenVarianceBelowThreshold() {
        ArchitectArbitrator arbitrator = new ArchitectArbitrator(registry, null);

        List<ScoredArtifact> artifacts =
                List.of(
                        scoredArtifact("step-1", "expert:coder", "output1", 0.7),
                        scoredArtifact("step-2", "expert:researcher", "output2", 0.5));

        assertThat(arbitrator.shouldArbitrate(artifacts)).isFalse();
    }

    @Test
    void shouldArbitrate_returnsFalseForSingleArtifact() {
        ArchitectArbitrator arbitrator = new ArchitectArbitrator(registry, null);

        List<ScoredArtifact> artifacts =
                List.of(scoredArtifact("step-1", "expert:coder", "output1", 0.9));

        assertThat(arbitrator.shouldArbitrate(artifacts)).isFalse();
    }

    // ---------------------------------------------------------------------- feedback exhaustion

    @Test
    void arbitrateFeedbackExhaustion_returnsAcceptWithCaveats() {
        Agent architectAgent =
                stubAgent(
                        msg ->
                                Mono.just(
                                        Msg.of(
                                                MsgRole.ASSISTANT,
                                                "DECISION: ACCEPT\nThe output meets minimum"
                                                        + " requirements despite reviewer concerns\nNo"
                                                        + " changes needed")));

        ArchitectArbitrator arbitrator = new ArchitectArbitrator(registry, architectAgent);

        StepOutcome coderOutput = stepOutcome("step-1", "generated code", VerdictOutcome.REVISE, 3);
        EvaluationVerdict verdict = reviseVerdict(0.3, "Code doesn't handle edge cases");

        StepVerifier.create(
                        arbitrator.arbitrateFeedbackExhaustion(
                                GOAL, coderOutput, verdict, "Code doesn't handle edge cases"))
                .assertNext(
                        result -> {
                            assertThat(result.decision())
                                    .isEqualTo(ArbitrationDecision.ACCEPT_WITH_CAVEATS);
                            assertThat(result.resolvedOutput()).isEqualTo("generated code");
                            assertThat(result.rationale())
                                    .contains("output meets minimum requirements");
                        })
                .verifyComplete();
    }

    @Test
    void arbitrateFeedbackExhaustion_returnsRevisedInstruction() {
        Agent architectAgent =
                stubAgent(
                        msg ->
                                Mono.just(
                                        Msg.of(
                                                MsgRole.ASSISTANT,
                                                "DECISION: REVISE\nThe coder should focus on null"
                                                        + " safety\nAdd null checks at method"
                                                        + " boundaries and use Optional for return"
                                                        + " types")));

        ArchitectArbitrator arbitrator = new ArchitectArbitrator(registry, architectAgent);

        StepOutcome coderOutput = stepOutcome("step-1", "generated code", VerdictOutcome.REVISE, 3);
        EvaluationVerdict verdict = reviseVerdict(0.3, "Missing null handling");

        StepVerifier.create(
                        arbitrator.arbitrateFeedbackExhaustion(
                                GOAL, coderOutput, verdict, "Missing null handling"))
                .assertNext(
                        result -> {
                            assertThat(result.decision())
                                    .isEqualTo(ArbitrationDecision.REVISED_INSTRUCTION);
                            assertThat(result.resolvedOutput()).contains("null checks");
                            assertThat(result.rationale()).contains("null safety");
                        })
                .verifyComplete();
    }

    // ---------------------------------------------------------------------- score divergence

    @Test
    void arbitrateScoreDivergence_returnsPickArtifact() {
        Agent architectAgent =
                stubAgent(
                        msg ->
                                Mono.just(
                                        Msg.of(
                                                MsgRole.ASSISTANT,
                                                "DECISION: PICK 1\nArtifact 1 provides a more"
                                                        + " complete solution with better error"
                                                        + " handling")));

        ArchitectArbitrator arbitrator = new ArchitectArbitrator(registry, architectAgent);

        List<ScoredArtifact> artifacts =
                List.of(
                        scoredArtifact("step-1", "expert:coder", "implementation A", 0.8),
                        scoredArtifact("step-2", "expert:researcher", "implementation B", 0.3));

        StepVerifier.create(arbitrator.arbitrateScoreDivergence(GOAL, artifacts))
                .assertNext(
                        result -> {
                            assertThat(result.decision())
                                    .isEqualTo(ArbitrationDecision.PICK_ARTIFACT);
                            assertThat(result.resolvedOutput()).isEqualTo("implementation A");
                            assertThat(result.rationale()).contains("complete solution");
                        })
                .verifyComplete();
    }

    @Test
    void arbitrateScoreDivergence_returnsMergedResolution() {
        Agent architectAgent =
                stubAgent(
                        msg ->
                                Mono.just(
                                        Msg.of(
                                                MsgRole.ASSISTANT,
                                                "DECISION: MERGE\nBoth approaches have merits;"
                                                        + " combining them\nMerged implementation"
                                                        + " combining A's error handling with B's"
                                                        + " clean API design")));

        ArchitectArbitrator arbitrator = new ArchitectArbitrator(registry, architectAgent);

        List<ScoredArtifact> artifacts =
                List.of(
                        scoredArtifact("step-1", "expert:coder", "implementation A", 0.7),
                        scoredArtifact("step-2", "expert:researcher", "implementation B", 0.6));

        StepVerifier.create(arbitrator.arbitrateScoreDivergence(GOAL, artifacts))
                .assertNext(
                        result -> {
                            assertThat(result.decision())
                                    .isEqualTo(ArbitrationDecision.MERGED_RESOLUTION);
                            assertThat(result.resolvedOutput()).contains("Merged implementation");
                            assertThat(result.rationale()).contains("Both approaches");
                        })
                .verifyComplete();
    }

    // ---------------------------------------------------------------------- null agent fallback

    @Test
    void nullArchitectAgent_feedbackExhaustion_returnsAcceptWithCaveats() {
        ArchitectArbitrator arbitrator = new ArchitectArbitrator(registry, null);

        StepOutcome coderOutput = stepOutcome("step-1", "generated code", VerdictOutcome.REVISE, 3);
        EvaluationVerdict verdict = reviseVerdict(0.3, "Some feedback");

        StepVerifier.create(
                        arbitrator.arbitrateFeedbackExhaustion(
                                GOAL, coderOutput, verdict, "Some feedback"))
                .assertNext(
                        result -> {
                            assertThat(result.decision())
                                    .isEqualTo(ArbitrationDecision.ACCEPT_WITH_CAVEATS);
                            assertThat(result.resolvedOutput()).isEqualTo("generated code");
                            assertThat(result.rationale()).contains("No architect agent available");
                        })
                .verifyComplete();
    }

    @Test
    void nullArchitectAgent_scoreDivergence_picksHighestScored() {
        ArchitectArbitrator arbitrator = new ArchitectArbitrator(registry, null);

        List<ScoredArtifact> artifacts =
                List.of(
                        scoredArtifact("step-1", "expert:coder", "low-score output", 0.3),
                        scoredArtifact("step-2", "expert:researcher", "high-score output", 0.9));

        StepVerifier.create(arbitrator.arbitrateScoreDivergence(GOAL, artifacts))
                .assertNext(
                        result -> {
                            assertThat(result.decision())
                                    .isEqualTo(ArbitrationDecision.PICK_ARTIFACT);
                            assertThat(result.resolvedOutput()).isEqualTo("high-score output");
                            assertThat(result.rationale()).contains("No architect agent available");
                        })
                .verifyComplete();
    }

    // ---------------------------------------------------------------------- helpers

    private static ScoredArtifact scoredArtifact(
            String stepId, String roleId, String output, double score) {
        EvaluationVerdict verdict =
                new EvaluationVerdict(
                        score >= 0.7 ? VerdictOutcome.PASS : VerdictOutcome.REVISE,
                        score,
                        "evaluation feedback",
                        List.of(),
                        Instant.now());
        return new ScoredArtifact(stepId, roleId, output, score, verdict);
    }

    private static StepOutcome stepOutcome(
            String stepId, String output, VerdictOutcome outcome, int attempts) {
        EvaluationVerdict verdict =
                new EvaluationVerdict(outcome, 0.3, "feedback", List.of(), Instant.now());
        return new StepOutcome(stepId, output, verdict, attempts);
    }

    private static EvaluationVerdict reviseVerdict(double score, String feedback) {
        return new EvaluationVerdict(
                VerdictOutcome.REVISE, score, feedback, List.of("fix this"), Instant.now());
    }

    private static Agent stubAgent(Function<Msg, Mono<Msg>> handler) {
        return new Agent() {
            @Override
            public Mono<Msg> call(Msg input) {
                return handler.apply(input);
            }

            @Override
            public String id() {
                return "architect-agent-001";
            }

            @Override
            public String name() {
                return "architect";
            }

            @Override
            public AgentState state() {
                return AgentState.IDLE;
            }

            @Override
            public void interrupt() {
                // no-op
            }
        };
    }
}
