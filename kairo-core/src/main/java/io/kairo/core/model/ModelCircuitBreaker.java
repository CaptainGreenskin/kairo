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
package io.kairo.core.model;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Three-state circuit breaker for model API calls.
 *
 * <p>State transitions:
 *
 * <pre>
 * CLOSED → (failureThreshold consecutive failures) → OPEN
 * OPEN → (after resetTimeout) → HALF_OPEN
 * HALF_OPEN → (success) → CLOSED
 * HALF_OPEN → (failure) → OPEN
 * </pre>
 *
 * <p>Thread-safe: uses {@link AtomicInteger} for failure count and volatile for state.
 */
public class ModelCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(ModelCircuitBreaker.class);

    /** Circuit breaker states. */
    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final String modelId;
    private final int failureThreshold;
    private final Duration resetTimeout;

    private volatile State state = State.CLOSED;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private volatile long lastFailureTime = 0;

    /**
     * Create a circuit breaker with default settings (threshold=5, timeout=60s).
     *
     * @param modelId identifier for the model this breaker protects
     */
    public ModelCircuitBreaker(String modelId) {
        this(modelId, 5, Duration.ofSeconds(60));
    }

    /**
     * Create a circuit breaker with custom settings.
     *
     * @param modelId identifier for the model this breaker protects
     * @param failureThreshold number of consecutive failures before opening
     * @param resetTimeout duration to wait before transitioning from OPEN to HALF_OPEN
     */
    public ModelCircuitBreaker(String modelId, int failureThreshold, Duration resetTimeout) {
        this.modelId = modelId;
        this.failureThreshold = failureThreshold;
        this.resetTimeout = resetTimeout;
    }

    /**
     * Check whether a call is allowed through the circuit breaker.
     *
     * @return true if the call is allowed, false if the circuit is open
     */
    public boolean allowCall() {
        return switch (state) {
            case CLOSED -> true;
            case OPEN -> {
                long elapsed = System.currentTimeMillis() - lastFailureTime;
                if (elapsed >= resetTimeout.toMillis()) {
                    state = State.HALF_OPEN;
                    log.info(
                            "Circuit breaker for model '{}' transitioning OPEN → HALF_OPEN"
                                    + " ({}ms elapsed since last failure)",
                            modelId,
                            elapsed);
                    yield true;
                }
                yield false;
            }
            case HALF_OPEN -> true;
        };
    }

    /** Record a successful call. Resets the circuit breaker to CLOSED state. */
    public void recordSuccess() {
        if (state == State.HALF_OPEN) {
            log.info("Circuit breaker for model '{}' transitioning HALF_OPEN → CLOSED", modelId);
        }
        failureCount.set(0);
        state = State.CLOSED;
    }

    /** Record a failed call. Increments failure count and may open the circuit. */
    public void recordFailure() {
        lastFailureTime = System.currentTimeMillis();

        if (state == State.HALF_OPEN) {
            state = State.OPEN;
            log.warn(
                    "Circuit breaker for model '{}' transitioning HALF_OPEN → OPEN"
                            + " (probe call failed)",
                    modelId);
            return;
        }

        int count = failureCount.incrementAndGet();
        if (count >= failureThreshold && state == State.CLOSED) {
            state = State.OPEN;
            log.warn(
                    "Circuit breaker for model '{}' transitioning CLOSED → OPEN"
                            + " ({} consecutive failures >= threshold {})",
                    modelId,
                    count,
                    failureThreshold);
        }
    }

    /**
     * Get the current state of the circuit breaker.
     *
     * @return the current state
     */
    public State getState() {
        return state;
    }

    /**
     * Get the model identifier.
     *
     * @return the model ID
     */
    public String getModelId() {
        return modelId;
    }
}
