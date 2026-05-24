/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.eventstream.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.kairo.api.event.KairoEvent;
import io.kairo.api.event.stream.BackpressurePolicy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class FluxBackpressureGuardTest {

    @Test
    void rejectsNonPositiveCapacity() {
        Flux<KairoEvent> upstream = Flux.empty();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FluxBackpressureGuard.apply(
                                upstream, BackpressurePolicy.BUFFER_DROP_OLDEST, 0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FluxBackpressureGuard.apply(
                                upstream, BackpressurePolicy.BUFFER_DROP_OLDEST, -1));
    }

    @Test
    void passesEventsThroughUnderNormalDemand() {
        KairoEvent a = KairoEvent.of("execution", "A", Map.of());
        KairoEvent b = KairoEvent.of("execution", "B", Map.of());
        Flux<KairoEvent> guarded =
                FluxBackpressureGuard.apply(
                        Flux.just(a, b), BackpressurePolicy.BUFFER_DROP_OLDEST, 16);
        List<KairoEvent> out = guarded.collectList().block();
        assertEquals(List.of(a, b), out);
    }

    @Test
    void supportsAllPolicies() {
        Flux<KairoEvent> upstream = Flux.empty();
        for (BackpressurePolicy policy : BackpressurePolicy.values()) {
            Flux<KairoEvent> guarded = FluxBackpressureGuard.apply(upstream, policy, 8);
            assertEquals(List.of(), guarded.collectList().block());
        }
    }
}
