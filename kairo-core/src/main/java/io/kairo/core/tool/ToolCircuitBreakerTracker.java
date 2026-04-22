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

import io.kairo.api.tool.ToolResult;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks consecutive tool failures and trips a circuit breaker when the threshold is exceeded.
 *
 * <p>The circuit breaker key is scoped to {@code toolName::sessionId} when a session is available,
 * or plain {@code toolName} otherwise. This prevents one tenant's failures from tripping the
 * breaker for another.
 *
 * <p>Extracted from {@link DefaultToolExecutor} pipeline.
 */
public final class ToolCircuitBreakerTracker {

    private static final Logger log = LoggerFactory.getLogger(ToolCircuitBreakerTracker.class);

    private final ConcurrentHashMap<String, AtomicInteger> consecutiveFailures =
            new ConcurrentHashMap<>();
    private final int threshold;

    /**
     * Create a new tracker with the given threshold.
     *
     * @param threshold consecutive failures before a tool is circuit-broken
     */
    public ToolCircuitBreakerTracker(int threshold) {
        this.threshold = threshold > 0 ? threshold : 3;
    }

    /**
     * Check whether a call is allowed (i.e., the circuit breaker is not open).
     *
     * @param cbKey the composite circuit-breaker key
     * @return true if the call is allowed, false if the circuit breaker is open
     */
    public boolean allowCall(String cbKey) {
        AtomicInteger failures = consecutiveFailures.get(cbKey);
        return failures == null || failures.get() < threshold;
    }

    /**
     * Get the current failure count for the given key.
     *
     * @param cbKey the composite circuit-breaker key
     * @return the current consecutive failure count
     */
    public int getFailureCount(String cbKey) {
        AtomicInteger failures = consecutiveFailures.get(cbKey);
        return failures == null ? 0 : failures.get();
    }

    /**
     * Track a tool execution result for circuit breaker logic.
     *
     * @param cbKey the composite circuit-breaker key
     * @param result the tool result to evaluate
     */
    public void track(String cbKey, ToolResult result) {
        if (result.isError()) {
            consecutiveFailures.computeIfAbsent(cbKey, k -> new AtomicInteger()).incrementAndGet();
        } else {
            consecutiveFailures.remove(cbKey);
        }
    }

    /** Reset circuit breaker state for all tools. */
    public void reset() {
        consecutiveFailures.clear();
    }

    /**
     * Reset circuit breaker state for a specific tool (across all sessions).
     *
     * @param toolName the tool name
     */
    public void reset(String toolName) {
        consecutiveFailures
                .keySet()
                .removeIf(key -> key.equals(toolName) || key.startsWith(toolName + "::"));
    }
}
