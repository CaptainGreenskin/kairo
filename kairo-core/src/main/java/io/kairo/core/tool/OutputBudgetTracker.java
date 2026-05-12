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
package io.kairo.core.tool;

import io.kairo.api.tool.OutputBudgetConfig;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mutable per-iteration tracker for output budget consumption.
 *
 * <p>Injected via Reactor Context at iteration start; shared across all tool executions within one
 * agent iteration (turn). Thread-safe via {@link AtomicLong}.
 *
 * @since 1.2.0
 */
public class OutputBudgetTracker {

    /** Reactor Context key for propagation. */
    public static final Class<OutputBudgetTracker> CONTEXT_KEY = OutputBudgetTracker.class;

    private final OutputBudgetConfig config;
    private final AtomicLong consumedBytes = new AtomicLong(0);

    public OutputBudgetTracker(OutputBudgetConfig config) {
        this.config = config != null ? config : OutputBudgetConfig.DEFAULT;
    }

    /** Record bytes consumed and return the new total. */
    public long consume(long bytes) {
        return consumedBytes.addAndGet(bytes);
    }

    /** Check if per-tool budget would be exceeded by this output size. */
    public boolean exceedsPerToolLimit(long outputBytes) {
        return outputBytes > config.maxPerToolBytes();
    }

    /** Check if per-turn budget would be exceeded by adding these bytes. */
    public boolean exceedsPerTurnLimit(long additionalBytes) {
        return consumedBytes.get() + additionalBytes > config.maxPerTurnBytes();
    }

    /** Remaining bytes available in the per-turn budget. */
    public long remainingTurnBudget() {
        return Math.max(0, config.maxPerTurnBytes() - consumedBytes.get());
    }

    public long consumed() {
        return consumedBytes.get();
    }

    public OutputBudgetConfig config() {
        return config;
    }
}
