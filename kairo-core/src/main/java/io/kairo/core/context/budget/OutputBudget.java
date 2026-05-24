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
package io.kairo.core.context.budget;

/**
 * A user-declared **output-token** budget for the current turn (e.g. {@code +500k}, {@code spend 2M
 * tokens}). When set, the agent should keep working until the model has produced at least {@link
 * #totalTokens()} worth of output, unless a diminishing-returns guard fires first.
 *
 * <p>Distinct from {@code TokenBudgetManager}, which sizes the model's <em>input context
 * window</em>. Output budget is the user's "how long do you want me to keep at it" knob — it
 * doesn't shrink as tokens are produced; the {@link OutputBudgetTracker} tracks progress
 * externally.
 *
 * <p>Why a record + factory: keeps invariants close (must be positive) and gives the JSON / SDK
 * caller a clean {@code OutputBudget.ofTokens(500_000)} entry point without exposing the {@code <=
 * 0} pitfall.
 *
 * @param totalTokens absolute number of output tokens the user wants the agent to spend; must be
 *     positive.
 * @since 1.3
 */
public record OutputBudget(long totalTokens) {

    public OutputBudget {
        if (totalTokens <= 0) {
            throw new IllegalArgumentException(
                    "Output budget must be positive, got " + totalTokens);
        }
    }

    /** Factory mirroring {@link #OutputBudget(long)} but with a clearer call-site verb. */
    public static OutputBudget ofTokens(long totalTokens) {
        return new OutputBudget(totalTokens);
    }

    /** Convenience for the common {@code N * 1000} ask. */
    public static OutputBudget ofKilo(double k) {
        return new OutputBudget(Math.round(k * 1_000));
    }

    /** Convenience for the common {@code N * 1_000_000} ask. */
    public static OutputBudget ofMega(double m) {
        return new OutputBudget(Math.round(m * 1_000_000));
    }
}
