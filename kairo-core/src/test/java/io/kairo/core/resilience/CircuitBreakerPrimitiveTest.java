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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CircuitBreakerPrimitive} — the shared state machine used by both {@code
 * ModelCircuitBreaker} and {@code CompactionPipeline}.
 *
 * <p>Verifies:
 *
 * <ul>
 *   <li>Three-state transitions: CLOSED → OPEN → HALF_OPEN → CLOSED / OPEN
 *   <li>Half-open probe recovery (one request allowed after cooldown)
 *   <li>Configurable failure thresholds
 *   <li>Reset capability
 *   <li>Thread-safety under concurrent access
 * </ul>
 */
class CircuitBreakerPrimitiveTest {

    // ==================== BASIC STATE MACHINE ====================

    @Test
    void startsInClosedState() {
        var cb = new CircuitBreakerPrimitive(3, Duration.ofSeconds(60));
        assertEquals(CircuitBreakerPrimitive.State.CLOSED, cb.state());
        assertFalse(cb.isOpen());
        assertTrue(cb.allowCall());
    }

    @Test
    void staysClosedBelowThreshold() {
        var cb = new CircuitBreakerPrimitive(5, Duration.ofSeconds(60));
        for (int i = 0; i < 4; i++) {
            cb.recordFailure();
        }
        assertEquals(CircuitBreakerPrimitive.State.CLOSED, cb.state());
        assertTrue(cb.allowCall());
    }

    @Test
    void transitionsToOpenAtThreshold() {
        var cb = new CircuitBreakerPrimitive(3, Duration.ofSeconds(60));
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(CircuitBreakerPrimitive.State.CLOSED, cb.state());

        cb.recordFailure();
        assertEquals(CircuitBreakerPrimitive.State.OPEN, cb.state());
        assertTrue(cb.isOpen());
    }

    @Test
    void openStateRejectsCalls() {
        var cb = new CircuitBreakerPrimitive(2, Duration.ofSeconds(60));
        cb.recordFailure();
        cb.recordFailure();
        assertFalse(cb.allowCall());
    }

    // ==================== HALF-OPEN PROBE RECOVERY ====================

    @Test
    void transitionsToHalfOpenAfterCooldown() {
        var cb = new CircuitBreakerPrimitive(2, Duration.ofMillis(50));
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(CircuitBreakerPrimitive.State.OPEN, cb.state());

        sleep(100);

        // allowCall() triggers the OPEN → HALF_OPEN transition
        assertTrue(cb.allowCall());
        assertEquals(CircuitBreakerPrimitive.State.HALF_OPEN, cb.state());
    }

    @Test
    void halfOpenAllowsProbeCall() {
        var cb = new CircuitBreakerPrimitive(2, Duration.ofMillis(50));
        cb.recordFailure();
        cb.recordFailure();
        sleep(100);

        assertTrue(cb.allowCall()); // transitions to HALF_OPEN
        // Additional calls in HALF_OPEN are allowed (the primitive doesn't limit probes)
        assertTrue(cb.allowCall());
    }

    @Test
    void successInHalfOpenTransitionsToClosed() {
        var cb = new CircuitBreakerPrimitive(2, Duration.ofMillis(50));
        cb.recordFailure();
        cb.recordFailure();
        sleep(100);

        cb.allowCall(); // → HALF_OPEN
        assertEquals(CircuitBreakerPrimitive.State.HALF_OPEN, cb.state());

        cb.recordSuccess();
        assertEquals(CircuitBreakerPrimitive.State.CLOSED, cb.state());
        assertFalse(cb.isOpen());
        assertTrue(cb.allowCall());
    }

    @Test
    void failureInHalfOpenTransitionsBackToOpen() {
        var cb = new CircuitBreakerPrimitive(2, Duration.ofMillis(50));
        cb.recordFailure();
        cb.recordFailure();
        sleep(100);

        cb.allowCall(); // → HALF_OPEN
        assertEquals(CircuitBreakerPrimitive.State.HALF_OPEN, cb.state());

        cb.recordFailure();
        assertEquals(CircuitBreakerPrimitive.State.OPEN, cb.state());
        assertTrue(cb.isOpen());
    }

    @Test
    void halfOpenRecoveryCycle() {
        // Full cycle: CLOSED → OPEN → HALF_OPEN → CLOSED
        var cb = new CircuitBreakerPrimitive(2, Duration.ofMillis(30));

        // Trip the breaker
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(CircuitBreakerPrimitive.State.OPEN, cb.state());

        // Wait for cooldown, probe, succeed
        sleep(60);
        assertTrue(cb.allowCall());
        assertEquals(CircuitBreakerPrimitive.State.HALF_OPEN, cb.state());
        cb.recordSuccess();
        assertEquals(CircuitBreakerPrimitive.State.CLOSED, cb.state());

        // Now it should work normally again
        assertTrue(cb.allowCall());
        assertEquals(CircuitBreakerPrimitive.State.CLOSED, cb.state());
    }

    @Test
    void halfOpenFailureRecoveryCycle() {
        // Full cycle: CLOSED → OPEN → HALF_OPEN → OPEN → HALF_OPEN → CLOSED
        var cb = new CircuitBreakerPrimitive(2, Duration.ofMillis(30));

        cb.recordFailure();
        cb.recordFailure();

        // First probe fails
        sleep(60);
        cb.allowCall();
        cb.recordFailure();
        assertEquals(CircuitBreakerPrimitive.State.OPEN, cb.state());

        // Second probe succeeds
        sleep(60);
        cb.allowCall();
        cb.recordSuccess();
        assertEquals(CircuitBreakerPrimitive.State.CLOSED, cb.state());
    }

    // ==================== CONFIGURABLE THRESHOLDS ====================

    @Test
    void threshold1OpensImmediately() {
        var cb = new CircuitBreakerPrimitive(1, Duration.ofSeconds(60));
        cb.recordFailure();
        assertEquals(CircuitBreakerPrimitive.State.OPEN, cb.state());
    }

    @Test
    void highThresholdRequiresMoreFailures() {
        var cb = new CircuitBreakerPrimitive(10, Duration.ofSeconds(60));
        for (int i = 0; i < 9; i++) {
            cb.recordFailure();
            assertEquals(CircuitBreakerPrimitive.State.CLOSED, cb.state());
        }
        cb.recordFailure();
        assertEquals(CircuitBreakerPrimitive.State.OPEN, cb.state());
    }

    @Test
    void failureThresholdAccessor() {
        var cb = new CircuitBreakerPrimitive(7, Duration.ofSeconds(30));
        assertEquals(7, cb.failureThreshold());
    }

    @Test
    void invalidThresholdThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CircuitBreakerPrimitive(0, Duration.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CircuitBreakerPrimitive(-1, Duration.ZERO));
    }

    @Test
    void zeroTimeoutTransitionsImmediately() {
        var cb = new CircuitBreakerPrimitive(2, Duration.ZERO);
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(CircuitBreakerPrimitive.State.OPEN, cb.state());

        // With Duration.ZERO, allowCall() immediately transitions to HALF_OPEN
        assertTrue(cb.allowCall());
        assertEquals(CircuitBreakerPrimitive.State.HALF_OPEN, cb.state());
    }

    @Test
    void nullTimeoutTreatedAsZero() {
        var cb = new CircuitBreakerPrimitive(2, null);
        cb.recordFailure();
        cb.recordFailure();
        // Should not throw and should transition immediately
        assertTrue(cb.allowCall());
        assertEquals(CircuitBreakerPrimitive.State.HALF_OPEN, cb.state());
    }

    // ==================== RESET ====================

    @Test
    void resetFromOpen() {
        var cb = new CircuitBreakerPrimitive(2, Duration.ofSeconds(60));
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(CircuitBreakerPrimitive.State.OPEN, cb.state());

        cb.reset();
        assertEquals(CircuitBreakerPrimitive.State.CLOSED, cb.state());
        assertTrue(cb.allowCall());
    }

    @Test
    void resetFromHalfOpen() {
        var cb = new CircuitBreakerPrimitive(2, Duration.ofMillis(30));
        cb.recordFailure();
        cb.recordFailure();
        sleep(60);
        cb.allowCall(); // → HALF_OPEN

        cb.reset();
        assertEquals(CircuitBreakerPrimitive.State.CLOSED, cb.state());
    }

    @Test
    void resetClearsFailureCount() {
        var cb = new CircuitBreakerPrimitive(3, Duration.ofSeconds(60));
        cb.recordFailure();
        cb.recordFailure(); // 2 failures, below threshold

        cb.reset();

        // Need 3 more failures after reset to open
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(CircuitBreakerPrimitive.State.CLOSED, cb.state());

        cb.recordFailure();
        assertEquals(CircuitBreakerPrimitive.State.OPEN, cb.state());
    }

    // ==================== SUCCESS RESETS FAILURE COUNT ====================

    @Test
    void successResetsFailureCountInClosed() {
        var cb = new CircuitBreakerPrimitive(3, Duration.ofSeconds(60));
        cb.recordFailure();
        cb.recordFailure();
        cb.recordSuccess(); // resets count

        cb.recordFailure();
        cb.recordFailure();
        assertEquals(CircuitBreakerPrimitive.State.CLOSED, cb.state());

        cb.recordFailure();
        assertEquals(CircuitBreakerPrimitive.State.OPEN, cb.state());
    }

    @Test
    void rapidAlternatingStaysClosed() {
        var cb = new CircuitBreakerPrimitive(3, Duration.ofSeconds(60));
        for (int i = 0; i < 100; i++) {
            cb.recordFailure();
            cb.recordSuccess();
        }
        assertEquals(CircuitBreakerPrimitive.State.CLOSED, cb.state());
    }

    // ==================== THREAD SAFETY ====================

    @Test
    void concurrentFailuresReachOpenState() throws Exception {
        var cb = new CircuitBreakerPrimitive(100, Duration.ofSeconds(60));
        int threads = 10;
        int failuresPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(
                    executor.submit(
                            () -> {
                                try {
                                    latch.await();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                for (int i = 0; i < failuresPerThread; i++) {
                                    cb.recordFailure();
                                }
                            }));
        }

        latch.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertEquals(CircuitBreakerPrimitive.State.OPEN, cb.state());
    }

    @Test
    void concurrentAllowCallDoesNotCorrupt() throws Exception {
        var cb = new CircuitBreakerPrimitive(2, Duration.ofMillis(10));
        cb.recordFailure();
        cb.recordFailure();
        sleep(50);

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            futures.add(
                    executor.submit(
                            () -> {
                                try {
                                    latch.await();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                return cb.allowCall();
                            }));
        }

        latch.countDown();
        int allowed = 0;
        for (Future<Boolean> f : futures) {
            if (f.get()) allowed++;
        }
        executor.shutdown();

        // All should be allowed (HALF_OPEN allows calls)
        assertTrue(allowed > 0);
        // State should be HALF_OPEN (not corrupted)
        assertEquals(CircuitBreakerPrimitive.State.HALF_OPEN, cb.state());
    }

    // ==================== SEMANTICS SHARED BY BOTH CBs ====================

    @Test
    void modelCBAndCompactionCBShareSameStateMachine() {
        // Model CB uses threshold=5, timeout=60s
        var modelPrimitive = new CircuitBreakerPrimitive(5, Duration.ofMillis(50));
        // Compaction CB uses threshold=3, timeout=0 (from CompactionPolicyDefaults)
        var compactionPrimitive = new CircuitBreakerPrimitive(3, Duration.ZERO);

        // Both follow identical state transitions: CLOSED → OPEN
        for (int i = 0; i < 5; i++) modelPrimitive.recordFailure();
        for (int i = 0; i < 3; i++) compactionPrimitive.recordFailure();
        assertEquals(CircuitBreakerPrimitive.State.OPEN, modelPrimitive.state());
        assertEquals(CircuitBreakerPrimitive.State.OPEN, compactionPrimitive.state());

        // Both support OPEN → HALF_OPEN after cooldown
        sleep(100);
        assertTrue(modelPrimitive.allowCall());
        assertTrue(compactionPrimitive.allowCall());
        assertEquals(CircuitBreakerPrimitive.State.HALF_OPEN, modelPrimitive.state());
        assertEquals(CircuitBreakerPrimitive.State.HALF_OPEN, compactionPrimitive.state());

        // Both support HALF_OPEN → CLOSED on success
        modelPrimitive.recordSuccess();
        compactionPrimitive.recordSuccess();
        assertEquals(CircuitBreakerPrimitive.State.CLOSED, modelPrimitive.state());
        assertEquals(CircuitBreakerPrimitive.State.CLOSED, compactionPrimitive.state());
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
