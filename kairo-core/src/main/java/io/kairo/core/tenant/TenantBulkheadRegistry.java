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

import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that lazily creates and caches a {@link TenantBulkhead} per tenant ID.
 *
 * <p>All tenants not explicitly configured fall back to the {@link #defaultConfig}.
 */
public final class TenantBulkheadRegistry {

    private final TenantBulkheadConfig defaultConfig;
    private final ConcurrentHashMap<String, TenantBulkhead> bulkheads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TenantBulkheadConfig> configs =
            new ConcurrentHashMap<>();

    public TenantBulkheadRegistry(TenantBulkheadConfig defaultConfig) {
        this.defaultConfig = defaultConfig;
    }

    public TenantBulkheadRegistry() {
        this(TenantBulkheadConfig.DEFAULT);
    }

    /** Register a per-tenant override config (must be done before first {@link #get(String)}). */
    public void configure(String tenantId, TenantBulkheadConfig config) {
        configs.put(tenantId, config);
    }

    /** Return the bulkhead for the given tenant, creating it on first access. */
    public TenantBulkhead get(String tenantId) {
        return bulkheads.computeIfAbsent(tenantId, id -> configFor(id).newBulkhead());
    }

    private TenantBulkheadConfig configFor(String tenantId) {
        return configs.getOrDefault(tenantId, defaultConfig);
    }
}
