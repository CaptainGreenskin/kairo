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
package io.kairo.api.event;

import io.kairo.api.Experimental;

/**
 * Domain event published when a circuit breaker changes state.
 *
 * <p>Emitted onto {@link KairoEventBus} by {@code ModelCircuitBreaker} and {@code
 * ToolCircuitBreakerTracker} on every state transition. Subscribers (metrics exporters, alerting
 * systems) can use this to track circuit breaker health without parsing logs.
 *
 * @param name breaker identifier, e.g. {@code "model:claude-3-5-sonnet"} or {@code "tool:bash"}
 * @param previous state before the transition
 * @param current state after the transition
 * @param timestamp transition time ({@link System#currentTimeMillis()})
 * @since v0.10 (Experimental)
 */
@Experimental("Circuit breaker observability — contract may change before v1.2.0 stabilization")
public record CircuitBreakerEvent(String name, State previous, State current, long timestamp) {

    /** Circuit breaker states, aligned with {@code CircuitBreakerPrimitive.State}. */
    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    /** Create a new event with {@code System.currentTimeMillis()} as the timestamp. */
    public CircuitBreakerEvent(String name, State previous, State current) {
        this(name, previous, current, System.currentTimeMillis());
    }

    /** Domain tag used when wrapping this event into a {@link KairoEvent} for the bus. */
    public static final String DOMAIN_RESILIENCE = "resilience";

    /** Wrap this event into a {@link KairoEvent} suitable for {@link KairoEventBus#publish}. */
    public KairoEvent toKairoEvent() {
        return KairoEvent.wrap(DOMAIN_RESILIENCE, current.name(), this);
    }
}
