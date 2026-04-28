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
import io.kairo.api.tenant.TenantContext;
import io.kairo.api.tenant.TenantContextHolder;
import io.kairo.core.tenant.TenantBulkhead;
import io.kairo.core.tenant.TenantBulkheadRegistry;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Middleware that enforces per-tenant concurrency and rate limits via {@link
 * TenantBulkheadRegistry}.
 *
 * <p>The tenant is resolved in priority order:
 *
 * <ol>
 *   <li>{@code "tenant"} attribute in the {@link MiddlewareContext} (set by auth middleware)
 *   <li>{@link TenantContextHolder#current()} on the injected holder (current thread)
 *   <li>{@link TenantContext#SINGLE} (fallback — returned by holder when no binding exists)
 * </ol>
 *
 * <p>When a limit is exceeded, the chain is terminated with a {@link MiddlewareRejectException}.
 * The concurrency slot is released in a {@code doFinally} handler, so it is always freed regardless
 * of success, error, or cancellation.
 */
public class BulkheadMiddleware implements Middleware {

    private final TenantBulkheadRegistry registry;
    private final TenantContextHolder tenantContextHolder;

    public BulkheadMiddleware(
            TenantBulkheadRegistry registry, TenantContextHolder tenantContextHolder) {
        this.registry = Objects.requireNonNull(registry);
        this.tenantContextHolder = Objects.requireNonNull(tenantContextHolder);
    }

    @Override
    public String name() {
        return "bulkhead";
    }

    @Override
    public Mono<MiddlewareContext> handle(MiddlewareContext ctx, MiddlewareChain chain) {
        return Mono.defer(
                () -> {
                    TenantContext tenant = resolveTenant(ctx);
                    TenantBulkhead bulkhead = registry.get(tenant);

                    // 1. Rate limit check
                    if (!bulkhead.tryAcquireRate()) {
                        return Mono.error(
                                new MiddlewareRejectException(
                                        name(),
                                        "Rate limit exceeded for tenant '"
                                                + tenant.tenantId()
                                                + "'"));
                    }

                    // 2. Concurrency limit check
                    if (!bulkhead.tryAcquireConcurrency()) {
                        return Mono.error(
                                new MiddlewareRejectException(
                                        name(),
                                        "Concurrency limit exceeded for tenant '"
                                                + tenant.tenantId()
                                                + "'"));
                    }

                    // 3. Pass through; release concurrency slot on any terminal signal
                    return chain.next(ctx).doFinally(signal -> bulkhead.releaseConcurrency());
                });
    }

    private TenantContext resolveTenant(MiddlewareContext ctx) {
        Object attr = ctx.attributes().get("tenant");
        if (attr instanceof TenantContext tc) return tc;
        return tenantContextHolder.current();
    }
}
