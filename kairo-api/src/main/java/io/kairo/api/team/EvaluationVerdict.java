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
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Outcome of running an {@link EvaluationStrategy} against a generated step artifact.
 *
 * <p>A verdict is the single atomic decision the coordinator acts on: {@link VerdictOutcome#PASS}
 * advances the state machine, {@link VerdictOutcome#REVISE} sends the step back to generation,
 * {@link VerdictOutcome#REVIEW_EXCEEDED} terminates the loop, and {@link
 * VerdictOutcome#AUTO_PASS_WITH_WARNING} is the opt-in LOW-risk bypass.
 *
 * @param outcome the verdict decision; non-null
 * @param score numeric score in {@code [0.0, 1.0]}; values outside the range are rejected
 * @param feedback human-readable feedback (empty string if none); non-null
 * @param suggestions actionable suggestions for the next revise attempt; defensively copied, never
 *     {@code null}
 * @param evaluatedAt when the verdict was produced; non-null
 * @since v0.10 (Experimental)
 */
@Experimental("Team evaluation verdict; introduced in v0.10, targeting stabilization in v1.1")
public record EvaluationVerdict(
        VerdictOutcome outcome,
        double score,
        String feedback,
        List<String> suggestions,
        Instant evaluatedAt) {

    public EvaluationVerdict {
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (Double.isNaN(score) || score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("score must be in [0.0, 1.0], got " + score);
        }
        Objects.requireNonNull(feedback, "feedback must not be null");
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
    }

    /** Possible verdict outcomes. */
    public enum VerdictOutcome {

        /** The artifact satisfies the rubric and the step is done. */
        PASS,

        /** The artifact needs revision; the coordinator should loop back to GENERATING. */
        REVISE,

        /**
         * The feedback loop exhausted its budget or the evaluator itself failed; the coordinator
         * terminates the step (and typically the team) rather than silently pass.
         */
        REVIEW_EXCEEDED,

        /**
         * LOW-risk opt-in: the evaluator crashed but the caller explicitly accepted passing the
         * artifact with a warning. Never selected automatically under MEDIUM / HIGH risk.
         */
        AUTO_PASS_WITH_WARNING
    }
}
