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
package io.kairo.core.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.core.resilience.CircuitBreakerPrimitive.State;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Scenario-level tests for the complete CircuitBreakerPrimitive state machine lifecycle.
 * Complements unit tests in CircuitBreakerPrimitiveTest with full-cycle documentation.
 */
class CircuitBreakerStateMachineTest {

    @Test
    @DisplayName("CLOSED: initial state allows all calls")
    void closedState_allowsAllCalls() {
        CircuitBreakerPrimitive cb = new CircuitBreakerPrimitive(3, Duration.ofSeconds(60));

        assertThat(cb.state()).isEqualTo(State.CLOSED);
        assertThat(cb.allowCall()).isTrue();
        assertThat(cb.allowCall()).isTrue();
    }

    @Test
    @DisplayName("CLOSED → OPEN: reaching failure threshold opens the breaker")
    void closedToOpen_atFailureThreshold() {
        CircuitBreakerPrimitive cb = new CircuitBreakerPrimitive(3, Duration.ofSeconds(60));

        cb.recordFailure();
        assertThat(cb.state()).isEqualTo(State.CLOSED);

        cb.recordFailure();
        assertThat(cb.state()).isEqualTo(State.CLOSED);

        cb.recordFailure(); // 3rd = at threshold
        assertThat(cb.state()).isEqualTo(State.OPEN);
        assertThat(cb.isOpen()).isTrue();
    }

    @Test
    @DisplayName("OPEN: rejects calls before timeout expires")
    void openState_rejectsCallsBeforeTimeout() {
        CircuitBreakerPrimitive cb = new CircuitBreakerPrimitive(1, Duration.ofSeconds(60));
        cb.recordFailure(); // → OPEN

        assertThat(cb.state()).isEqualTo(State.OPEN);
        assertThat(cb.allowCall()).isFalse();
        assertThat(cb.allowCall()).isFalse();
    }

    @Test
    @DisplayName("OPEN → HALF_OPEN: zero timeout transitions immediately")
    void openToHalfOpen_zeroTimeout() {
        CircuitBreakerPrimitive cb = new CircuitBreakerPrimitive(1, Duration.ZERO);
        cb.recordFailure(); // → OPEN immediately

        // With zero timeout, next allowCall should transition to HALF_OPEN
        boolean allowed = cb.allowCall();
        assertThat(allowed).isTrue();
        assertThat(cb.state()).isEqualTo(State.HALF_OPEN);
    }

    @Test
    @DisplayName("HALF_OPEN → CLOSED: success in half-open state closes the breaker")
    void halfOpenToClosed_onSuccess() {
        CircuitBreakerPrimitive cb = new CircuitBreakerPrimitive(1, Duration.ZERO);
        cb.recordFailure(); // → OPEN
        cb.allowCall(); // → HALF_OPEN

        assertThat(cb.state()).isEqualTo(State.HALF_OPEN);

        cb.recordSuccess(); // → CLOSED
        assertThat(cb.state()).isEqualTo(State.CLOSED);
        assertThat(cb.allowCall()).isTrue();
    }

    @Test
    @DisplayName("HALF_OPEN → OPEN: failure in half-open state re-opens the breaker")
    void halfOpenToOpen_onFailure() {
        CircuitBreakerPrimitive cb = new CircuitBreakerPrimitive(1, Duration.ZERO);
        cb.recordFailure(); // → OPEN
        cb.allowCall(); // → HALF_OPEN

        assertThat(cb.state()).isEqualTo(State.HALF_OPEN);

        cb.recordFailure(); // → OPEN again
        assertThat(cb.state()).isEqualTo(State.OPEN);
        assertThat(cb.isOpen()).isTrue();
    }

    @Test
    @DisplayName("Full cycle: CLOSED → OPEN → HALF_OPEN → CLOSED")
    void fullCycle_closedOpenHalfOpenClosed() {
        CircuitBreakerPrimitive cb = new CircuitBreakerPrimitive(2, Duration.ZERO);

        // Phase 1: CLOSED — calls allowed
        assertThat(cb.state()).isEqualTo(State.CLOSED);
        assertThat(cb.allowCall()).isTrue();

        // Phase 2: Failures accumulate → OPEN
        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.state()).isEqualTo(State.OPEN);

        // With zero timeout, allowCall() immediately transitions to HALF_OPEN and allows probe
        assertThat(cb.allowCall()).isTrue();
        assertThat(cb.state()).isEqualTo(State.HALF_OPEN);

        // Phase 3: HALF_OPEN → success → CLOSED
        cb.recordSuccess();
        assertThat(cb.state()).isEqualTo(State.CLOSED);
        assertThat(cb.allowCall()).isTrue();
    }
}
