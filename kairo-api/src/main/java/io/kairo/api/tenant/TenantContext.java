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
import java.util.Map;
import java.util.Objects;

/**
 * Identity envelope for cross-cutting concerns (audit / quota / log / metric / cost) that need to
 * aggregate behavior by tenant.
 *
 * <p>{@code TenantContext} is intentionally minimal: it is read by passive sinks (event bus, OTel
 * exporter, security event sink) and never required by call sites. When nothing binds a tenant the
 * implicit value is {@link #SINGLE}, so existing single-tenant deployments observe no behavior
 * change.
 *
 * <p>Quota enforcement, physical isolation, and cross-tenant routing are explicitly out of scope
 * for v1.1. Those land in v1.2 (see {@code .plans/V1.2-DISTRIBUTED.md} D1).
 *
 * @param tenantId stable identifier for the tenant (organization / workspace / project). Must be
 *     non-blank.
 * @param principalId end-user or service-account identifier within the tenant. Must be non-blank;
 *     use {@code "anonymous"} for unauthenticated flows.
 * @param attributes free-form labels (e.g., {@code region}, {@code plan-tier}, {@code org-id}).
 *     Always non-null; defensively copied to an immutable view at construction time.
 * @since v1.1
 */
@Stable(since = "1.1.0", value = "Multi-tenant identity envelope; passive consumption only.")
public record TenantContext(String tenantId, String principalId, Map<String, String> attributes) {

    /**
     * Implicit context observed when no tenant has been bound. Backward-compatible default for
     * single-tenant deployments.
     */
    public static final TenantContext SINGLE = new TenantContext("default", "anonymous", Map.of());

    /**
     * Canonical attribute key for the tenant id when a {@code TenantContext} is projected onto an
     * event/log/span attribute map. Sinks (OTel exporter, audit log) recognize this key to emit a
     * normalized {@code kairo.tenant.id} attribute.
     */
    public static final String ATTR_TENANT_ID = "tenant.id";

    /**
     * Canonical attribute key for the principal id when a {@code TenantContext} is projected onto
     * an event/log/span attribute map.
     */
    public static final String ATTR_PRINCIPAL_ID = "tenant.principal";

    public TenantContext {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(principalId, "principalId");
        Objects.requireNonNull(attributes, "attributes");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (principalId.isBlank()) {
            throw new IllegalArgumentException("principalId must not be blank");
        }
        attributes = Map.copyOf(attributes);
    }

    /** Convenience constructor for tenants without extra attributes. */
    public TenantContext(String tenantId, String principalId) {
        this(tenantId, principalId, Map.of());
    }
}
