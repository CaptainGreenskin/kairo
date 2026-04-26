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

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Recovery window tests for {@link CircuitBreakerPrimitive}: verifies that a tripped breaker
 * transitions to HALF_OPEN after the configurable recovery window elapses, then closes on success
 * or reopens on failure.
 */
class CircuitBreakerRecoveryTest {

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void openTransitionsToHalfOpenAfterRecoveryWindow() {
        var cb = new CircuitBreakerPrimitive(2, Duration.ofMillis(50));
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(CircuitBreakerPrimitive.State.OPEN, cb.state());
        assertFalse(cb.allowCall()); // still within window

        sleep(100);

        assertTrue(cb.allowCall()); // window elapsed → HALF_OPEN
        assertEquals(CircuitBreakerPrimitive.State.HALF_OPEN, cb.state());
    }

    @Test
    void halfOpenTransitionsToClosedOnSuccess() {
        var cb = new CircuitBreakerPrimitive(2, Duration.ofMillis(30));
        cb.recordFailure();
        cb.recordFailure();
        sleep(60);

        cb.allowCall(); // → HALF_OPEN
        cb.recordSuccess(); // → CLOSED

        assertEquals(CircuitBreakerPrimitive.State.CLOSED, cb.state());
        assertTrue(cb.allowCall());
    }

    @Test
    void halfOpenTransitionsBackToOpenOnFailure() {
        var cb = new CircuitBreakerPrimitive(2, Duration.ofMillis(30));
        cb.recordFailure();
        cb.recordFailure();
        sleep(60);

        cb.allowCall(); // → HALF_OPEN
        cb.recordFailure(); // → OPEN

        assertEquals(CircuitBreakerPrimitive.State.OPEN, cb.state());
        assertFalse(cb.allowCall()); // closed again within new window
    }

    @Test
    void recoveryDoesNotTriggerBeforeWindowElapses() {
        var cb = new CircuitBreakerPrimitive(2, Duration.ofSeconds(60));
        cb.recordFailure();
        cb.recordFailure();

        // Window is 60s; call immediately — should stay OPEN
        assertFalse(cb.allowCall());
        assertEquals(CircuitBreakerPrimitive.State.OPEN, cb.state());
    }

    @Test
    void multipleRecoveryAttemptsEventuallySucceed() {
        // Full cycle: OPEN → HALF_OPEN(fail) → OPEN → HALF_OPEN(success) → CLOSED
        var cb = new CircuitBreakerPrimitive(2, Duration.ofMillis(20));
        cb.recordFailure();
        cb.recordFailure();

        // First probe fails
        sleep(40);
        cb.allowCall(); // → HALF_OPEN
        cb.recordFailure(); // → OPEN
        assertEquals(CircuitBreakerPrimitive.State.OPEN, cb.state());

        // Second probe succeeds
        sleep(40);
        cb.allowCall(); // → HALF_OPEN
        cb.recordSuccess(); // → CLOSED
        assertEquals(CircuitBreakerPrimitive.State.CLOSED, cb.state());
        assertTrue(cb.allowCall());
    }
}
