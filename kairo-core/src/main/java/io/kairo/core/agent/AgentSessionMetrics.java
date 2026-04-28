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
package io.kairo.core.agent;

import java.time.Instant;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Immutable snapshot of a single agent session's execution metrics.
 *
 * <p>Recorded by {@link AgentMetricsCollector} at the end of each {@code call()} invocation.
 */
public record AgentSessionMetrics(
        String agentId,
        String agentName,
        Instant startTime,
        @Nullable Instant endTime,
        long totalTokensUsed,
        int totalIterations,
        int totalToolCalls,
        Map<String, Integer> toolCallCounts,
        boolean succeeded,
        @Nullable String failureReason) {

    /** Convenience factory for a successful session. */
    public static AgentSessionMetrics success(
            String agentId,
            String agentName,
            Instant startTime,
            Instant endTime,
            long totalTokensUsed,
            int totalIterations,
            int totalToolCalls,
            Map<String, Integer> toolCallCounts) {
        return new AgentSessionMetrics(
                agentId,
                agentName,
                startTime,
                endTime,
                totalTokensUsed,
                totalIterations,
                totalToolCalls,
                Map.copyOf(toolCallCounts),
                true,
                null);
    }

    /** Convenience factory for a failed session. */
    public static AgentSessionMetrics failure(
            String agentId,
            String agentName,
            Instant startTime,
            Instant endTime,
            long totalTokensUsed,
            int totalIterations,
            int totalToolCalls,
            Map<String, Integer> toolCallCounts,
            String failureReason) {
        return new AgentSessionMetrics(
                agentId,
                agentName,
                startTime,
                endTime,
                totalTokensUsed,
                totalIterations,
                totalToolCalls,
                Map.copyOf(toolCallCounts),
                false,
                failureReason);
    }

    /** Duration in milliseconds, or -1 if session is still running. */
    public long durationMs() {
        if (endTime == null) return -1L;
        return endTime.toEpochMilli() - startTime.toEpochMilli();
    }
}
