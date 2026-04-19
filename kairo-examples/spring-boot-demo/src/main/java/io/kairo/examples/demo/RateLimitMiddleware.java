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
package io.kairo.examples.demo;

import io.kairo.api.middleware.Middleware;
import io.kairo.api.middleware.MiddlewareChain;
import io.kairo.api.middleware.MiddlewareContext;
import io.kairo.api.middleware.MiddlewareOrder;
import io.kairo.api.middleware.MiddlewareRejectException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import reactor.core.publisher.Mono;

/**
 * Example middleware that enforces a simple rate limit per session.
 *
 * <p>Runs after {@link AuthMiddleware} (via {@link MiddlewareOrder#after()}) so that
 * unauthenticated requests are rejected before consuming rate-limit quota.
 *
 * <pre>{@code
 * // Register as a Spring bean
 * @Bean
 * public Middleware rateLimitMiddleware() {
 *     return new RateLimitMiddleware(10); // 10 requests per session
 * }
 * }</pre>
 */
@MiddlewareOrder(after = {"auth"})
public class RateLimitMiddleware implements Middleware {

    private final long maxRequests;
    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    /** Create a rate limiter allowing {@code maxRequests} per session. */
    public RateLimitMiddleware(long maxRequests) {
        this.maxRequests = maxRequests;
    }

    @Override
    public String name() {
        return "rate-limiter";
    }

    @Override
    public Mono<MiddlewareContext> handle(MiddlewareContext context, MiddlewareChain chain) {
        String sessionId = context.sessionId();
        if (sessionId == null) {
            // No session — skip rate limiting
            return chain.next(context);
        }

        AtomicLong counter = counters.computeIfAbsent(sessionId, k -> new AtomicLong(0));
        long count = counter.incrementAndGet();

        if (count > maxRequests) {
            return Mono.error(new MiddlewareRejectException(
                    "rate-limiter", "Rate limit exceeded for session " + sessionId));
        }

        return chain.next(context.withAttribute("requestCount", count));
    }

    /** Reset all counters (for testing). */
    public void reset() {
        counters.clear();
    }
}
