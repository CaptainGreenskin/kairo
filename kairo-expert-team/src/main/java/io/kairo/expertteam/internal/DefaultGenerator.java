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
package io.kairo.expertteam.internal;

import io.kairo.api.agent.Agent;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.team.EvaluationVerdict;
import io.kairo.api.team.TeamStep;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Dispatches a single {@link TeamStep} to its role-bound {@link Agent} and returns the generated
 * artifact text.
 *
 * <p>The generator is stateless; the coordinator is responsible for tracking attempt counts and
 * prior verdicts. Revise loops are modelled by re-invoking {@link #generate} with an incremented
 * {@code attemptNumber} and the cumulative list of prior verdicts so the agent can react to
 * feedback.
 *
 * @since v0.10 (Experimental)
 */
public final class DefaultGenerator {

    /** Produce an artifact for the given step using the role-bound agent. */
    public Mono<String> generate(
            TeamStep step,
            Map<String, Agent> roleBindings,
            String goal,
            int attemptNumber,
            List<EvaluationVerdict> priorVerdicts) {
        Objects.requireNonNull(step, "step must not be null");
        Objects.requireNonNull(roleBindings, "roleBindings must not be null");
        Objects.requireNonNull(goal, "goal must not be null");
        Objects.requireNonNull(priorVerdicts, "priorVerdicts must not be null");

        Agent agent = roleBindings.get(step.assignedRole().roleId());
        if (agent == null) {
            return Mono.error(
                    new IllegalStateException(
                            "No agent bound to role '"
                                    + step.assignedRole().roleId()
                                    + "' for step '"
                                    + step.stepId()
                                    + "'"));
        }

        String prompt = buildPrompt(step, goal, attemptNumber, priorVerdicts);
        Msg input = Msg.of(MsgRole.USER, prompt);
        return agent.call(input).map(Msg::text);
    }

    private static String buildPrompt(
            TeamStep step, String goal, int attemptNumber, List<EvaluationVerdict> priorVerdicts) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Goal] ").append(goal).append('\n');
        sb.append("[Step ")
                .append(step.stepIndex() + 1)
                .append("] ")
                .append(step.description())
                .append('\n');
        sb.append("[Role] ")
                .append(step.assignedRole().roleName())
                .append("\n[Instructions]\n")
                .append(step.assignedRole().instructions())
                .append('\n');
        if (attemptNumber > 1 && !priorVerdicts.isEmpty()) {
            sb.append("[Revision attempt ").append(attemptNumber).append("]\n");
            EvaluationVerdict last = priorVerdicts.get(priorVerdicts.size() - 1);
            if (!last.feedback().isBlank()) {
                sb.append("Previous feedback: ").append(last.feedback()).append('\n');
            }
            if (!last.suggestions().isEmpty()) {
                sb.append("Suggestions:\n");
                for (String s : last.suggestions()) {
                    sb.append(" - ").append(s).append('\n');
                }
            }
        }
        return sb.toString();
    }
}
