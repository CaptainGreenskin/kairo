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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.kairo.api.event.KairoEvent;
import io.kairo.api.tenant.TenantContext;
import io.kairo.api.tenant.TenantContextHolder;
import io.kairo.core.tenant.ThreadLocalTenantContextHolder;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

class DefaultKairoEventBusTest {

    @Test
    void deliversEventsToAllSubscribers() {
        DefaultKairoEventBus bus = new DefaultKairoEventBus();
        AtomicInteger sub1 = new AtomicInteger();
        AtomicInteger sub2 = new AtomicInteger();

        Disposable d1 = bus.subscribe().subscribe(e -> sub1.incrementAndGet());
        Disposable d2 = bus.subscribe().subscribe(e -> sub2.incrementAndGet());

        bus.publish(KairoEvent.of(KairoEvent.DOMAIN_EXECUTION, "A", Map.of()));
        bus.publish(KairoEvent.of(KairoEvent.DOMAIN_EXECUTION, "B", Map.of()));

        waitUntil(() -> sub1.get() == 2 && sub2.get() == 2);

        assertEquals(2, sub1.get());
        assertEquals(2, sub2.get());

        d1.dispose();
        d2.dispose();
    }

    @Test
    void domainFilterOnlyDeliversMatchingEvents() {
        DefaultKairoEventBus bus = new DefaultKairoEventBus();
        AtomicInteger execution = new AtomicInteger();

        Disposable sub =
                bus.subscribe(KairoEvent.DOMAIN_EXECUTION)
                        .subscribe(e -> execution.incrementAndGet());

        bus.publish(KairoEvent.of(KairoEvent.DOMAIN_EVOLUTION, "X", Map.of()));
        bus.publish(KairoEvent.of(KairoEvent.DOMAIN_EXECUTION, "Y", Map.of()));

        waitUntil(() -> execution.get() == 1);

        assertEquals(1, execution.get());
        sub.dispose();
    }

    @Test
    void noopHolderEnrichesEventsWithSingleTenantSentinel() {
        DefaultKairoEventBus bus = new DefaultKairoEventBus();
        AtomicReference<KairoEvent> received = new AtomicReference<>();

        Disposable sub = bus.subscribe().subscribe(received::set);

        bus.publish(KairoEvent.of(KairoEvent.DOMAIN_EXECUTION, "tick", Map.of()));
        waitUntil(() -> received.get() != null);

        KairoEvent ev = received.get();
        assertNotNull(ev);
        assertEquals("default", ev.attributes().get(TenantContext.ATTR_TENANT_ID));
        assertEquals("anonymous", ev.attributes().get(TenantContext.ATTR_PRINCIPAL_ID));
        sub.dispose();
    }

    @Test
    void enrichesEventWithCurrentTenantWhenHolderIsBound() {
        ThreadLocalTenantContextHolder holder = new ThreadLocalTenantContextHolder();
        DefaultKairoEventBus bus = new DefaultKairoEventBus(holder);
        AtomicReference<KairoEvent> received = new AtomicReference<>();
        Disposable sub = bus.subscribe().subscribe(received::set);

        TenantContext acme = new TenantContext("acme", "alice", Map.of("region", "us-west-2"));
        try (TenantContextHolder.Scope ignored = holder.bind(acme)) {
            bus.publish(KairoEvent.of(KairoEvent.DOMAIN_EXECUTION, "tick", Map.of("k", "v")));
        }

        waitUntil(() -> received.get() != null);
        KairoEvent ev = received.get();
        assertEquals("acme", ev.attributes().get(TenantContext.ATTR_TENANT_ID));
        assertEquals("alice", ev.attributes().get(TenantContext.ATTR_PRINCIPAL_ID));
        // original payload survives merge
        assertEquals("v", ev.attributes().get("k"));
        sub.dispose();
    }

    @Test
    void publisherSuppliedTenantAttributesAreNotOverwritten() {
        ThreadLocalTenantContextHolder holder = new ThreadLocalTenantContextHolder();
        DefaultKairoEventBus bus = new DefaultKairoEventBus(holder);
        AtomicReference<KairoEvent> received = new AtomicReference<>();
        Disposable sub = bus.subscribe().subscribe(received::set);

        try (TenantContextHolder.Scope ignored =
                holder.bind(new TenantContext("acme", "alice", Map.of()))) {
            // Publisher already populated tenant.id -> bus must not overwrite it.
            bus.publish(
                    KairoEvent.of(
                            KairoEvent.DOMAIN_SECURITY,
                            "guardrail",
                            Map.of(
                                    TenantContext.ATTR_TENANT_ID, "explicit",
                                    TenantContext.ATTR_PRINCIPAL_ID, "explicit-user")));
        }

        waitUntil(() -> received.get() != null);
        KairoEvent ev = received.get();
        assertEquals("explicit", ev.attributes().get(TenantContext.ATTR_TENANT_ID));
        assertEquals("explicit-user", ev.attributes().get(TenantContext.ATTR_PRINCIPAL_ID));
        sub.dispose();
    }

    @Test
    void preservesEventIdentityFieldsAcrossEnrichment() {
        ThreadLocalTenantContextHolder holder = new ThreadLocalTenantContextHolder();
        DefaultKairoEventBus bus = new DefaultKairoEventBus(holder);
        AtomicReference<KairoEvent> received = new AtomicReference<>();
        Disposable sub = bus.subscribe().subscribe(received::set);

        KairoEvent original = KairoEvent.of(KairoEvent.DOMAIN_EXECUTION, "tick", Map.of());
        bus.publish(original);
        waitUntil(() -> received.get() != null);

        KairoEvent enriched = received.get();
        assertEquals(original.eventId(), enriched.eventId());
        assertEquals(original.timestamp(), enriched.timestamp());
        assertEquals(original.domain(), enriched.domain());
        assertEquals(original.eventType(), enriched.eventType());
        assertNull(enriched.payload());
        sub.dispose();
    }

    private static void waitUntil(java.util.function.BooleanSupplier predicate) {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (predicate.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
