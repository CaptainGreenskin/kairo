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
package io.kairo.api.tenant;

import io.kairo.api.Stable;

/**
 * Pluggable holder that exposes the {@link TenantContext} bound to the current execution.
 *
 * <p>Default callers should treat the result of {@link #current()} as authoritative for read
 * purposes, and use {@link #bind(TenantContext)} only at boundary code (HTTP filters, message
 * consumers, scheduled job entry points) to propagate identity.
 *
 * <p>Implementations are expected to be safe for concurrent use across threads. The default
 * implementation is thread-local; reactive integrations bridge it onto the Reactor {@code Context}.
 *
 * <p>Bindings nest: invoking {@link #bind(TenantContext)} pushes a new value; closing the returned
 * {@link Scope} restores whatever was active before. Closing out of order is a programming error
 * and may raise an exception.
 *
 * @since v1.1
 */
@Stable(since = "1.1.0", value = "Tenant context propagation seam; default impl in kairo-core.")
public interface TenantContextHolder {

    /**
     * @return the tenant bound to the current execution, or {@link TenantContext#SINGLE} when
     *     nothing is bound. Never {@code null}.
     */
    TenantContext current();

    /**
     * Bind a tenant for the current execution. The previous binding (if any) is restored when the
     * returned {@link Scope} is closed.
     *
     * <p>Callers should always use try-with-resources:
     *
     * <pre>{@code
     * try (TenantContextHolder.Scope ignored = holder.bind(ctx)) {
     *     // tenant-aware work
     * }
     * }</pre>
     *
     * @param ctx the tenant to bind; must not be {@code null}.
     * @return a scope handle that pops the binding on close.
     */
    Scope bind(TenantContext ctx);

    /** AutoCloseable scope marker; {@link #close()} never throws checked exceptions. */
    interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * Sentinel holder that always reports {@link TenantContext#SINGLE}. Useful where a holder is
     * required structurally but the deployment has no tenant story (single-tenant default).
     *
     * <p>{@link #bind(TenantContext)} is a silent no-op: the binding is dropped and {@link
     * #current()} continues to return {@link TenantContext#SINGLE}. This is intentional — opting
     * into {@code NOOP} means "I don't track tenants here".
     */
    TenantContextHolder NOOP =
            new TenantContextHolder() {
                @Override
                public TenantContext current() {
                    return TenantContext.SINGLE;
                }

                @Override
                public Scope bind(TenantContext ctx) {
                    return () -> {};
                }
            };
}
