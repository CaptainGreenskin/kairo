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
package io.kairo.core.middleware;

import io.kairo.api.middleware.Middleware;
import io.kairo.api.middleware.MiddlewareChain;
import io.kairo.api.middleware.MiddlewareContext;
import io.kairo.api.middleware.MiddlewareRejectException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import reactor.core.publisher.Mono;

/**
 * Token-bucket rate-limiting middleware.
 *
 * <p>Limits agent invocations to {@code requestsPerSecond} sustained throughput with a {@code
 * burstCapacity} token buffer. Each call consumes one token; tokens refill at the configured rate.
 * When the bucket is empty and {@code blockOnEmpty} is false, the request is rejected immediately;
 * when true, the caller waits up to {@code maxWait} for a token.
 */
public class RateLimitMiddleware implements Middleware {

    private final double requestsPerSecond;
    private final long burstCapacity;
    private final boolean blockOnEmpty;
    private final Duration maxWait;

    private final AtomicLong availableTokens;
    private volatile long lastRefillNanos;

    /**
     * @param requestsPerSecond sustained refill rate
     * @param burstCapacity maximum tokens that can accumulate
     * @param blockOnEmpty when true, wait for a token instead of rejecting
     * @param maxWait maximum wait time when {@code blockOnEmpty} is true
     */
    public RateLimitMiddleware(
            double requestsPerSecond, long burstCapacity, boolean blockOnEmpty, Duration maxWait) {
        if (requestsPerSecond <= 0)
            throw new IllegalArgumentException("requestsPerSecond must be > 0");
        if (burstCapacity < 1) throw new IllegalArgumentException("burstCapacity must be >= 1");
        this.requestsPerSecond = requestsPerSecond;
        this.burstCapacity = burstCapacity;
        this.blockOnEmpty = blockOnEmpty;
        this.maxWait = maxWait;
        this.availableTokens = new AtomicLong(burstCapacity);
        this.lastRefillNanos = System.nanoTime();
    }

    /** Convenience constructor: reject immediately when rate exceeded. */
    public RateLimitMiddleware(double requestsPerSecond, long burstCapacity) {
        this(requestsPerSecond, burstCapacity, false, Duration.ZERO);
    }

    @Override
    public String name() {
        return "rate-limiter";
    }

    @Override
    public Mono<MiddlewareContext> handle(MiddlewareContext ctx, MiddlewareChain chain) {
        return Mono.defer(
                () -> {
                    if (tryAcquire()) {
                        return chain.next(ctx);
                    }
                    if (!blockOnEmpty) {
                        return Mono.error(
                                new MiddlewareRejectException(
                                        name(), "Rate limit exceeded — retry later"));
                    }
                    return waitForToken(maxWait)
                            .flatMap(
                                    acquired -> {
                                        if (!acquired) {
                                            return Mono.error(
                                                    new MiddlewareRejectException(
                                                            name(),
                                                            "Rate limit wait timed out after "
                                                                    + maxWait));
                                        }
                                        return chain.next(ctx);
                                    });
                });
    }

    /** Returns true if a token was successfully acquired. */
    boolean tryAcquire() {
        refill();
        while (true) {
            long current = availableTokens.get();
            if (current <= 0) return false;
            if (availableTokens.compareAndSet(current, current - 1)) return true;
        }
    }

    /** Polls for an available token up to {@code maxWait}, sleeping 1 ms between attempts. */
    private Mono<Boolean> waitForToken(Duration maxWait) {
        return Mono.fromCallable(
                () -> {
                    long deadlineNanos = System.nanoTime() + maxWait.toNanos();
                    while (System.nanoTime() < deadlineNanos) {
                        if (tryAcquire()) return true;
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                    }
                    return false;
                });
    }

    private synchronized void refill() {
        long nowNanos = System.nanoTime();
        long elapsedNanos = nowNanos - lastRefillNanos;
        long tokensToAdd = (long) (elapsedNanos * requestsPerSecond / 1_000_000_000.0);
        if (tokensToAdd > 0) {
            long updated = Math.min(burstCapacity, availableTokens.get() + tokensToAdd);
            availableTokens.set(updated);
            lastRefillNanos = nowNanos;
        }
    }

    /** Returns the number of currently available tokens (for testing/monitoring). */
    public long availableTokens() {
        refill();
        return availableTokens.get();
    }
}
