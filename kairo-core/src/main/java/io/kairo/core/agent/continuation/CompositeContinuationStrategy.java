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

import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Composes multiple strategies with short-circuit precedence.
 *
 * <p>Evaluates strategies in order; the first non-{@link ContinuationDecision.Pass} decision wins.
 * If all strategies return Pass, terminates with reason {@code "all_strategies_passed"}.
 *
 * <p>Additionally enforces global guards before delegating to child strategies:
 *
 * <ul>
 *   <li>Plan mode → immediate {@link ContinuationDecision.Terminate}
 *   <li>Iteration budget exhausted → immediate {@link ContinuationDecision.Terminate}
 * </ul>
 *
 * <p>Recommended default ordering:
 *
 * <ol>
 *   <li>{@link FinishReasonRecoveryStrategy} — highest priority (model was cut off)
 *   <li>{@link PendingTodoNudgeStrategy} — work remains
 *   <li>{@link RecentToolActivityStrategy} — model was actively working
 * </ol>
 *
 * @since 0.5.0
 */
public final class CompositeContinuationStrategy implements AgentContinuationStrategy {

    private final List<AgentContinuationStrategy> strategies;

    /**
     * Creates a composite with the given strategy list.
     *
     * @param strategies ordered list of strategies to evaluate
     */
    public CompositeContinuationStrategy(List<AgentContinuationStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    /**
     * Creates a composite with the recommended default strategy ordering.
     *
     * @return a pre-configured composite strategy
     */
    public static CompositeContinuationStrategy withDefaults() {
        return new CompositeContinuationStrategy(
                List.of(
                        new FinishReasonRecoveryStrategy(3),
                        new PendingTodoNudgeStrategy(),
                        new RecentToolActivityStrategy(3)));
    }

    @Override
    public Mono<ContinuationDecision> decide(ContinuationContext ctx) {
        // Plan mode forces termination regardless of strategies
        if (ctx.isPlanMode()) {
            return Mono.just(new ContinuationDecision.Terminate("plan_mode"));
        }

        // No iteration budget → terminate
        if (!ctx.hasIterationBudget()) {
            return Mono.just(new ContinuationDecision.Terminate("iteration_budget_exhausted"));
        }

        return Flux.fromIterable(strategies)
                .concatMap(s -> s.decide(ctx).defaultIfEmpty(ContinuationDecision.Pass.INSTANCE))
                .filter(d -> !(d instanceof ContinuationDecision.Pass))
                .next()
                .defaultIfEmpty(new ContinuationDecision.Terminate("all_strategies_passed"));
    }

    @Override
    public String name() {
        return "Composite["
                + strategies.stream()
                        .map(AgentContinuationStrategy::name)
                        .reduce((a, b) -> a + "," + b)
                        .orElse("")
                + "]";
    }
}
