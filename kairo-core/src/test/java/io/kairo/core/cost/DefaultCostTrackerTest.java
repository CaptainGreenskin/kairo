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

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.cost.NoopCostTracker;
import io.kairo.api.cost.UsageSummary;
import io.kairo.api.model.ModelResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultCostTrackerTest {

    private DefaultCostTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new DefaultCostTracker();
    }

    @Test
    void initialSummaryIsZero() {
        UsageSummary s = tracker.summary();
        assertEquals(0, s.inputTokens());
        assertEquals(0, s.outputTokens());
        assertEquals(0, s.cacheReadTokens());
        assertEquals(0, s.cacheCreationTokens());
        assertEquals(0.0, s.estimatedCostUsd());
        assertEquals(0, s.callCount());
        assertEquals(0, s.totalTokens());
    }

    @Test
    void recordUsageAccumulatesTokens() {
        tracker.recordUsage("claude-sonnet-4-20250514", usage(1000, 500, 200, 50));
        tracker.recordUsage("claude-sonnet-4-20250514", usage(2000, 1000, 100, 0));

        UsageSummary s = tracker.summary();
        assertEquals(3000, s.inputTokens());
        assertEquals(1500, s.outputTokens());
        assertEquals(300, s.cacheReadTokens());
        assertEquals(50, s.cacheCreationTokens());
        assertEquals(2, s.callCount());
        assertEquals(4500, s.totalTokens());
    }

    @Test
    void recordUsageEstimatesCostForKnownModel() {
        tracker.recordUsage("claude-sonnet-4-20250514", usage(1_000_000, 100_000, 0, 0));

        UsageSummary s = tracker.summary();
        assertTrue(s.estimatedCostUsd() > 0, "cost should be positive for known model");
    }

    @Test
    void recordUsageWithCacheTokensIncludesDiscountPricing() {
        tracker.recordUsage("claude-sonnet-4-20250514", usage(0, 0, 1_000_000, 0));

        UsageSummary s = tracker.summary();
        assertTrue(s.estimatedCostUsd() > 0, "cache read tokens should contribute to cost");
    }

    @Test
    void unknownModelRecordsTokensButZeroCost() {
        tracker.recordUsage("unknown-model-xyz", usage(5000, 2000, 0, 0));

        UsageSummary s = tracker.summary();
        assertEquals(5000, s.inputTokens());
        assertEquals(2000, s.outputTokens());
        assertEquals(0.0, s.estimatedCostUsd());
        assertEquals(1, s.callCount());
    }

    @Test
    void resetClearsAllCounters() {
        tracker.recordUsage("claude-sonnet-4-20250514", usage(1000, 500, 100, 50));
        tracker.reset();

        UsageSummary s = tracker.summary();
        assertEquals(0, s.inputTokens());
        assertEquals(0, s.outputTokens());
        assertEquals(0, s.cacheReadTokens());
        assertEquals(0, s.cacheCreationTokens());
        assertEquals(0.0, s.estimatedCostUsd());
        assertEquals(0, s.callCount());
    }

    @Test
    void nullUsageIsIgnored() {
        tracker.recordUsage("claude-sonnet-4-20250514", null);

        UsageSummary s = tracker.summary();
        assertEquals(0, s.callCount());
    }

    @Test
    void concurrentRecordUsageIsSafe() throws Exception {
        int threads = 8;
        int callsPerThread = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(
                    pool.submit(
                            () -> {
                                try {
                                    latch.await();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                for (int i = 0; i < callsPerThread; i++) {
                                    tracker.recordUsage("gpt-4o", usage(10, 5, 0, 0));
                                }
                            }));
        }

        latch.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        UsageSummary s = tracker.summary();
        int expectedCalls = threads * callsPerThread;
        assertEquals(expectedCalls, s.callCount());
        assertEquals((long) expectedCalls * 10, s.inputTokens());
        assertEquals((long) expectedCalls * 5, s.outputTokens());
    }

    @Test
    void noopTrackerReturnEmptySummary() {
        NoopCostTracker noop = NoopCostTracker.INSTANCE;
        noop.recordUsage("claude-sonnet-4-20250514", usage(1000, 500, 0, 0));

        UsageSummary s = noop.summary();
        assertEquals(0, s.inputTokens());
        assertEquals(0, s.callCount());
    }

    private static ModelResponse.Usage usage(
            int input, int output, int cacheRead, int cacheCreate) {
        return new ModelResponse.Usage(input, output, cacheRead, cacheCreate);
    }
}
