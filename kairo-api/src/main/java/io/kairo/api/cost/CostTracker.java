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
import io.kairo.api.model.ModelResponse;

/**
 * SPI for tracking cumulative token usage and estimated cost across model calls.
 *
 * <p>The framework calls {@link #recordUsage(String, ModelResponse.Usage)} after every model
 * invocation. Consumers call {@link #summary()} to read the current totals.
 *
 * <p>Implementations must be thread-safe — the agent loop may invoke {@code recordUsage} from
 * different iterations concurrently (e.g., during parallel tool execution with nested model calls).
 *
 * @see NoopCostTracker
 * @see UsageSummary
 */
@Experimental("CostTracker SPI v0.10")
public interface CostTracker {

    /**
     * Record token usage from a single model call.
     *
     * @param model the model identifier (e.g., "claude-sonnet-4-20250514")
     * @param usage the usage data from the model response
     */
    void recordUsage(String model, ModelResponse.Usage usage);

    /**
     * Return an immutable snapshot of cumulative usage and estimated cost.
     *
     * @return current totals, never {@code null}
     */
    UsageSummary summary();

    /** Reset all cumulative counters to zero. */
    void reset();
}
