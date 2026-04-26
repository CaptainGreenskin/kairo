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

/**
 * Risk posture that governs evaluator-crash semantics and evaluator selection.
 *
 * <p>Per ADR-015 §"Failure semantics":
 *
 * <ul>
 *   <li>{@link #LOW} may opt into {@link EvaluationVerdict.VerdictOutcome#AUTO_PASS_WITH_WARNING}
 *       when the evaluator crashes — this is an explicit, auditable choice.
 *   <li>{@link #MEDIUM} and {@link #HIGH} always fall through to {@link
 *       EvaluationVerdict.VerdictOutcome#REVIEW_EXCEEDED}; silent auto-pass is never permitted.
 * </ul>
 *
 * @since v0.10 (Experimental)
 */
@Experimental("Team risk profile enum; introduced in v0.10, targeting stabilization in v1.1")
public enum RiskProfile {

    /**
     * Low-risk workloads (non-regulated internal tooling). May opt into {@link
     * EvaluationVerdict.VerdictOutcome#AUTO_PASS_WITH_WARNING} on evaluator crash.
     */
    LOW,

    /**
     * Medium-risk workloads. Evaluator crashes always degrade to {@link
     * EvaluationVerdict.VerdictOutcome#REVIEW_EXCEEDED}.
     */
    MEDIUM,

    /**
     * High-risk workloads (regulated / safety-critical). Evaluator crashes always degrade to {@link
     * EvaluationVerdict.VerdictOutcome#REVIEW_EXCEEDED}.
     */
    HIGH
}
