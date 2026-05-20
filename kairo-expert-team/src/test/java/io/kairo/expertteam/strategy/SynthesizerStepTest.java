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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.kairo.api.agent.Agent;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.team.EvaluationVerdict;
import io.kairo.api.team.EvaluationVerdict.VerdictOutcome;
import io.kairo.api.team.TeamResult.StepOutcome;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SynthesizerStepTest {

    private Agent mockAgent;
    private SynthesizerStep synthesizer;

    @BeforeEach
    void setUp() {
        mockAgent = mock(Agent.class);
        synthesizer = new SynthesizerStep(mockAgent);
    }

    private static StepOutcome outcome(String stepId, String output, VerdictOutcome verdict) {
        return new StepOutcome(
                stepId,
                output,
                new EvaluationVerdict(verdict, 0.9, "good", List.of(), Instant.now()),
                1);
    }

    @Test
    void synthesizeReturnsAgentResponseText() {
        when(mockAgent.call(any()))
                .thenReturn(Mono.just(Msg.of(MsgRole.ASSISTANT, "## Summary\nDone.")));

        List<StepOutcome> outcomes =
                List.of(outcome("step-1", "Wrote the service layer", VerdictOutcome.PASS));

        StepVerifier.create(synthesizer.synthesize("Build REST API", outcomes))
                .expectNext("## Summary\nDone.")
                .verifyComplete();
    }

    @Test
    void synthesizeCallsAgentWithUserMessage() {
        when(mockAgent.call(any())).thenReturn(Mono.just(Msg.of(MsgRole.ASSISTANT, "report")));

        synthesizer.synthesize("goal", List.of(outcome("s1", "out", VerdictOutcome.PASS))).block();

        verify(mockAgent).call(any(Msg.class));
    }

    @Test
    void synthesizePropagatesAgentError() {
        when(mockAgent.call(any())).thenReturn(Mono.error(new RuntimeException("model crashed")));

        StepVerifier.create(
                        synthesizer.synthesize(
                                "goal", List.of(outcome("s1", "out", VerdictOutcome.PASS))))
                .expectErrorMessage("model crashed")
                .verify();
    }

    @Test
    void constructorRejectsNullAgent() {
        assertThrows(NullPointerException.class, () -> new SynthesizerStep(null));
    }

    @Test
    void synthesizeRejectsNullGoal() {
        assertThrows(NullPointerException.class, () -> synthesizer.synthesize(null, List.of()));
    }

    @Test
    void synthesizeRejectsNullStepOutcomes() {
        assertThrows(NullPointerException.class, () -> synthesizer.synthesize("goal", null));
    }

    @Test
    void buildPromptContainsGoal() {
        String prompt =
                synthesizer.buildSynthesisPrompt(
                        "Implement login flow",
                        List.of(outcome("s1", "auth code", VerdictOutcome.PASS)));

        assertThat(prompt).contains("## Original Goal");
        assertThat(prompt).contains("Implement login flow");
    }

    @Test
    void buildPromptContainsAllStepOutcomes() {
        List<StepOutcome> outcomes =
                List.of(
                        outcome("step-arch", "Architecture design", VerdictOutcome.PASS),
                        outcome("step-code", "Implementation code", VerdictOutcome.PASS),
                        outcome("step-test", "Test suite", VerdictOutcome.AUTO_PASS_WITH_WARNING));

        String prompt = synthesizer.buildSynthesisPrompt("Build microservice", outcomes);

        assertThat(prompt).contains("### Step: step-arch");
        assertThat(prompt).contains("### Step: step-code");
        assertThat(prompt).contains("### Step: step-test");
        assertThat(prompt).contains("Architecture design");
        assertThat(prompt).contains("Implementation code");
        assertThat(prompt).contains("Test suite");
    }

    @Test
    void buildPromptContainsRequiredOutputSections() {
        String prompt =
                synthesizer.buildSynthesisPrompt(
                        "goal", List.of(outcome("s1", "out", VerdictOutcome.PASS)));

        assertThat(prompt).contains("## Summary");
        assertThat(prompt).contains("## Changes Made");
        assertThat(prompt).contains("## Test Results");
        assertThat(prompt).contains("## PR Description");
        assertThat(prompt).contains("## Remaining Risks");
    }

    @Test
    void buildPromptContainsVerdictAndScore() {
        EvaluationVerdict verdict =
                new EvaluationVerdict(
                        VerdictOutcome.PASS, 0.85, "well done", List.of(), Instant.now());
        StepOutcome outcome = new StepOutcome("step-review", "reviewed code", verdict, 2);

        String prompt = synthesizer.buildSynthesisPrompt("review", List.of(outcome));

        assertThat(prompt).contains("**Verdict**: PASS");
        assertThat(prompt).contains("**Score**: 0.85");
        assertThat(prompt).contains("**Attempts**: 2");
    }

    @Test
    void buildPromptWithEmptyStepOutcomes() {
        String prompt = synthesizer.buildSynthesisPrompt("empty goal", List.of());

        assertThat(prompt).contains("## Original Goal");
        assertThat(prompt).contains("empty goal");
        assertThat(prompt).contains("## Expert Outputs");
        assertThat(prompt).contains("## Required Output Format");
        // No step sections
        assertThat(prompt).doesNotContain("### Step:");
    }

    @Test
    void synthesizeWithEmptyOutcomesStillCallsAgent() {
        when(mockAgent.call(any()))
                .thenReturn(Mono.just(Msg.of(MsgRole.ASSISTANT, "nothing to report")));

        StepVerifier.create(synthesizer.synthesize("goal", List.of()))
                .expectNext("nothing to report")
                .verifyComplete();
    }

    @Test
    void buildPromptIncludesSynthesisExpertPreamble() {
        String prompt =
                synthesizer.buildSynthesisPrompt(
                        "goal", List.of(outcome("s1", "out", VerdictOutcome.PASS)));

        assertThat(prompt).startsWith("You are a synthesis expert.");
    }
}
