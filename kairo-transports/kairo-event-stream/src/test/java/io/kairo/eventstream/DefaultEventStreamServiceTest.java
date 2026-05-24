/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.eventstream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kairo.api.event.KairoEvent;
import io.kairo.api.event.stream.BackpressurePolicy;
import io.kairo.api.event.stream.EventStreamFilter;
import io.kairo.api.event.stream.EventStreamSubscription;
import io.kairo.api.event.stream.EventStreamSubscriptionRequest;
import io.kairo.api.event.stream.KairoEventStreamAuthorizer;
import io.kairo.api.event.stream.KairoEventStreamAuthorizer.AuthorizationDecision;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

class DefaultEventStreamServiceTest {

    private static EventStreamSubscriptionRequest acceptAllRequest() {
        return new EventStreamSubscriptionRequest(
                EventStreamFilter.acceptAll(), BackpressurePolicy.BUFFER_DROP_OLDEST, 16, Map.of());
    }

    @Test
    void deniesWhenAuthorizerDenies() {
        FakeKairoEventBus bus = new FakeKairoEventBus();
        KairoEventStreamAuthorizer deny = req -> AuthorizationDecision.deny("nope");
        DefaultEventStreamService service =
                new DefaultEventStreamService(bus, deny, new EventStreamRegistry());

        EventStreamAuthorizationException ex =
                assertThrows(
                        EventStreamAuthorizationException.class,
                        () -> service.subscribe(acceptAllRequest()));
        assertEquals("nope", ex.getMessage());
    }

    @Test
    void allowsAndRoutesMatchingEventsOnly() throws InterruptedException {
        FakeKairoEventBus bus = new FakeKairoEventBus();
        KairoEventStreamAuthorizer allow = req -> AuthorizationDecision.allow();
        EventStreamRegistry registry = new EventStreamRegistry();
        DefaultEventStreamService service = new DefaultEventStreamService(bus, allow, registry);

        EventStreamSubscription sub =
                service.subscribe(
                        new EventStreamSubscriptionRequest(
                                EventStreamFilter.byDomain("execution"),
                                BackpressurePolicy.BUFFER_DROP_OLDEST,
                                16,
                                Map.of()));

        CopyOnWriteArrayList<KairoEvent> received = new CopyOnWriteArrayList<>();
        Disposable d = sub.events().subscribe(received::add);

        bus.publish(KairoEvent.of("execution", "X", Map.of()));
        bus.publish(KairoEvent.of("security", "Y", Map.of()));
        bus.publish(KairoEvent.of("execution", "Z", Map.of()));

        waitUntil(() -> received.size() == 2);

        assertEquals(2, received.size());
        assertEquals("X", received.get(0).eventType());
        assertEquals("Z", received.get(1).eventType());
        assertNotNull(sub.id());
        assertTrue(sub.isActive());

        d.dispose();
    }

    @Test
    void activeSubscriptionCountTracksLifecycle() {
        FakeKairoEventBus bus = new FakeKairoEventBus();
        KairoEventStreamAuthorizer allow = req -> AuthorizationDecision.allow();
        EventStreamRegistry registry = new EventStreamRegistry();
        DefaultEventStreamService service = new DefaultEventStreamService(bus, allow, registry);

        assertEquals(0, service.activeSubscriptionCount());
        EventStreamSubscription s1 = service.subscribe(acceptAllRequest());
        EventStreamSubscription s2 = service.subscribe(acceptAllRequest());
        assertEquals(2, service.activeSubscriptionCount());

        s1.cancel();
        assertEquals(1, service.activeSubscriptionCount());
        assertFalse(s1.isActive());

        s2.cancel();
        assertEquals(0, service.activeSubscriptionCount());
    }

    @Test
    void cancelTerminatesEventFluxForSubscriber() throws InterruptedException {
        FakeKairoEventBus bus = new FakeKairoEventBus();
        KairoEventStreamAuthorizer allow = req -> AuthorizationDecision.allow();
        DefaultEventStreamService service =
                new DefaultEventStreamService(bus, allow, new EventStreamRegistry());

        EventStreamSubscription sub = service.subscribe(acceptAllRequest());
        AtomicBoolean completed = new AtomicBoolean();
        Disposable d = sub.events().doOnComplete(() -> completed.set(true)).subscribe();

        sub.cancel();
        waitUntil(completed::get);
        assertTrue(completed.get());

        d.dispose();
    }

    private static void waitUntil(java.util.function.BooleanSupplier predicate)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(2).toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (predicate.getAsBoolean()) {
                return;
            }
            Thread.sleep(10L);
        }
    }
}
