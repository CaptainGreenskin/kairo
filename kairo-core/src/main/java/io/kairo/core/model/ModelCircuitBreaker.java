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

import io.kairo.core.resilience.CircuitBreakerPrimitive;
import java.time.Duration;
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
    private final CircuitBreakerPrimitive delegate;

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
        this.delegate = new CircuitBreakerPrimitive(failureThreshold, resetTimeout);
    }

    /**
     * Check whether a call is allowed through the circuit breaker.
     *
     * @return true if the call is allowed, false if the circuit is open
     */
    public boolean allowCall() {
        State before = getState();
        boolean allowed = delegate.allowCall();
        State after = getState();
        if (before == State.OPEN && after == State.HALF_OPEN && allowed) {
            log.info("Circuit breaker for model '{}' transitioning OPEN → HALF_OPEN", modelId);
        }
        return allowed;
    }

    /** Record a successful call. Resets the circuit breaker to CLOSED state. */
    public void recordSuccess() {
        if (getState() == State.HALF_OPEN) {
            log.info("Circuit breaker for model '{}' transitioning HALF_OPEN → CLOSED", modelId);
        }
        delegate.recordSuccess();
    }

    /** Record a failed call. Increments failure count and may open the circuit. */
    public void recordFailure() {
        State before = getState();
        delegate.recordFailure();
        State after = getState();
        if (before == State.HALF_OPEN && after == State.OPEN) {
            log.warn(
                    "Circuit breaker for model '{}' transitioning HALF_OPEN → OPEN"
                            + " (probe call failed)",
                    modelId);
            return;
        }
        if (before == State.CLOSED && after == State.OPEN) {
            log.warn(
                    "Circuit breaker for model '{}' transitioning CLOSED → OPEN"
                            + " (consecutive failures >= threshold {})",
                    modelId,
                    delegate.failureThreshold());
        }
    }

    /**
     * Get the current state of the circuit breaker.
     *
     * @return the current state
     */
    public State getState() {
        return switch (delegate.state()) {
            case CLOSED -> State.CLOSED;
            case OPEN -> State.OPEN;
            case HALF_OPEN -> State.HALF_OPEN;
        };
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
