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
import java.time.Duration;
import java.util.Objects;

/**
 * Team-level resource budget.
 *
 * <p>This VO is <strong>independent</strong> of the per-agent {@code
 * io.kairo.api.execution.ResourceConstraint} SPI (ADR-012). Per-agent constraints govern a single
 * agent loop; {@link TeamResourceConstraint} bounds the aggregate budget for a whole team
 * execution. A team-level reuse of the per-agent shape would either double-account tokens or force
 * awkward aggregation semantics, so ADR-015 §"Resource constraints" keeps them as separate
 * contracts.
 *
 * @param maxTotalTokens cumulative token budget across every step; must be {@code > 0} to be
 *     meaningful, {@link Long#MAX_VALUE} to indicate "unbounded"
 * @param maxDuration wall-clock budget; non-null, positive, or {@link Duration#ofNanos(long)
 *     Duration.ofNanos(Long.MAX_VALUE)} for "unbounded"
 * @param maxParallelSteps maximum steps executing in parallel; must be {@code >= 1}
 * @param maxFeedbackRounds maximum revise loops per step; must be {@code >= 1}
 * @since v0.10 (Experimental)
 */
@Experimental("Team resource constraint VO; introduced in v0.10, targeting stabilization in v1.1")
public record TeamResourceConstraint(
        long maxTotalTokens, Duration maxDuration, int maxParallelSteps, int maxFeedbackRounds) {

    public TeamResourceConstraint {
        if (maxTotalTokens <= 0) {
            throw new IllegalArgumentException("maxTotalTokens must be > 0, got " + maxTotalTokens);
        }
        Objects.requireNonNull(maxDuration, "maxDuration must not be null");
        if (maxDuration.isZero() || maxDuration.isNegative()) {
            throw new IllegalArgumentException("maxDuration must be positive, got " + maxDuration);
        }
        if (maxParallelSteps < 1) {
            throw new IllegalArgumentException(
                    "maxParallelSteps must be >= 1, got " + maxParallelSteps);
        }
        if (maxFeedbackRounds < 1) {
            throw new IllegalArgumentException(
                    "maxFeedbackRounds must be >= 1, got " + maxFeedbackRounds);
        }
    }

    /**
     * Convenience factory for callers that do not want to impose any meaningful bound. Uses {@link
     * Long#MAX_VALUE} tokens, a {@link Duration#ofDays(long) 365-day} clock budget, {@link
     * Integer#MAX_VALUE} parallel steps, and {@link Integer#MAX_VALUE} feedback rounds.
     */
    public static TeamResourceConstraint unbounded() {
        return new TeamResourceConstraint(
                Long.MAX_VALUE, Duration.ofDays(365L), Integer.MAX_VALUE, Integer.MAX_VALUE);
    }
}
