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

/**
 * Immutable snapshot of an agent's execution progress at a given point in time.
 *
 * <p>Used by {@link AgentProgressTracker} to expose real-time agent state to monitoring systems
 * such as the Spring Boot Actuator endpoint.
 */
public record ProgressSnapshot(
        int currentIteration,
        int maxIterations,
        int percentage,
        String currentActivity,
        long elapsedMs,
        int toolCallsCount,
        long tokensUsed) {

    /** Returns a snapshot representing the state before any iteration has run. */
    public static ProgressSnapshot initial(int maxIterations) {
        return new ProgressSnapshot(0, maxIterations, 0, "Initializing", 0, 0, 0);
    }

    /**
     * Constructs a progress snapshot, clamping the percentage to [0, 100].
     *
     * @param iteration current iteration number (1-based when reporting mid-loop)
     * @param maxIterations the configured upper bound on iterations
     * @param activity human-readable description of what the agent is doing
     * @param elapsedMs wall-clock milliseconds since the agent session started
     * @param toolCalls total number of tool calls executed so far
     * @param tokens total tokens consumed so far
     */
    public static ProgressSnapshot of(
            int iteration,
            int maxIterations,
            String activity,
            long elapsedMs,
            int toolCalls,
            long tokens) {
        int pct = maxIterations > 0 ? Math.min(100, (iteration * 100) / maxIterations) : 0;
        return new ProgressSnapshot(
                iteration, maxIterations, pct, activity, elapsedMs, toolCalls, tokens);
    }
}
