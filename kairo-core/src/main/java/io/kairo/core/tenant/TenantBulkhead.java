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
package io.kairo.core.tenant;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-tenant bulkhead: concurrency slot limiter + token-bucket rate limiter.
 *
 * <p>Call {@link #tryAcquireConcurrency()} before the operation; on completion (success or error)
 * call {@link #releaseConcurrency()}. Call {@link #tryAcquireRate()} to check the rate bucket
 * first.
 */
public final class TenantBulkhead {

    private final Semaphore concurrencySlots;
    private final double requestsPerSecond;
    private final long burstCapacity;

    private final AtomicLong availableTokens;
    private volatile long lastRefillNanos;

    public TenantBulkhead(int maxConcurrency, double requestsPerSecond, long burstCapacity) {
        if (maxConcurrency < 1) throw new IllegalArgumentException("maxConcurrency must be >= 1");
        if (requestsPerSecond <= 0)
            throw new IllegalArgumentException("requestsPerSecond must be > 0");
        if (burstCapacity < 1) throw new IllegalArgumentException("burstCapacity must be >= 1");
        this.concurrencySlots = new Semaphore(maxConcurrency);
        this.requestsPerSecond = requestsPerSecond;
        this.burstCapacity = burstCapacity;
        this.availableTokens = new AtomicLong(burstCapacity);
        this.lastRefillNanos = System.nanoTime();
    }

    public boolean tryAcquireConcurrency() {
        return concurrencySlots.tryAcquire();
    }

    public void releaseConcurrency() {
        concurrencySlots.release();
    }

    public boolean tryAcquireRate() {
        refillTokens();
        while (true) {
            long current = availableTokens.get();
            if (current <= 0) return false;
            if (availableTokens.compareAndSet(current, current - 1)) return true;
        }
    }

    private synchronized void refillTokens() {
        long now = System.nanoTime();
        long elapsed = now - lastRefillNanos;
        if (elapsed <= 0) return;
        long tokensToAdd = (long) (elapsed * requestsPerSecond / 1_000_000_000.0);
        if (tokensToAdd <= 0) return;
        lastRefillNanos = now;
        long current = availableTokens.get();
        availableTokens.set(Math.min(burstCapacity, current + tokensToAdd));
    }
}
