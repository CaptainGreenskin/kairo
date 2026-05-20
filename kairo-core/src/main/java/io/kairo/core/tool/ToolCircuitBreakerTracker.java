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

import io.kairo.api.event.CircuitBreakerEvent;
import io.kairo.api.event.KairoEventBus;
import io.kairo.api.tool.ToolResult;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
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
    @Nullable private final KairoEventBus eventBus;

    /**
     * Create a new tracker with the given threshold.
     *
     * @param threshold consecutive failures before a tool is circuit-broken
     */
    public ToolCircuitBreakerTracker(int threshold) {
        this(threshold, null);
    }

    /**
     * Create a new tracker with the given threshold and an event bus for observability.
     *
     * @param threshold consecutive failures before a tool is circuit-broken
     * @param eventBus optional event bus to publish state transition events
     */
    public ToolCircuitBreakerTracker(int threshold, @Nullable KairoEventBus eventBus) {
        this.threshold = threshold > 0 ? threshold : 3;
        this.eventBus = eventBus;
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
     * <p>Only infrastructure-level failures (TIMEOUT, CANCELLED) count toward the circuit breaker
     * threshold. Application-level errors (e.g., bash returning non-zero) are expected during
     * normal debugging and do not trip the breaker.
     *
     * @param cbKey the composite circuit-breaker key
     * @param result the tool result to evaluate
     */
    public void track(String cbKey, ToolResult result) {
        if (isInfrastructureFailure(result)) {
            AtomicInteger counter =
                    consecutiveFailures.computeIfAbsent(cbKey, k -> new AtomicInteger());
            int count = counter.incrementAndGet();
            if (count == threshold) {
                log.warn(
                        "Circuit breaker for tool '{}' tripped OPEN"
                                + " (consecutive infrastructure failures >= threshold {})",
                        cbKey,
                        threshold);
                publish(cbKey, CircuitBreakerEvent.State.CLOSED, CircuitBreakerEvent.State.OPEN);
            }
        } else {
            AtomicInteger prev = consecutiveFailures.remove(cbKey);
            if (prev != null) {
                log.info("Circuit breaker for tool '{}' reset to CLOSED", cbKey);
                publish(cbKey, CircuitBreakerEvent.State.OPEN, CircuitBreakerEvent.State.CLOSED);
            }
        }
    }

    private static boolean isInfrastructureFailure(ToolResult result) {
        if (result == null) return false;
        var outcome = result.outcome();
        return outcome == io.kairo.api.tool.ToolOutcome.TIMEOUT
                || outcome == io.kairo.api.tool.ToolOutcome.CANCELLED;
    }

    private void publish(
            String cbKey, CircuitBreakerEvent.State from, CircuitBreakerEvent.State to) {
        if (eventBus != null) {
            eventBus.publish(new CircuitBreakerEvent(cbKey, from, to).toKairoEvent());
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
