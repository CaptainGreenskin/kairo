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
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * Middleware that enforces per-tenant concurrency and rate limits via {@link
 * TenantBulkheadRegistry}.
 *
 * <p>Rate check is performed first (cheap), then concurrency slot acquisition. Slots are always
 * released in {@code doFinally} regardless of whether the downstream chain succeeded or failed.
 */
public final class BulkheadMiddleware implements Middleware {

    private final TenantBulkheadRegistry registry;
    private final TenantContextHolder tenantContextHolder;

    public BulkheadMiddleware(
            TenantBulkheadRegistry registry, TenantContextHolder tenantContextHolder) {
        this.registry = registry;
        this.tenantContextHolder = tenantContextHolder;
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
                    TenantBulkhead bulkhead = registry.get(tenant.tenantId());

                    if (!bulkhead.tryAcquireRate()) {
                        return Mono.error(
                                new MiddlewareRejectException(
                                        name(),
                                        "Rate limit exceeded for tenant: " + tenant.tenantId()));
                    }
                    if (!bulkhead.tryAcquireConcurrency()) {
                        return Mono.error(
                                new MiddlewareRejectException(
                                        name(),
                                        "Concurrency limit exceeded for tenant: "
                                                + tenant.tenantId()));
                    }
                    return chain.next(ctx)
                            .doFinally(
                                    signal -> {
                                        if (signal != SignalType.CANCEL) {
                                            bulkhead.releaseConcurrency();
                                        } else {
                                            bulkhead.releaseConcurrency();
                                        }
                                    });
                });
    }

    private TenantContext resolveTenant(MiddlewareContext ctx) {
        Object attr = ctx.attributes().get("tenant");
        if (attr instanceof TenantContext tc) return tc;
        return tenantContextHolder.current();
    }
}
