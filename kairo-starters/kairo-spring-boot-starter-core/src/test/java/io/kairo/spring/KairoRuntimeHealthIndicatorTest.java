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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kairo.api.event.KairoEvent;
import io.kairo.core.event.DefaultKairoEventBus;
import io.kairo.core.health.AgentHealthRegistry;
import io.kairo.core.model.ModelCircuitBreaker;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

class KairoRuntimeHealthIndicatorTest {

    @Test
    void reportsUpWhenBreakerClosedAndBusHealthy() {
        ModelCircuitBreaker breaker =
                new ModelCircuitBreaker("test-model", 3, Duration.ofSeconds(1));
        DefaultKairoEventBus bus = new DefaultKairoEventBus();
        AgentHealthRegistry registry = AgentHealthRegistry.global();
        registry.deregisterAll();

        Health health = new KairoRuntimeHealthIndicator(breaker, bus, registry).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("CLOSED", health.getDetails().get("modelCircuitBreaker.state"));
        assertEquals("test-model", health.getDetails().get("modelCircuitBreaker.modelId"));
        assertEquals(0L, health.getDetails().get("eventBus.droppedCount"));
        assertEquals(0, health.getDetails().get("agents.active"));
    }

    @Test
    void reportsDownWhenBreakerOpens() {
        ModelCircuitBreaker breaker =
                new ModelCircuitBreaker("test-model", 1, Duration.ofMinutes(1));
        breaker.recordFailure(); // threshold=1, immediately opens
        DefaultKairoEventBus bus = new DefaultKairoEventBus();
        AgentHealthRegistry registry = AgentHealthRegistry.global();
        registry.deregisterAll();

        Health health = new KairoRuntimeHealthIndicator(breaker, bus, registry).health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("OPEN", health.getDetails().get("modelCircuitBreaker.state"));
    }

    @Test
    void marksBreakerDisabledWhenNullAndStillReportsBus() {
        DefaultKairoEventBus bus = new DefaultKairoEventBus();
        AgentHealthRegistry registry = AgentHealthRegistry.global();
        registry.deregisterAll();

        Health health = new KairoRuntimeHealthIndicator(null, bus, registry).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("DISABLED", health.getDetails().get("modelCircuitBreaker.state"));
        assertNotNull(health.getDetails().get("eventBus.bufferSize"));
    }

    @Test
    void surfacesEventBusDropsAsDetailWithoutFlippingStatus() {
        DefaultKairoEventBus bus =
                new DefaultKairoEventBus(io.kairo.api.tenant.TenantContextHolder.NOOP, 4);
        for (int i = 0; i < 20; i++) {
            bus.publish(KairoEvent.of(KairoEvent.DOMAIN_EXECUTION, "evt-" + i, Map.of()));
        }
        AgentHealthRegistry registry = AgentHealthRegistry.global();
        registry.deregisterAll();

        Health health = new KairoRuntimeHealthIndicator(null, bus, registry).health();

        assertEquals(Status.UP, health.getStatus(), "drops alone must not flip status");
        long dropped = (long) health.getDetails().get("eventBus.droppedCount");
        assertTrue(dropped > 0, "expected drops to be surfaced as detail, got " + dropped);
        assertEquals(4, health.getDetails().get("eventBus.bufferSize"));
    }
}
