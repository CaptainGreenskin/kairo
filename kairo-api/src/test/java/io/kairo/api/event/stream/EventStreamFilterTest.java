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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kairo.api.event.KairoEvent;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventStreamFilterTest {

    private static KairoEvent event(String domain, String eventType, Map<String, Object> attrs) {
        return KairoEvent.of(domain, eventType, attrs);
    }

    @Test
    void acceptAll_matchesEverything() {
        assertTrue(EventStreamFilter.acceptAll().test(event("execution", "MODEL_CALL", Map.of())));
    }

    @Test
    void byDomain_matchesOnlyListedDomains() {
        EventStreamFilter filter = EventStreamFilter.byDomain("execution", "team");
        assertTrue(filter.test(event("execution", "X", Map.of())));
        assertTrue(filter.test(event("team", "Y", Map.of())));
        assertFalse(filter.test(event("security", "Z", Map.of())));
    }

    @Test
    void byDomain_rejectsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> EventStreamFilter.byDomain());
    }

    @Test
    void byEventType_matchesOnlyListedTypes() {
        EventStreamFilter filter = EventStreamFilter.byEventType("MODEL_CALL", "TOOL_CALL");
        assertTrue(filter.test(event("execution", "MODEL_CALL", Map.of())));
        assertTrue(filter.test(event("execution", "TOOL_CALL", Map.of())));
        assertFalse(filter.test(event("execution", "OTHER", Map.of())));
    }

    @Test
    void byAttribute_matchesExactValue() {
        EventStreamFilter filter = EventStreamFilter.byAttribute("tenant", "acme");
        assertTrue(filter.test(event("execution", "X", Map.of("tenant", "acme"))));
        assertFalse(filter.test(event("execution", "X", Map.of("tenant", "other"))));
        assertFalse(filter.test(event("execution", "X", Map.of())));
    }

    @Test
    void and_composesTwoFilters() {
        EventStreamFilter both =
                EventStreamFilter.byDomain("execution").and(EventStreamFilter.byEventType("X"));
        assertTrue(both.test(event("execution", "X", Map.of())));
        assertFalse(both.test(event("execution", "Y", Map.of())));
        assertFalse(both.test(event("team", "X", Map.of())));
    }

    @Test
    void or_composesTwoFilters() {
        EventStreamFilter either =
                EventStreamFilter.byDomain("execution").or(EventStreamFilter.byDomain("team"));
        assertTrue(either.test(event("execution", "X", Map.of())));
        assertTrue(either.test(event("team", "X", Map.of())));
        assertFalse(either.test(event("security", "X", Map.of())));
    }
}
