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
package io.kairo.api.cost;

import io.kairo.api.Experimental;

/**
 * Immutable snapshot of cumulative token usage and estimated cost across model calls.
 *
 * <p>Returned by {@link CostTracker#summary()}. All token counts are cumulative since the last
 * {@link CostTracker#reset()} (or since tracker creation).
 *
 * @param inputTokens cumulative input (prompt) tokens
 * @param outputTokens cumulative output (completion) tokens
 * @param cacheReadTokens cumulative cache-read tokens (Anthropic prompt caching)
 * @param cacheCreationTokens cumulative cache-creation tokens
 * @param estimatedCostUsd estimated total cost in USD based on public pricing
 * @param callCount number of model calls recorded
 */
@Experimental("CostTracker SPI v0.10")
public record UsageSummary(
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheCreationTokens,
        double estimatedCostUsd,
        int callCount) {

    /** Total tokens consumed (input + output, excludes cache tokens). */
    public long totalTokens() {
        return inputTokens + outputTokens;
    }
}
