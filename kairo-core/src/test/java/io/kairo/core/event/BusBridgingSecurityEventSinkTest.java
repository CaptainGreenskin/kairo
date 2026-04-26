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

import io.kairo.api.event.KairoEvent;
import io.kairo.api.guardrail.GuardrailPhase;
import io.kairo.api.guardrail.SecurityEvent;
import io.kairo.api.guardrail.SecurityEventType;
import io.kairo.api.tenant.TenantContext;
import io.kairo.api.tenant.TenantContextHolder;
import io.kairo.core.tenant.ThreadLocalTenantContextHolder;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

class BusBridgingSecurityEventSinkTest {

    @Test
    void delegateReceivesStampedTenantBeforeBusPublish() {
        ThreadLocalTenantContextHolder holder = new ThreadLocalTenantContextHolder();
        DefaultKairoEventBus bus = new DefaultKairoEventBus(holder);
        List<SecurityEvent> delegateRecorded = new CopyOnWriteArrayList<>();
        AtomicReference<KairoEvent> busReceived = new AtomicReference<>();
        Disposable sub = bus.subscribe().subscribe(busReceived::set);

        BusBridgingSecurityEventSink sink =
                new BusBridgingSecurityEventSink(delegateRecorded::add, bus, holder);

        try (TenantContextHolder.Scope ignored =
                holder.bind(new TenantContext("acme", "alice", Map.of()))) {
            sink.record(
                    new SecurityEvent(
                            Instant.ofEpochMilli(1_000),
                            SecurityEventType.GUARDRAIL_DENY,
                            "agent-1",
                            "tool-x",
                            GuardrailPhase.PRE_TOOL,
                            "no-secrets",
                            "blocked",
                            Map.of("decision", "deny")));
        }

        // delegate received the stamped event
        assertEquals(1, delegateRecorded.size());
        SecurityEvent forwarded = delegateRecorded.get(0);
        assertEquals("acme", forwarded.attributes().get(TenantContext.ATTR_TENANT_ID));
        assertEquals("alice", forwarded.attributes().get(TenantContext.ATTR_PRINCIPAL_ID));
        assertEquals("deny", forwarded.attributes().get("decision"));

        // bus event also carries tenant id
        waitUntil(() -> busReceived.get() != null);
        KairoEvent busEvent = busReceived.get();
        assertNotNull(busEvent);
        assertEquals(KairoEvent.DOMAIN_SECURITY, busEvent.domain());
        assertEquals("acme", busEvent.attributes().get(TenantContext.ATTR_TENANT_ID));
        sub.dispose();
    }

    @Test
    void noTenantHolderPreservesV010Behavior() {
        DefaultKairoEventBus bus = new DefaultKairoEventBus();
        List<SecurityEvent> delegateRecorded = new CopyOnWriteArrayList<>();

        // 2-arg ctor (legacy): no tenant stamping
        BusBridgingSecurityEventSink sink =
                new BusBridgingSecurityEventSink(delegateRecorded::add, bus);

        sink.record(
                new SecurityEvent(
                        Instant.now(),
                        SecurityEventType.GUARDRAIL_ALLOW,
                        "agent-1",
                        "tool-x",
                        GuardrailPhase.PRE_TOOL,
                        "policy",
                        "ok",
                        Map.of("decision", "allow")));

        // Delegate sees ONLY the original attributes; no tenant stamping at the SecurityEvent
        // layer.
        assertEquals(1, delegateRecorded.size());
        SecurityEvent forwarded = delegateRecorded.get(0);
        assertEquals(1, forwarded.attributes().size());
        assertEquals("allow", forwarded.attributes().get("decision"));
    }

    @Test
    void callerSuppliedTenantAttributesAreNotOverwritten() {
        ThreadLocalTenantContextHolder holder = new ThreadLocalTenantContextHolder();
        DefaultKairoEventBus bus = new DefaultKairoEventBus(holder);
        List<SecurityEvent> delegateRecorded = new CopyOnWriteArrayList<>();
        BusBridgingSecurityEventSink sink =
                new BusBridgingSecurityEventSink(delegateRecorded::add, bus, holder);

        try (TenantContextHolder.Scope ignored =
                holder.bind(new TenantContext("acme", "alice", Map.of()))) {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(TenantContext.ATTR_TENANT_ID, "explicit");
            attrs.put(TenantContext.ATTR_PRINCIPAL_ID, "explicit-user");
            sink.record(
                    new SecurityEvent(
                            Instant.now(),
                            SecurityEventType.GUARDRAIL_DENY,
                            "agent-1",
                            "tool-x",
                            GuardrailPhase.PRE_TOOL,
                            "no-secrets",
                            "blocked",
                            attrs));
        }

        SecurityEvent forwarded = delegateRecorded.get(0);
        assertEquals("explicit", forwarded.attributes().get(TenantContext.ATTR_TENANT_ID));
        assertEquals("explicit-user", forwarded.attributes().get(TenantContext.ATTR_PRINCIPAL_ID));
    }

    @Test
    void nullEventIsSilentlyIgnored() {
        DefaultKairoEventBus bus = new DefaultKairoEventBus();
        List<SecurityEvent> delegateRecorded = new CopyOnWriteArrayList<>();
        BusBridgingSecurityEventSink sink =
                new BusBridgingSecurityEventSink(delegateRecorded::add, bus);

        sink.record(null);
        assertEquals(0, delegateRecorded.size());
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
