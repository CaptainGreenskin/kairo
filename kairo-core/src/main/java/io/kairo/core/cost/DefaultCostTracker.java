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
package io.kairo.core.cost;

import io.kairo.api.cost.CostTracker;
import io.kairo.api.cost.UsageSummary;
import io.kairo.api.model.ModelResponse;
import io.kairo.core.model.ModelPricing;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Default in-memory {@link CostTracker} that accumulates token usage and estimates cost using
 * {@link ModelPricing}.
 *
 * <p>Thread-safe: all counters use atomic primitives. Suitable for concurrent access from the agent
 * loop and external readers (CLI commands, dashboards).
 */
public final class DefaultCostTracker implements CostTracker {

    private final AtomicLong inputTokens = new AtomicLong();
    private final AtomicLong outputTokens = new AtomicLong();
    private final AtomicLong cacheReadTokens = new AtomicLong();
    private final AtomicLong cacheCreationTokens = new AtomicLong();
    private final DoubleAdder costUsd = new DoubleAdder();
    private final AtomicInteger callCount = new AtomicInteger();

    @Override
    public void recordUsage(String model, ModelResponse.Usage usage) {
        if (usage == null) return;
        inputTokens.addAndGet(usage.inputTokens());
        outputTokens.addAndGet(usage.outputTokens());
        cacheReadTokens.addAndGet(usage.cacheReadTokens());
        cacheCreationTokens.addAndGet(usage.cacheCreationTokens());
        callCount.incrementAndGet();
        ModelPricing.estimateUsd(model, usage).ifPresent(costUsd::add);
    }

    @Override
    public UsageSummary summary() {
        return new UsageSummary(
                inputTokens.get(),
                outputTokens.get(),
                cacheReadTokens.get(),
                cacheCreationTokens.get(),
                costUsd.sum(),
                callCount.get());
    }

    @Override
    public void reset() {
        inputTokens.set(0);
        outputTokens.set(0);
        cacheReadTokens.set(0);
        cacheCreationTokens.set(0);
        costUsd.reset();
        callCount.set(0);
    }
}
