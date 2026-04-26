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
import java.util.List;
import java.util.Objects;

/**
 * Input passed to an {@link EvaluationStrategy#evaluate(EvaluationContext)} call.
 *
 * @param step the step whose artifact is being evaluated; non-null
 * @param artifact the generated artifact (typically the agent's output text); non-null
 * @param attemptNumber one-based attempt counter; must be {@code >= 1}
 * @param priorVerdicts verdicts for earlier attempts on the same step, in chronological order;
 *     defensively copied, never {@code null}
 * @param config the team configuration in effect for this execution; non-null
 * @since v0.10 (Experimental)
 */
@Experimental("Team evaluation context; introduced in v0.10, targeting stabilization in v1.1")
public record EvaluationContext(
        TeamStep step,
        String artifact,
        int attemptNumber,
        List<EvaluationVerdict> priorVerdicts,
        TeamConfig config) {

    public EvaluationContext {
        Objects.requireNonNull(step, "step must not be null");
        Objects.requireNonNull(artifact, "artifact must not be null");
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be >= 1, got " + attemptNumber);
        }
        priorVerdicts = priorVerdicts == null ? List.of() : List.copyOf(priorVerdicts);
        Objects.requireNonNull(config, "config must not be null");
    }
}
