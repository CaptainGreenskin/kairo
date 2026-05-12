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
package io.kairo.core.agent.continuation;

import io.kairo.api.message.Msg;
import reactor.core.publisher.Mono;

/**
 * Nudges the agent to continue when there are pending TODO items.
 *
 * <p>Detection sources (in priority order):
 *
 * <ol>
 *   <li>{@code extensionData["pendingTodoCount"]} — numeric count of pending items
 *   <li>{@code extensionData["hasIncompletePlan"]} — boolean flag for incomplete plans
 * </ol>
 *
 * <p>Signal-based: always nudges while pending TODOs are present. There is no per-session nudge
 * budget — long tasks must be allowed to complete. Runaway protection is handled by {@code
 * LoopDetector} (tool+args repetition), {@link CompositeContinuationStrategy}'s iteration budget
 * guard, and the model token budget.
 *
 * <p>Returns {@link ContinuationDecision.Pass} when no pending work is detected.
 *
 * @since 0.5.0
 */
public final class PendingTodoNudgeStrategy implements AgentContinuationStrategy {

    private static final String NUDGE_TEMPLATE =
            "There are still unfinished tasks. Continue executing the plan. "
                    + "Every response must call at least one tool until all work is completed. "
                    + "Do not respond with text only — call a tool now.";

    /** Creates the strategy. Signal-based — no parameters required. */
    public PendingTodoNudgeStrategy() {}

    @Override
    public Mono<ContinuationDecision> decide(ContinuationContext ctx) {
        // Check for pending todos via extensionData
        Object pendingCount = ctx.extensionData().get("pendingTodoCount");
        if (pendingCount instanceof Number n && n.intValue() > 0) {
            Msg synthetic =
                    Msg.nudge(
                            String.format(
                                    "There are %d unfinished TODO items. %s",
                                    n.intValue(), NUDGE_TEMPLATE),
                            name());
            return Mono.just(
                    new ContinuationDecision.Nudge(synthetic, "pending_todos=" + n.intValue()));
        }

        // Check if iteration is early relative to maxIterations (agent likely not done)
        // If we're less than 50% through max iterations and model stopped, likely premature
        if (ctx.iteration() < ctx.maxIterations() / 2 && ctx.hasIterationBudget()) {
            Object hasIncompletePlan = ctx.extensionData().get("hasIncompletePlan");
            if (Boolean.TRUE.equals(hasIncompletePlan)) {
                Msg synthetic = Msg.nudge(NUDGE_TEMPLATE, name());
                return Mono.just(
                        new ContinuationDecision.Nudge(synthetic, "incomplete_plan_detected"));
            }
        }

        return Mono.just(ContinuationDecision.Pass.INSTANCE);
    }

    @Override
    public String name() {
        return "PendingTodoNudge";
    }
}
