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

import io.kairo.api.tenant.TenantContext;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry that maps each tenant to its {@link TenantBulkhead}.
 *
 * <p>Bulkheads are created lazily on first access and cached for the lifetime of the registry. The
 * registry is thread-safe; concurrent calls for the same tenantId are safe.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * registry.get(tenantContext).execute(() -> callExpensiveService());
 * }</pre>
 */
public final class TenantBulkheadRegistry {

    private final TenantBulkheadConfig config;
    private final ConcurrentHashMap<String, TenantBulkhead> bulkheads = new ConcurrentHashMap<>();

    public TenantBulkheadRegistry(TenantBulkheadConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /** Convenience constructor with default configuration (FREE limits for all tenants). */
    public TenantBulkheadRegistry() {
        this(TenantBulkheadConfig.defaults());
    }

    /**
     * Returns (or creates) the bulkhead for the given tenant. The bulkhead limits are resolved from
     * {@link TenantBulkheadConfig} on first creation and then cached.
     */
    public TenantBulkhead get(TenantContext tenant) {
        Objects.requireNonNull(tenant, "tenant");
        return bulkheads.computeIfAbsent(
                tenant.tenantId(), id -> new TenantBulkhead(id, config.limitsFor(tenant)));
    }

    /** Returns the number of tenant bulkheads currently registered. */
    public int size() {
        return bulkheads.size();
    }

    /**
     * Removes all cached bulkhead instances. Useful for testing or dynamic reconfiguration.
     * In-flight operations on removed bulkheads are not interrupted.
     */
    public void clear() {
        bulkheads.clear();
    }
}
