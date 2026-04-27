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
import java.util.function.Supplier;

/**
 * Per-tenant isolation unit: a concurrency semaphore paired with a token-bucket rate limiter.
 *
 * <p>Calling {@link #execute} enforces both limits atomically:
 *
 * <ol>
 *   <li>Rate check (token bucket) — rejects immediately if the rate window is full.
 *   <li>Concurrency acquire (semaphore) — rejects immediately if all slots are taken.
 * </ol>
 */
public final class TenantBulkhead {

    private final String tenantId;
    private final Semaphore semaphore;

    // Token-bucket state
    private final double ratePerSecond;
    private final long burstCapacity;
    private final AtomicLong availableTokens;
    private volatile long lastRefillNanos;

    public TenantBulkhead(String tenantId, TierBulkheadLimits limits) {
        this.tenantId = tenantId;
        this.semaphore =
                limits.maxConcurrent() == Integer.MAX_VALUE
                        ? null
                        : new Semaphore(limits.maxConcurrent(), true);
        this.ratePerSecond = limits.ratePerSecond();
        this.burstCapacity = limits.burstCapacity();
        this.availableTokens = new AtomicLong(limits.burstCapacity());
        this.lastRefillNanos = System.nanoTime();
    }

    /**
     * Executes the supplier under the bulkhead constraints. Rejects immediately if rate or
     * concurrency limits are exceeded.
     *
     * @throws BulkheadRejectedException if rate or concurrency is exceeded
     */
    public <T> T execute(Supplier<T> action) {
        if (!tryAcquireRate()) {
            throw new BulkheadRejectedException(tenantId, "rate limit exceeded");
        }
        if (semaphore != null && !semaphore.tryAcquire()) {
            throw new BulkheadRejectedException(tenantId, "concurrency limit exceeded");
        }
        try {
            return action.get();
        } finally {
            if (semaphore != null) semaphore.release();
        }
    }

    /** Run a Runnable under bulkhead constraints. */
    public void run(Runnable action) {
        execute(
                () -> {
                    action.run();
                    return null;
                });
    }

    public boolean tryAcquireRate() {
        if (ratePerSecond >= Double.MAX_VALUE) return true; // unlimited
        refillTokens();
        while (true) {
            long current = availableTokens.get();
            if (current <= 0) return false;
            if (availableTokens.compareAndSet(current, current - 1)) return true;
        }
    }

    private synchronized void refillTokens() {
        long nowNanos = System.nanoTime();
        long elapsedNanos = nowNanos - lastRefillNanos;
        long toAdd = (long) (elapsedNanos * ratePerSecond / 1_000_000_000.0);
        if (toAdd > 0) {
            long updated = Math.min(burstCapacity, availableTokens.get() + toAdd);
            availableTokens.set(updated);
            lastRefillNanos = nowNanos;
        }
    }

    /**
     * Try to acquire a concurrency slot without blocking. Returns {@code true} if the slot was
     * acquired and must be released via {@link #releaseConcurrency()}.
     */
    public boolean tryAcquireConcurrency() {
        return semaphore == null || semaphore.tryAcquire();
    }

    /** Release a concurrency slot previously acquired by {@link #tryAcquireConcurrency()}. */
    public void releaseConcurrency() {
        if (semaphore != null) semaphore.release();
    }

    /**
     * Returns the number of available concurrency slots (or {@link Integer#MAX_VALUE} if
     * unlimited).
     */
    public int availableConcurrency() {
        return semaphore == null ? Integer.MAX_VALUE : semaphore.availablePermits();
    }

    /** Returns the number of available rate tokens. */
    public long availableRateTokens() {
        refillTokens();
        return availableTokens.get();
    }

    public String tenantId() {
        return tenantId;
    }
}
