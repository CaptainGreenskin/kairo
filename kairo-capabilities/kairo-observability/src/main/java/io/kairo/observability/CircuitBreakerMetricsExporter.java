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

import io.kairo.api.event.CircuitBreakerEvent;
import io.kairo.api.event.CircuitBreakerEvent.State;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Subscribes to {@link CircuitBreakerEvent} on the KairoEventBus and updates Micrometer metrics:
 *
 * <ul>
 *   <li>{@code kairo.circuit_breaker.state} — gauge: 0=CLOSED, 1=HALF_OPEN, 2=OPEN (per breaker
 *       name)
 *   <li>{@code kairo.circuit_breaker.trips_total} — counter: CLOSED → OPEN transitions
 *   <li>{@code kairo.circuit_breaker.resets_total} — counter: any → CLOSED transitions
 * </ul>
 *
 * <p>Usage: call {@link #onEvent(CircuitBreakerEvent)} for each event received from the bus.
 */
public final class CircuitBreakerMetricsExporter {

    private final MeterRegistry registry;
    private final Counter tripsCounter;
    private final Counter resetsCounter;
    private final Map<String, AtomicInteger> stateGauges = new ConcurrentHashMap<>();

    public CircuitBreakerMetricsExporter(MeterRegistry registry) {
        this.registry = registry;
        this.tripsCounter = registry.counter("kairo.circuit_breaker.trips_total");
        this.resetsCounter = registry.counter("kairo.circuit_breaker.resets_total");
    }

    /**
     * Process a circuit breaker state change event and update metrics accordingly.
     *
     * @param event the state change event
     */
    public void onEvent(CircuitBreakerEvent event) {
        if (event == null) return;

        String name = event.name();
        State previous = event.previous();
        State current = event.current();

        // Update trips counter: CLOSED → OPEN
        if (previous == State.CLOSED && current == State.OPEN) {
            tripsCounter.increment();
        }

        // Update resets counter: any non-CLOSED → CLOSED
        if (previous != State.CLOSED && current == State.CLOSED) {
            resetsCounter.increment();
        }

        // Update per-breaker state gauge
        updateStateGauge(name, current);
    }

    private void updateStateGauge(String name, State current) {
        stateGauges.computeIfAbsent(name, k -> new AtomicInteger(0)).set(stateToInt(current));
    }

    /**
     * Register all currently known breaker state gauges with the meter registry.
     *
     * <p>Call this after the exporter has been running for a while to register gauges for breakers
     * that have been discovered. Typically called by the auto-configuration on a periodic basis or
     * at startup for pre-existing breakers.
     */
    public void registerGauges() {
        for (Map.Entry<String, AtomicInteger> entry : stateGauges.entrySet()) {
            String name = entry.getKey();
            Gauge.builder("kairo.circuit_breaker.state", entry.getValue(), AtomicInteger::get)
                    .tag("name", name)
                    .register(registry);
        }
    }

    private static int stateToInt(State state) {
        return switch (state) {
            case CLOSED -> 0;
            case HALF_OPEN -> 1;
            case OPEN -> 2;
        };
    }
}
