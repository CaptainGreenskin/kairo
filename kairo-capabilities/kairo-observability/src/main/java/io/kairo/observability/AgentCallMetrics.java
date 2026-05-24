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

import io.kairo.core.health.AgentCallObserver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Micrometer metrics for Kairo Agent call lifecycles.
 *
 * <p>Implements {@link AgentCallObserver} to record per-agent metrics with tags. Registers the
 * following meters:
 *
 * <ul>
 *   <li>{@code kairo.agent.calls.active} — gauge: number of in-flight calls
 *   <li>{@code kairo.agent.calls.total} — counter: total calls per agent (tags: agentName, success)
 *   <li>{@code kairo.agent.call.duration} — timer: call duration per agent (tags: agentName,
 *       success)
 * </ul>
 */
public final class AgentCallMetrics implements AgentCallObserver {

    private final MeterRegistry registry;
    private final AtomicLong activeCalls = new AtomicLong(0);

    public AgentCallMetrics(MeterRegistry registry) {
        this.registry = registry;
        registry.gauge("kairo.agent.calls.active", activeCalls);
    }

    @Override
    public void onCallStart(String agentId, String agentName) {
        activeCalls.incrementAndGet();
    }

    @Override
    public void onCallEnd(String agentId, String agentName, Duration duration, boolean success) {
        activeCalls.decrementAndGet();

        String successTag = success ? "true" : "false";

        Counter.builder("kairo.agent.calls.total")
                .tag("agentName", agentName)
                .tag("success", successTag)
                .register(registry)
                .increment();

        Timer.builder("kairo.agent.call.duration")
                .tag("agentName", agentName)
                .tag("success", successTag)
                .register(registry)
                .record(duration);
    }
}
