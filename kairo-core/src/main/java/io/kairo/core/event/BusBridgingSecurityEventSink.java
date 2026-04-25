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
package io.kairo.core.event;

import io.kairo.api.event.KairoEvent;
import io.kairo.api.event.KairoEventBus;
import io.kairo.api.guardrail.SecurityEvent;
import io.kairo.api.guardrail.SecurityEventSink;
import io.kairo.api.tenant.TenantContext;
import io.kairo.api.tenant.TenantContextHolder;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Adapter that forwards a {@link SecurityEvent} to both an underlying {@link SecurityEventSink} and
 * the {@link KairoEventBus}.
 *
 * <p>Use this to preserve the existing logging/audit contract while making every security event
 * observable through the unified event bus (OTel exporter, metrics, correlation).
 *
 * <p>When a {@link TenantContextHolder} is configured, security events are stamped with {@link
 * TenantContext#ATTR_TENANT_ID} / {@link TenantContext#ATTR_PRINCIPAL_ID} BEFORE the delegate sink
 * is invoked, so JDBC/audit-log delegates see tenant attribution end-to-end. Caller-supplied keys
 * are preserved (call-site override always wins).
 *
 * @since v0.10
 */
public class BusBridgingSecurityEventSink implements SecurityEventSink {

    private final SecurityEventSink delegate;
    private final KairoEventBus bus;
    private final TenantContextHolder tenantHolder;

    public BusBridgingSecurityEventSink(SecurityEventSink delegate, KairoEventBus bus) {
        this(delegate, bus, TenantContextHolder.NOOP);
    }

    public BusBridgingSecurityEventSink(
            SecurityEventSink delegate, KairoEventBus bus, TenantContextHolder tenantHolder) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.bus = Objects.requireNonNull(bus, "bus must not be null");
        this.tenantHolder = Objects.requireNonNull(tenantHolder, "tenantHolder must not be null");
    }

    @Override
    public void record(SecurityEvent event) {
        if (event == null) {
            return;
        }
        SecurityEvent stamped = stampTenant(event);
        delegate.record(stamped);
        bus.publish(toBusEvent(stamped));
    }

    private SecurityEvent stampTenant(SecurityEvent event) {
        Map<String, Object> existing = event.attributes() == null ? Map.of() : event.attributes();
        boolean hasTenant = existing.containsKey(TenantContext.ATTR_TENANT_ID);
        boolean hasPrincipal = existing.containsKey(TenantContext.ATTR_PRINCIPAL_ID);
        if (hasTenant && hasPrincipal) {
            return event;
        }
        TenantContext ctx = tenantHolder.current();
        // Preserve v0.10 contract for delegates: do NOT mutate SecurityEvent.attributes when no
        // real tenant is bound. The bus path still enriches its KairoEvent envelope so OTel and
        // other downstream consumers always see a tenant key, but JDBC/log delegates stay clean.
        if (ctx == TenantContext.SINGLE) {
            return event;
        }
        Map<String, Object> merged = new HashMap<>(existing);
        if (!hasTenant) {
            merged.put(TenantContext.ATTR_TENANT_ID, ctx.tenantId());
        }
        if (!hasPrincipal) {
            merged.put(TenantContext.ATTR_PRINCIPAL_ID, ctx.principalId());
        }
        return new SecurityEvent(
                event.timestamp(),
                event.type(),
                event.agentName(),
                event.targetName(),
                event.phase(),
                event.policyName(),
                event.reason(),
                merged);
    }

    private static KairoEvent toBusEvent(SecurityEvent event) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("agentName", event.agentName());
        attributes.put("targetName", event.targetName());
        attributes.put("phase", event.phase().name());
        attributes.put("policyName", event.policyName());
        if (event.reason() != null && !event.reason().isBlank()) {
            attributes.put("reason", event.reason());
        }
        if (event.attributes() != null) {
            attributes.putAll(event.attributes());
        }
        return new KairoEvent(
                java.util.UUID.randomUUID().toString(),
                event.timestamp(),
                KairoEvent.DOMAIN_SECURITY,
                event.type().name(),
                event,
                attributes);
    }
}
