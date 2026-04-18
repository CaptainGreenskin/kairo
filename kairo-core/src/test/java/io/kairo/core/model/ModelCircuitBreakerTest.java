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

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class ModelCircuitBreakerTest {

    @Test
    void startsInClosedState() {
        var breaker = new ModelCircuitBreaker("test-model");
        assertEquals(ModelCircuitBreaker.State.CLOSED, breaker.getState());
        assertTrue(breaker.allowCall());
    }

    @Test
    void staysClosedAfterFewerFailuresThanThreshold() {
        var breaker = new ModelCircuitBreaker("test-model", 5, Duration.ofSeconds(60));
        for (int i = 0; i < 4; i++) {
            breaker.recordFailure();
        }
        assertEquals(ModelCircuitBreaker.State.CLOSED, breaker.getState());
        assertTrue(breaker.allowCall());
    }

    @Test
    void transitionsToOpenAfterFailureThreshold() {
        var breaker = new ModelCircuitBreaker("test-model", 3, Duration.ofSeconds(60));
        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordFailure();
        assertEquals(ModelCircuitBreaker.State.OPEN, breaker.getState());
    }

    @Test
    void openStateRejectsCalls() {
        var breaker = new ModelCircuitBreaker("test-model", 2, Duration.ofSeconds(60));
        breaker.recordFailure();
        breaker.recordFailure();
        assertEquals(ModelCircuitBreaker.State.OPEN, breaker.getState());
        assertFalse(breaker.allowCall());
    }

    @Test
    void transitionsToHalfOpenAfterResetTimeout() {
        var breaker = new ModelCircuitBreaker("test-model", 2, Duration.ofMillis(50));
        breaker.recordFailure();
        breaker.recordFailure();
        assertEquals(ModelCircuitBreaker.State.OPEN, breaker.getState());

        // Wait for reset timeout to elapse
        sleep(100);

        assertTrue(breaker.allowCall());
        assertEquals(ModelCircuitBreaker.State.HALF_OPEN, breaker.getState());
    }

    @Test
    void halfOpenAllowsProbeCall() {
        var breaker = new ModelCircuitBreaker("test-model", 2, Duration.ofMillis(50));
        breaker.recordFailure();
        breaker.recordFailure();
        sleep(100);

        // First call transitions to HALF_OPEN and is allowed
        assertTrue(breaker.allowCall());
        assertEquals(ModelCircuitBreaker.State.HALF_OPEN, breaker.getState());

        // Additional calls in HALF_OPEN are also allowed
        assertTrue(breaker.allowCall());
    }

    @Test
    void successInHalfOpenTransitionsToClosed() {
        var breaker = new ModelCircuitBreaker("test-model", 2, Duration.ofMillis(50));
        breaker.recordFailure();
        breaker.recordFailure();
        sleep(100);

        breaker.allowCall(); // transitions to HALF_OPEN
        assertEquals(ModelCircuitBreaker.State.HALF_OPEN, breaker.getState());

        breaker.recordSuccess();
        assertEquals(ModelCircuitBreaker.State.CLOSED, breaker.getState());
        assertTrue(breaker.allowCall());
    }

    @Test
    void failureInHalfOpenTransitionsBackToOpen() {
        var breaker = new ModelCircuitBreaker("test-model", 2, Duration.ofMillis(50));
        breaker.recordFailure();
        breaker.recordFailure();
        sleep(100);

        breaker.allowCall(); // transitions to HALF_OPEN
        assertEquals(ModelCircuitBreaker.State.HALF_OPEN, breaker.getState());

        breaker.recordFailure();
        assertEquals(ModelCircuitBreaker.State.OPEN, breaker.getState());
        assertFalse(breaker.allowCall());
    }

    @Test
    void successResetsFailureCount() {
        var breaker = new ModelCircuitBreaker("test-model", 3, Duration.ofSeconds(60));
        breaker.recordFailure();
        breaker.recordFailure();
        // 2 failures, not yet at threshold

        breaker.recordSuccess();
        // Count should be reset, need 3 more failures to open

        breaker.recordFailure();
        breaker.recordFailure();
        assertEquals(ModelCircuitBreaker.State.CLOSED, breaker.getState());

        breaker.recordFailure();
        assertEquals(ModelCircuitBreaker.State.OPEN, breaker.getState());
    }

    @Test
    void customThresholdAndTimeout() {
        var breaker = new ModelCircuitBreaker("custom-model", 10, Duration.ofSeconds(120));
        for (int i = 0; i < 9; i++) {
            breaker.recordFailure();
        }
        assertEquals(ModelCircuitBreaker.State.CLOSED, breaker.getState());

        breaker.recordFailure();
        assertEquals(ModelCircuitBreaker.State.OPEN, breaker.getState());
    }

    @Test
    void modelIdIsPreserved() {
        var breaker = new ModelCircuitBreaker("my-model-id");
        assertEquals("my-model-id", breaker.getModelId());
    }

    @Test
    void concurrentAccessSafety() throws Exception {
        var breaker = new ModelCircuitBreaker("concurrent-model", 100, Duration.ofSeconds(60));
        int threadCount = 10;
        int failuresPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            futures.add(
                    executor.submit(
                            () -> {
                                try {
                                    latch.await();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                for (int i = 0; i < failuresPerThread; i++) {
                                    breaker.recordFailure();
                                }
                            }));
        }

        latch.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        // After 100 concurrent failures with threshold 100, should be OPEN
        assertEquals(ModelCircuitBreaker.State.OPEN, breaker.getState());
    }

    @Test
    void circuitBreakerOpenExceptionContainsModelId() {
        var ex = new CircuitBreakerOpenException("broken-model");
        assertEquals("broken-model", ex.getModelId());
        assertTrue(ex.getMessage().contains("broken-model"));
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
