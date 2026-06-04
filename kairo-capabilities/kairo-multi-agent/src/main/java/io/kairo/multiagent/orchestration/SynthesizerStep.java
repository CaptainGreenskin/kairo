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
package io.kairo.multiagent.orchestration;

import io.kairo.api.agent.Agent;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.team.TeamResult.StepOutcome;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Final synthesis step: integrates all expert outputs into a structured report.
 *
 * <p>Invoked after all DAG steps complete and evaluation settles. The synthesized output is
 * intended to populate {@link io.kairo.api.team.TeamResult#finalOutput()}.
 *
 * <p>This step is <b>not</b> responsible for memory extraction — that concern is handled separately
 * by the coordinator after synthesis completes.
 *
 * @since v0.10 (Experimental)
 */
public final class SynthesizerStep {

    private final Agent synthesisAgent;

    /**
     * Constructs a synthesizer step using the given agent for LLM invocation.
     *
     * @param synthesisAgent the agent to use for synthesis; must not be {@code null}
     */
    public SynthesizerStep(Agent synthesisAgent) {
        this.synthesisAgent =
                Objects.requireNonNull(synthesisAgent, "synthesisAgent must not be null");
    }

    /**
     * Synthesize all expert outputs into a final markdown report.
     *
     * @param goal the original user goal; must not be {@code null}
     * @param stepOutcomes all completed step outcomes with their artifacts; must not be {@code
     *     null}
     * @return a {@link Mono} emitting the structured markdown report
     */
    public Mono<String> synthesize(String goal, List<StepOutcome> stepOutcomes) {
        Objects.requireNonNull(goal, "goal must not be null");
        Objects.requireNonNull(stepOutcomes, "stepOutcomes must not be null");

        String prompt = buildSynthesisPrompt(goal, stepOutcomes);
        Msg input = Msg.of(MsgRole.USER, prompt);
        return synthesisAgent.call(input).map(Msg::text);
    }

    /**
     * Build the synthesis prompt from goal + outcomes.
     *
     * <p>Package-private for testing.
     */
    String buildSynthesisPrompt(String goal, List<StepOutcome> stepOutcomes) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a synthesis expert. Your job is to integrate all team outputs ")
                .append("into a coherent final report.\n\n");
        sb.append("## Original Goal\n").append(goal).append("\n\n");
        sb.append("## Expert Outputs\n\n");

        for (var outcome : stepOutcomes) {
            sb.append("### Step: ").append(outcome.stepId()).append('\n');
            sb.append("**Verdict**: ").append(outcome.finalVerdict().outcome()).append('\n');
            sb.append("**Score**: ").append(outcome.finalVerdict().score()).append('\n');
            sb.append("**Attempts**: ").append(outcome.attempts()).append('\n');
            sb.append("**Output**:\n").append(outcome.output()).append("\n\n");
        }

        sb.append("## Required Output Format\n");
        sb.append("Produce a markdown report with these sections:\n");
        sb.append("- ## Summary (1-3 sentences of what was accomplished)\n");
        sb.append("- ## Changes Made (per expert, what they produced)\n");
        sb.append("- ## Test Results (if any tests were written/run)\n");
        sb.append("- ## PR Description (ready to paste into a PR)\n");
        sb.append("- ## Remaining Risks (what might need follow-up)\n");

        return sb.toString();
    }
}
