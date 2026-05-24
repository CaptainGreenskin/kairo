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
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Deterministic rubric-driven evaluator (ADR-016 reference impl #1).
 *
 * <p>The default rubric treats any non-blank artifact as {@link VerdictOutcome#PASS} (score 1.0)
 * and any blank artifact as {@link VerdictOutcome#REVISE} (score 0.0). Adopters override the rubric
 * by supplying a {@link RubricFunction} — a pure function from {@link EvaluationContext} to {@link
 * EvaluationVerdict} — to layer in domain-specific checks without re-implementing the whole
 * strategy.
 *
 * <p>Per ADR-015 §"Failure semantics", a rubric that throws is never silently mapped to {@link
 * VerdictOutcome#PASS}: the strategy returns a {@link VerdictOutcome#REVIEW_EXCEEDED} verdict so
 * the coordinator can honour the team's risk profile.
 *
 * @since v0.10 (Experimental)
 */
public final class SimpleEvaluationStrategy implements EvaluationStrategy {

    /** Functional hook for plugging in a domain-specific rubric. */
    @FunctionalInterface
    public interface RubricFunction extends Function<EvaluationContext, EvaluationVerdict> {}

    private static final Logger log = LoggerFactory.getLogger(SimpleEvaluationStrategy.class);

    private final RubricFunction rubric;

    /** Strategy with the built-in non-blank rubric. */
    public SimpleEvaluationStrategy() {
        this(SimpleEvaluationStrategy::defaultRubric);
    }

    /** Strategy with a caller-provided rubric. */
    public SimpleEvaluationStrategy(RubricFunction rubric) {
        this.rubric = Objects.requireNonNull(rubric, "rubric must not be null");
    }

    @Override
    public Mono<EvaluationVerdict> evaluate(EvaluationContext context) {
        Objects.requireNonNull(context, "context must not be null");
        return Mono.fromCallable(
                        () -> {
                            EvaluationVerdict verdict = rubric.apply(context);
                            if (verdict == null) {
                                throw new IllegalStateException(
                                        "Rubric returned null verdict (forbidden by contract)");
                            }
                            return verdict;
                        })
                .onErrorResume(
                        ex -> {
                            log.warn(
                                    "SimpleEvaluationStrategy rubric crashed — mapping to"
                                            + " REVIEW_EXCEEDED per ADR-015: {}",
                                    ex.toString());
                            return Mono.just(crashVerdict(ex));
                        });
    }

    /** Default rubric: non-blank artifact -> PASS (1.0); blank artifact -> REVISE (0.0). */
    public static EvaluationVerdict defaultRubric(EvaluationContext ctx) {
        String artifact = ctx.artifact();
        if (artifact.isBlank()) {
            return new EvaluationVerdict(
                    VerdictOutcome.REVISE,
                    0.0,
                    "Artifact is empty; please produce a non-blank response"
                            + " (attempt "
                            + ctx.attemptNumber()
                            + ")",
                    List.of("Return a non-empty artifact that addresses the step description"),
                    Instant.now());
        }
        return new EvaluationVerdict(
                VerdictOutcome.PASS,
                1.0,
                "Artifact is non-empty (attempt " + ctx.attemptNumber() + ")",
                List.of(),
                Instant.now());
    }

    private static EvaluationVerdict crashVerdict(Throwable ex) {
        return new EvaluationVerdict(
                VerdictOutcome.REVIEW_EXCEEDED,
                0.0,
                "Evaluator crashed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                List.of(),
                Instant.now());
    }
}
