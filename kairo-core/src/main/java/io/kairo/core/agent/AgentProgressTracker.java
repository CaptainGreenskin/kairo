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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe tracker for an agent's execution progress across ReAct loop iterations.
 *
 * <p>Updated by {@link ReActLoop} after every iteration. Consumers (e.g. Spring Boot Actuator
 * endpoints) read the latest {@link ProgressSnapshot} via {@link #getSnapshot()}.
 */
public class AgentProgressTracker {

    private final Instant startTime;
    private final AtomicReference<ProgressSnapshot> current;

    /**
     * Create a new tracker initialised with the agent's iteration ceiling.
     *
     * @param maxIterations the {@code AgentConfig.maxIterations()} value; 0 means unknown
     */
    public AgentProgressTracker(int maxIterations) {
        this.startTime = Instant.now();
        this.current = new AtomicReference<>(ProgressSnapshot.initial(maxIterations));
    }

    /**
     * Record progress after an iteration completes.
     *
     * @param iteration the iteration number that just finished (1-based)
     * @param activity short description of what was done (e.g. "tool call: read")
     * @param toolCalls cumulative tool-call count for this session
     * @param tokens cumulative token count for this session
     */
    public void update(int iteration, String activity, int toolCalls, long tokens) {
        long elapsedMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();
        int max = current.get().maxIterations();
        current.set(ProgressSnapshot.of(iteration, max, activity, elapsedMs, toolCalls, tokens));
    }

    /** Returns the most recent progress snapshot. Never null. */
    public ProgressSnapshot getSnapshot() {
        return current.get();
    }

    /**
     * Reset the tracker for a new invocation, preserving the start-time reference.
     *
     * @param maxIterations iteration ceiling for the new invocation
     */
    public void reset(int maxIterations) {
        current.set(ProgressSnapshot.initial(maxIterations));
    }
}
