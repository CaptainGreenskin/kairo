/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.api.event.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EventStreamSubscriptionRequestTest {

    @Test
    void rejectsNonPositiveBufferCapacity() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new EventStreamSubscriptionRequest(
                                EventStreamFilter.acceptAll(),
                                BackpressurePolicy.BUFFER_DROP_OLDEST,
                                0,
                                Map.of()));
    }

    @Test
    void copiesAuthorizationContext() {
        Map<String, String> mutable = new HashMap<>();
        mutable.put("auth", "bearer xyz");
        EventStreamSubscriptionRequest req =
                new EventStreamSubscriptionRequest(
                        EventStreamFilter.acceptAll(),
                        BackpressurePolicy.BUFFER_DROP_OLDEST,
                        16,
                        mutable);

        mutable.put("extra", "injected");
        assertEquals(Set.of("auth"), req.authorizationContext().keySet());
    }

    @Test
    void nullAuthorizationContextBecomesEmptyMap() {
        EventStreamSubscriptionRequest req =
                new EventStreamSubscriptionRequest(
                        EventStreamFilter.acceptAll(),
                        BackpressurePolicy.BUFFER_DROP_OLDEST,
                        16,
                        null);
        assertTrue(req.authorizationContext().isEmpty());
    }
}
