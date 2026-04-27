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
import java.util.Map;
import java.util.Objects;

/**
 * Resolves {@link TierBulkheadLimits} for a given {@link TenantContext}.
 *
 * <p>Resolution order:
 *
 * <ol>
 *   <li>Per-tenant override from {@link #tenantOverrides} map
 *   <li>{@code plan-tier} attribute on the context (free / pro / enterprise / unlimited)
 *   <li>{@link #defaultLimits}
 * </ol>
 */
public final class TenantBulkheadConfig {

    private final TierBulkheadLimits defaultLimits;
    private final Map<String, TierBulkheadLimits> tenantOverrides;

    public TenantBulkheadConfig(
            TierBulkheadLimits defaultLimits, Map<String, TierBulkheadLimits> tenantOverrides) {
        this.defaultLimits = Objects.requireNonNull(defaultLimits);
        this.tenantOverrides = Map.copyOf(tenantOverrides);
    }

    /** Default config: FREE limits for all tenants, no per-tenant overrides. */
    public static TenantBulkheadConfig defaults() {
        return new TenantBulkheadConfig(TierBulkheadLimits.FREE, Map.of());
    }

    /** Unlimited config: no bulkhead enforced. Use for single-tenant / dev environments. */
    public static TenantBulkheadConfig unlimited() {
        return new TenantBulkheadConfig(TierBulkheadLimits.UNLIMITED, Map.of());
    }

    /** Resolve the effective limits for the given tenant. */
    public TierBulkheadLimits limitsFor(TenantContext tenant) {
        // 1. per-tenant override
        TierBulkheadLimits override = tenantOverrides.get(tenant.tenantId());
        if (override != null) return override;

        // 2. plan-tier attribute
        String tier = tenant.attributes().get("plan-tier");
        if (tier != null) return TierBulkheadLimits.forTierName(tier);

        // 3. default
        return defaultLimits;
    }
}
