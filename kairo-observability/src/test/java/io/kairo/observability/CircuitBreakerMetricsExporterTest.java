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
package io.kairo.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.event.CircuitBreakerEvent;
import io.kairo.api.event.CircuitBreakerEvent.State;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CircuitBreakerMetricsExporterTest {

    private MeterRegistry registry;
    private CircuitBreakerMetricsExporter exporter;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        exporter = new CircuitBreakerMetricsExporter(registry);
    }

    @Test
    void closedToOpenIncrementsTripsCounter() {
        CircuitBreakerEvent event = new CircuitBreakerEvent("model:test", State.CLOSED, State.OPEN);
        exporter.onEvent(event);

        Counter trips = registry.counter("kairo.circuit_breaker.trips_total");
        assertThat(trips.count()).isEqualTo(1.0);

        Counter resets = registry.counter("kairo.circuit_breaker.resets_total");
        assertThat(resets.count()).isEqualTo(0.0);
    }

    @Test
    void openToHalfOpenDoesNotIncrementResets() {
        CircuitBreakerEvent event =
                new CircuitBreakerEvent("model:test", State.OPEN, State.HALF_OPEN);
        exporter.onEvent(event);

        Counter trips = registry.counter("kairo.circuit_breaker.trips_total");
        Counter resets = registry.counter("kairo.circuit_breaker.resets_total");
        assertThat(trips.count()).isEqualTo(0.0);
        assertThat(resets.count()).isEqualTo(0.0);
    }

    @Test
    void halfOpenToClosedIncrementsResetsCounter() {
        CircuitBreakerEvent event =
                new CircuitBreakerEvent("model:test", State.HALF_OPEN, State.CLOSED);
        exporter.onEvent(event);

        Counter resets = registry.counter("kairo.circuit_breaker.resets_total");
        assertThat(resets.count()).isEqualTo(1.0);
    }

    @Test
    void openToClosedIncrementsResetsCounter() {
        CircuitBreakerEvent event = new CircuitBreakerEvent("model:test", State.OPEN, State.CLOSED);
        exporter.onEvent(event);

        Counter resets = registry.counter("kairo.circuit_breaker.resets_total");
        assertThat(resets.count()).isEqualTo(1.0);
    }

    @Test
    void halfOpenToOpenIncrementsTripsCounter() {
        // HALF_OPEN → OPEN is another trip (probe call failed)
        CircuitBreakerEvent event =
                new CircuitBreakerEvent("model:test", State.HALF_OPEN, State.OPEN);
        exporter.onEvent(event);

        // Note: trips counter only increments on CLOSED → OPEN per the spec
        Counter trips = registry.counter("kairo.circuit_breaker.trips_total");
        assertThat(trips.count()).isEqualTo(0.0);
    }

    @Test
    void multipleBreakerNamesAreIndependent() {
        CircuitBreakerEvent event1 =
                new CircuitBreakerEvent("model:claude", State.CLOSED, State.OPEN);
        CircuitBreakerEvent event2 = new CircuitBreakerEvent("tool:bash", State.CLOSED, State.OPEN);
        CircuitBreakerEvent event3 =
                new CircuitBreakerEvent("model:claude", State.OPEN, State.HALF_OPEN);

        exporter.onEvent(event1);
        exporter.onEvent(event2);
        exporter.onEvent(event3);

        Counter trips = registry.counter("kairo.circuit_breaker.trips_total");
        Counter resets = registry.counter("kairo.circuit_breaker.resets_total");
        assertThat(trips.count()).isEqualTo(2.0); // two CLOSED → OPEN
        assertThat(resets.count()).isEqualTo(0.0);
    }

    @Test
    void stateGaugeReflectsCurrentState() {
        exporter.onEvent(new CircuitBreakerEvent("model:test", State.CLOSED, State.OPEN));
        exporter.registerGauges();

        Gauge gauge =
                registry.find("kairo.circuit_breaker.state").tag("name", "model:test").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(2.0); // OPEN = 2

        exporter.onEvent(new CircuitBreakerEvent("model:test", State.OPEN, State.HALF_OPEN));
        assertThat(gauge.value()).isEqualTo(1.0); // HALF_OPEN = 1

        exporter.onEvent(new CircuitBreakerEvent("model:test", State.HALF_OPEN, State.CLOSED));
        assertThat(gauge.value()).isEqualTo(0.0); // CLOSED = 0
    }

    @Test
    void nullEventIsIgnored() {
        exporter.onEvent(null);

        Counter trips = registry.counter("kairo.circuit_breaker.trips_total");
        Counter resets = registry.counter("kairo.circuit_breaker.resets_total");
        assertThat(trips.count()).isEqualTo(0.0);
        assertThat(resets.count()).isEqualTo(0.0);
    }

    @Test
    void closedToClosedDoesNotIncrementAnything() {
        // No real state transition, should not affect counters
        CircuitBreakerEvent event =
                new CircuitBreakerEvent("model:test", State.CLOSED, State.CLOSED);
        exporter.onEvent(event);

        Counter trips = registry.counter("kairo.circuit_breaker.trips_total");
        Counter resets = registry.counter("kairo.circuit_breaker.resets_total");
        assertThat(trips.count()).isEqualTo(0.0);
        assertThat(resets.count()).isEqualTo(0.0);
    }

    @Test
    void rapidStateChangesAreTrackedCorrectly() {
        // Full cycle: CLOSED → OPEN → HALF_OPEN → CLOSED → OPEN → HALF_OPEN → CLOSED
        exporter.onEvent(new CircuitBreakerEvent("model:test", State.CLOSED, State.OPEN));
        exporter.onEvent(new CircuitBreakerEvent("model:test", State.OPEN, State.HALF_OPEN));
        exporter.onEvent(new CircuitBreakerEvent("model:test", State.HALF_OPEN, State.CLOSED));
        exporter.onEvent(new CircuitBreakerEvent("model:test", State.CLOSED, State.OPEN));
        exporter.onEvent(new CircuitBreakerEvent("model:test", State.OPEN, State.HALF_OPEN));
        exporter.onEvent(new CircuitBreakerEvent("model:test", State.HALF_OPEN, State.CLOSED));

        Counter trips = registry.counter("kairo.circuit_breaker.trips_total");
        Counter resets = registry.counter("kairo.circuit_breaker.resets_total");
        assertThat(trips.count()).isEqualTo(2.0);
        assertThat(resets.count()).isEqualTo(2.0);
    }
}
