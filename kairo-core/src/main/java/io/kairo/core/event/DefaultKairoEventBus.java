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
import io.kairo.api.tenant.TenantContext;
import io.kairo.api.tenant.TenantContextHolder;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Default in-process implementation of {@link KairoEventBus} backed by a multicast Reactor sink.
 *
 * <p>Uses {@code Sinks.many().multicast().onBackpressureBuffer()} so multiple concurrent
 * subscribers (OTel exporter, metrics, custom audit sink) can receive every event without blocking
 * the publisher. Events published when no subscribers are connected are dropped; this is
 * intentional — subscribers must attach before publishers start emitting.
 *
 * <p>The bus is also the single enrichment seam for {@link TenantContext} propagation: every
 * envelope is decorated with {@link TenantContext#ATTR_TENANT_ID} and {@link
 * TenantContext#ATTR_PRINCIPAL_ID} attributes drawn from the configured {@link
 * TenantContextHolder}, unless the publisher already populated those keys (call-site override
 * always wins). When no holder is configured the bus falls back to {@link TenantContextHolder#NOOP}
 * and tenants are reported as {@link TenantContext#SINGLE}, preserving v0.10 behavior bit-for-bit
 * for single-tenant deployments.
 *
 * @since v0.10 (Experimental)
 */
public class DefaultKairoEventBus implements KairoEventBus {

    private static final Logger log = LoggerFactory.getLogger(DefaultKairoEventBus.class);

    private final Sinks.Many<KairoEvent> sink;
    private final TenantContextHolder tenantHolder;

    public DefaultKairoEventBus() {
        this(TenantContextHolder.NOOP);
    }

    public DefaultKairoEventBus(TenantContextHolder tenantHolder) {
        this.sink = Sinks.many().multicast().onBackpressureBuffer();
        this.tenantHolder = Objects.requireNonNull(tenantHolder, "tenantHolder");
    }

    @Override
    public void publish(KairoEvent event) {
        if (event == null) {
            return;
        }
        KairoEvent enriched = enrichWithTenant(event);
        Sinks.EmitResult result = sink.tryEmitNext(enriched);
        if (result.isFailure()) {
            log.debug(
                    "KairoEventBus dropped event (domain={}, type={}, result={})",
                    enriched.domain(),
                    enriched.eventType(),
                    result);
        }
    }

    @Override
    public Flux<KairoEvent> subscribe() {
        return sink.asFlux();
    }

    @Override
    public Flux<KairoEvent> subscribe(String domain) {
        if (domain == null || domain.isBlank()) {
            return subscribe();
        }
        return sink.asFlux().filter(event -> domain.equals(event.domain()));
    }

    private KairoEvent enrichWithTenant(KairoEvent event) {
        Map<String, Object> existing = event.attributes();
        boolean hasTenant = existing.containsKey(TenantContext.ATTR_TENANT_ID);
        boolean hasPrincipal = existing.containsKey(TenantContext.ATTR_PRINCIPAL_ID);
        if (hasTenant && hasPrincipal) {
            return event;
        }
        TenantContext ctx = tenantHolder.current();
        Map<String, Object> merged = new HashMap<>(existing);
        if (!hasTenant) {
            merged.put(TenantContext.ATTR_TENANT_ID, ctx.tenantId());
        }
        if (!hasPrincipal) {
            merged.put(TenantContext.ATTR_PRINCIPAL_ID, ctx.principalId());
        }
        return new KairoEvent(
                event.eventId(),
                event.timestamp(),
                event.domain(),
                event.eventType(),
                event.payload(),
                merged);
    }
}
