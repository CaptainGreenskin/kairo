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
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kairo.api.event.KairoEvent;
import io.kairo.api.event.stream.EventStreamSubscription;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class EventStreamRegistryTest {

    private static EventStreamSubscription fakeSub(String id, AtomicBoolean cancelled) {
        return new EventStreamSubscription() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public Flux<KairoEvent> events() {
                return Flux.empty();
            }

            @Override
            public void cancel() {
                cancelled.set(true);
            }

            @Override
            public boolean isActive() {
                return !cancelled.get();
            }
        };
    }

    @Test
    void registerAndSnapshot() {
        EventStreamRegistry registry = new EventStreamRegistry();
        AtomicBoolean c1 = new AtomicBoolean();
        registry.register(fakeSub("a", c1));
        registry.register(fakeSub("b", new AtomicBoolean()));
        assertEquals(2, registry.size());
        assertEquals(2, registry.snapshot().size());
    }

    @Test
    void unregisterRemovesEntry() {
        EventStreamRegistry registry = new EventStreamRegistry();
        registry.register(fakeSub("a", new AtomicBoolean()));
        registry.unregister("a");
        assertEquals(0, registry.size());
    }

    @Test
    void cancelAllCancelsEverySubscription() {
        EventStreamRegistry registry = new EventStreamRegistry();
        AtomicBoolean c1 = new AtomicBoolean();
        AtomicBoolean c2 = new AtomicBoolean();
        registry.register(fakeSub("a", c1));
        registry.register(fakeSub("b", c2));
        registry.cancelAll();
        assertTrue(c1.get());
        assertTrue(c2.get());
        assertEquals(0, registry.size());
    }

    @Test
    void snapshotIsDefensiveCopy() {
        EventStreamRegistry registry = new EventStreamRegistry();
        registry.register(fakeSub("a", new AtomicBoolean()));
        var snap = registry.snapshot();
        registry.unregister("a");
        assertEquals(1, snap.size());
        assertFalse(snap.isEmpty());
    }
}
