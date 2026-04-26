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

import io.kairo.api.team.EvaluationContext;
import io.kairo.api.team.EvaluationStrategy;
import io.kairo.api.team.EvaluationVerdict;
import io.kairo.api.team.EvaluationVerdict.VerdictOutcome;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * LLM-judge evaluator (ADR-016 reference impl #2).
 *
 * <p>The strategy is opt-in: callers select it via {@link
 * io.kairo.api.team.EvaluatorPreference#AGENT} or via the coordinator's evaluator-selection logic
 * driven by {@link io.kairo.api.team.RiskProfile}. To keep the strategy unit-testable without
 * spinning up a real LLM, the actual agent invocation is injected through an {@link AgentInvoker}
 * functional interface: production deployments wire a {@code kairo.api.agent.Agent}-backed invoker,
 * while tests wire deterministic stubs.
 *
 * <p>Crash semantics follow ADR-015 §"Failure semantics": exceptions thrown by the judge surface as
 * a {@link VerdictOutcome#REVIEW_EXCEEDED} verdict — never a silent {@link VerdictOutcome#PASS}.
 *
 * @since v0.10 (Experimental)
 */
public final class AgentEvaluationStrategy implements EvaluationStrategy {

    /** Abstracts the LLM-judge call so the strategy stays deterministic in tests. */
    @FunctionalInterface
    public interface AgentInvoker {
        Mono<EvaluationVerdict> invoke(EvaluationContext context);
    }

    private static final Logger log = LoggerFactory.getLogger(AgentEvaluationStrategy.class);

    private final AgentInvoker invoker;

    public AgentEvaluationStrategy(AgentInvoker invoker) {
        this.invoker = Objects.requireNonNull(invoker, "invoker must not be null");
    }

    @Override
    public Mono<EvaluationVerdict> evaluate(EvaluationContext context) {
        Objects.requireNonNull(context, "context must not be null");
        return Mono.defer(() -> invoker.invoke(context))
                .map(
                        verdict -> {
                            if (verdict == null) {
                                throw new IllegalStateException(
                                        "AgentInvoker returned null verdict (forbidden by"
                                                + " contract)");
                            }
                            return verdict;
                        })
                .onErrorResume(
                        ex -> {
                            log.warn(
                                    "AgentEvaluationStrategy judge crashed — mapping to"
                                            + " REVIEW_EXCEEDED per ADR-015: {}",
                                    ex.toString());
                            return Mono.just(crashVerdict(ex));
                        });
    }

    private static EvaluationVerdict crashVerdict(Throwable ex) {
        return new EvaluationVerdict(
                VerdictOutcome.REVIEW_EXCEEDED,
                0.0,
                "Judge agent crashed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                List.of(),
                Instant.now());
    }
}
