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
package io.kairo.api.team;

import io.kairo.api.Experimental;
import reactor.core.publisher.Mono;

/**
 * Evaluates a generated step artifact and decides whether it passes, needs revision, or escalates.
 *
 * <p>Evaluation is the single most common extension point for expert-team workflows — domain
 * rubrics, regulated-industry validators, and human-in-the-loop gates all plug in here. The SPI is
 * earned on day one by two reference implementations in {@code kairo-expert-team}:
 *
 * <ul>
 *   <li>{@code SimpleEvaluationStrategy} — deterministic rubric-driven evaluator (default).
 *   <li>{@code AgentEvaluationStrategy} — LLM-judge agent, opt-in via {@link
 *       TeamConfig#evaluatorPreference()} / {@link RiskProfile}.
 * </ul>
 *
 * <p>Crash semantics (ADR-015 §"Failure semantics"): an evaluator failure MUST NOT silently return
 * {@link EvaluationVerdict.VerdictOutcome#PASS}. The caller translates exceptions to {@link
 * EvaluationVerdict.VerdictOutcome#REVIEW_EXCEEDED} unless the team's {@link RiskProfile}
 * explicitly opted into {@link EvaluationVerdict.VerdictOutcome#AUTO_PASS_WITH_WARNING}.
 *
 * <p>Third-party implementors must pass the {@code EvaluationStrategyTCK} (ADR-016).
 *
 * @see TeamCoordinator
 * @see <a href="../../../../../../docs/adr/ADR-016-coordinator-spi.md">ADR-016</a>
 * @since v0.10 (Experimental)
 */
@Experimental("EvaluationStrategy SPI — contract may change in v0.11")
public interface EvaluationStrategy {

    /**
     * Evaluate the supplied context and emit a verdict.
     *
     * @param context the evaluation context; never {@code null}
     * @return a {@link Mono} emitting the verdict; never completes empty
     */
    Mono<EvaluationVerdict> evaluate(EvaluationContext context);
}
