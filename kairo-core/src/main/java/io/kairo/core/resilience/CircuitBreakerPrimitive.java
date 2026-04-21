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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/** Minimal thread-safe circuit-breaker primitive shared by core runtime components. */
public final class CircuitBreakerPrimitive {

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final int failureThreshold;
    private final Duration resetTimeout;
    private final AtomicInteger failureCount = new AtomicInteger(0);

    private volatile State state = State.CLOSED;
    private volatile long lastFailureTime = 0L;

    public CircuitBreakerPrimitive(int failureThreshold, Duration resetTimeout) {
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("failureThreshold must be > 0");
        }
        this.failureThreshold = failureThreshold;
        this.resetTimeout =
                resetTimeout != null && !resetTimeout.isNegative() ? resetTimeout : Duration.ZERO;
    }

    public synchronized boolean allowCall() {
        if (state == State.CLOSED) {
            return true;
        }
        if (state == State.OPEN) {
            long elapsed = System.currentTimeMillis() - lastFailureTime;
            if (elapsed >= resetTimeout.toMillis()) {
                state = State.HALF_OPEN;
                return true;
            }
            return false;
        }
        return true;
    }

    public synchronized void recordSuccess() {
        failureCount.set(0);
        state = State.CLOSED;
    }

    public synchronized void recordFailure() {
        lastFailureTime = System.currentTimeMillis();
        if (state == State.HALF_OPEN) {
            state = State.OPEN;
            return;
        }
        int count = failureCount.incrementAndGet();
        if (count >= failureThreshold) {
            state = State.OPEN;
        }
    }

    public synchronized void reset() {
        failureCount.set(0);
        state = State.CLOSED;
    }

    public State state() {
        return state;
    }

    public boolean isOpen() {
        return state == State.OPEN;
    }

    public int failureThreshold() {
        return failureThreshold;
    }
}
