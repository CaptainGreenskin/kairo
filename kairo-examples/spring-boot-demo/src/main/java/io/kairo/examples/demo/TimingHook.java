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
package io.kairo.examples.demo;

import io.kairo.api.hook.PostActing;
import io.kairo.api.hook.PostActingEvent;
import io.kairo.api.hook.PostReasoning;
import io.kairo.api.hook.PostReasoningEvent;
import io.kairo.api.hook.PreActing;
import io.kairo.api.hook.PreActingEvent;
import io.kairo.api.hook.PreReasoning;
import io.kairo.api.hook.PreReasoningEvent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Hook that records timing metrics for reasoning and acting phases of the agent loop.
 *
 * <p>Tracks the following metrics:
 *
 * <ul>
 *   <li>Iteration count (number of complete reasoning + acting cycles)
 *   <li>Total time spent in reasoning (model call) phases
 *   <li>Total time spent in acting (tool execution) phases
 * </ul>
 *
 * <p>All metrics are stored in thread-safe data structures and can be retrieved via {@link
 * #getMetrics()} for external inspection.
 */
public class TimingHook {

    private final AtomicInteger iterationCount = new AtomicInteger(0);
    private final AtomicLong totalReasoningTimeMs = new AtomicLong(0);
    private final AtomicLong totalActingTimeMs = new AtomicLong(0);
    private final ConcurrentHashMap<String, Long> startTimestamps = new ConcurrentHashMap<>();

    /**
     * Record the start timestamp before the reasoning phase.
     *
     * @param event the pre-reasoning event
     * @return the unmodified event
     */
    @PreReasoning
    public PreReasoningEvent onPreReasoning(PreReasoningEvent event) {
        startTimestamps.put("reasoning", System.currentTimeMillis());
        return event;
    }

    /**
     * Calculate and accumulate the reasoning phase duration. Increments the iteration count.
     *
     * @param event the post-reasoning event
     * @return the unmodified event
     */
    @PostReasoning
    public PostReasoningEvent onPostReasoning(PostReasoningEvent event) {
        Long start = startTimestamps.remove("reasoning");
        if (start != null) {
            totalReasoningTimeMs.addAndGet(System.currentTimeMillis() - start);
        }
        iterationCount.incrementAndGet();
        return event;
    }

    /**
     * Record the start timestamp before the acting phase.
     *
     * @param event the pre-acting event
     * @return the unmodified event
     */
    @PreActing
    public PreActingEvent onPreActing(PreActingEvent event) {
        startTimestamps.put("acting-" + event.toolName(), System.currentTimeMillis());
        return event;
    }

    /**
     * Calculate and accumulate the acting phase duration.
     *
     * @param event the post-acting event
     * @return the unmodified event
     */
    @PostActing
    public PostActingEvent onPostActing(PostActingEvent event) {
        Long start = startTimestamps.remove("acting-" + event.toolName());
        if (start != null) {
            totalActingTimeMs.addAndGet(System.currentTimeMillis() - start);
        }
        return event;
    }

    /**
     * Get a snapshot of all collected timing metrics.
     *
     * @return a map containing iteration count, total reasoning time, and total acting time
     */
    public Map<String, Object> getMetrics() {
        return Map.of(
                "iterationCount", iterationCount.get(),
                "totalReasoningTimeMs", totalReasoningTimeMs.get(),
                "totalActingTimeMs", totalActingTimeMs.get());
    }

    /** Reset all timing metrics to their initial values. */
    public void reset() {
        iterationCount.set(0);
        totalReasoningTimeMs.set(0);
        totalActingTimeMs.set(0);
        startTimestamps.clear();
    }
}
