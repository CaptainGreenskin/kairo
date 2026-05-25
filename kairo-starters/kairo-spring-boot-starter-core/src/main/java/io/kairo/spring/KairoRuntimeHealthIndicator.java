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
package io.kairo.spring;

import io.kairo.api.event.KairoEventBus;
import io.kairo.core.event.DefaultKairoEventBus;
import io.kairo.core.health.AgentHealthRegistry;
import io.kairo.core.model.ModelCircuitBreaker;
import javax.annotation.Nullable;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Spring Actuator health contribution covering the Kairo agent runtime: model circuit-breaker
 * state, event-bus backpressure, and live-agent count.
 *
 * <p>Status mapping:
 *
 * <ul>
 *   <li>{@code DOWN} — model circuit breaker is {@code OPEN} (upstream model is being protected
 *       from traffic; new requests will fail-fast).
 *   <li>{@code UP} — everything else. Event-bus drops are surfaced as a detail rather than a status
 *       flip because a few dropped envelopes during a slow subscriber blip should not page on-call
 *       for the whole app.
 * </ul>
 *
 * <p>The indicator complements {@link
 * io.kairo.spring.observability.KairoObservabilityHealthIndicator}, which covers the OTel export
 * path. This one covers the runtime *into* that path.
 */
public class KairoRuntimeHealthIndicator implements HealthIndicator {

    @Nullable private final ModelCircuitBreaker modelBreaker;
    private final KairoEventBus eventBus;
    private final AgentHealthRegistry agentRegistry;

    public KairoRuntimeHealthIndicator(
            @Nullable ModelCircuitBreaker modelBreaker, KairoEventBus eventBus) {
        this(modelBreaker, eventBus, AgentHealthRegistry.global());
    }

    KairoRuntimeHealthIndicator(
            @Nullable ModelCircuitBreaker modelBreaker,
            KairoEventBus eventBus,
            AgentHealthRegistry agentRegistry) {
        this.modelBreaker = modelBreaker;
        this.eventBus = eventBus;
        this.agentRegistry = agentRegistry;
    }

    @Override
    public Health health() {
        boolean down = false;
        Health.Builder builder = Health.up();

        if (modelBreaker != null) {
            ModelCircuitBreaker.State state = modelBreaker.getState();
            builder.withDetail("modelCircuitBreaker.modelId", modelBreaker.getModelId())
                    .withDetail("modelCircuitBreaker.state", state.name());
            if (state == ModelCircuitBreaker.State.OPEN) {
                down = true;
            }
        } else {
            builder.withDetail("modelCircuitBreaker.state", "DISABLED");
        }

        if (eventBus instanceof DefaultKairoEventBus defaultBus) {
            builder.withDetail("eventBus.bufferSize", defaultBus.bufferSize())
                    .withDetail("eventBus.droppedCount", defaultBus.droppedCount());
        }

        builder.withDetail("agents.active", agentRegistry.snapshot().size());

        if (down) {
            Health snapshot = builder.build();
            return Health.down().withDetails(snapshot.getDetails()).build();
        }
        return builder.build();
    }
}
