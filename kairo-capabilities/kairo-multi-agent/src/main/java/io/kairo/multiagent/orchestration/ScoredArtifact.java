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

import io.kairo.api.team.EvaluationVerdict;
import java.util.Objects;

/**
 * An artifact produced by a parallel step, paired with its evaluation score and verdict.
 *
 * <p>Used by {@link ArchitectArbitrator} to detect score divergence across parallel group steps.
 *
 * @param stepId the step that produced this artifact
 * @param roleId the role that executed the step
 * @param output the generated artifact text
 * @param score the evaluation score in {@code [0.0, 1.0]}
 * @param verdict the evaluation verdict for this artifact
 * @since v0.10 (Experimental)
 */
public record ScoredArtifact(
        String stepId, String roleId, String output, double score, EvaluationVerdict verdict) {

    public ScoredArtifact {
        Objects.requireNonNull(stepId, "stepId must not be null");
        Objects.requireNonNull(roleId, "roleId must not be null");
        Objects.requireNonNull(output, "output must not be null");
        if (Double.isNaN(score) || score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("score must be in [0.0, 1.0], got " + score);
        }
        Objects.requireNonNull(verdict, "verdict must not be null");
    }
}
