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

import io.kairo.eventstream.EventStreamRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

class DefaultSubscriptionTest {

    private DefaultSubscription newSubscription(String id) {
        EventStreamRegistry registry = new EventStreamRegistry();
        Sinks.Empty<Void> cancelSignal = Sinks.empty();
        return new DefaultSubscription(id, Flux.never(), cancelSignal, registry);
    }

    @Test
    void idIsPreserved() {
        DefaultSubscription sub = newSubscription("sub-1");
        assertEquals("sub-1", sub.id());
    }

    @Test
    void isActiveInitiallyTrue() {
        DefaultSubscription sub = newSubscription("sub-2");
        assertTrue(sub.isActive());
    }

    @Test
    void cancelMakesInactive() {
        DefaultSubscription sub = newSubscription("sub-3");
        sub.cancel();
        assertFalse(sub.isActive());
    }

    @Test
    void cancelIsIdempotent() {
        DefaultSubscription sub = newSubscription("sub-4");
        sub.cancel();
        sub.cancel();
        assertFalse(sub.isActive());
    }

    @Test
    void eventsFluxIsNotNull() {
        DefaultSubscription sub = newSubscription("sub-5");
        assertNotNull(sub.events());
    }

    @Test
    void unregistersFromRegistryOnCancel() {
        EventStreamRegistry registry = new EventStreamRegistry();
        Sinks.Empty<Void> cancelSignal = Sinks.empty();
        DefaultSubscription sub =
                new DefaultSubscription("sub-6", Flux.never(), cancelSignal, registry);
        registry.register(sub);
        assertEquals(1, registry.size());
        sub.cancel();
        assertEquals(0, registry.size());
    }
}
