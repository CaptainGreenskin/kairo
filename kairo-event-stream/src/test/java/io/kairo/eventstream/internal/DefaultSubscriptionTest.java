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
package io.kairo.eventstream.internal;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.event.KairoEvent;
import io.kairo.eventstream.EventStreamRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

class DefaultSubscriptionTest {

    private EventStreamRegistry registry;
    private Sinks.Empty<Void> cancelSignal;
    private DefaultSubscription subscription;

    @BeforeEach
    void setUp() {
        registry = new EventStreamRegistry();
        cancelSignal = Sinks.empty();
        subscription = new DefaultSubscription("sub-1", Flux.never(), cancelSignal, registry);
    }

    @Test
    void idReturnsConstructedValue() {
        assertEquals("sub-1", subscription.id());
    }

    @Test
    void isActiveInitiallyTrue() {
        assertTrue(subscription.isActive());
    }

    @Test
    void cancelSetsInactive() {
        subscription.cancel();
        assertFalse(subscription.isActive());
    }

    @Test
    void cancelUnregistersFromRegistry() {
        registry.register(subscription);
        assertEquals(1, registry.size());
        subscription.cancel();
        assertEquals(0, registry.size());
    }

    @Test
    void cancelIdempotentDoesNotThrow() {
        subscription.cancel();
        assertDoesNotThrow(subscription::cancel);
        assertFalse(subscription.isActive());
    }

    @Test
    void eventsFluxNonNull() {
        assertNotNull(subscription.events());
    }

    @Test
    void eventsFluxEmitsFromSource() {
        Sinks.Many<KairoEvent> source = Sinks.many().multicast().onBackpressureBuffer();
        Sinks.Empty<Void> cancel = Sinks.empty();
        DefaultSubscription sub =
                new DefaultSubscription("sub-2", source.asFlux(), cancel, registry);

        var events = sub.events().collectList();
        source.tryEmitComplete();
        assertNotNull(events.block());
    }
}
