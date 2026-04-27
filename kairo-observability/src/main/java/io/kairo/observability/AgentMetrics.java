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

import io.kairo.api.agent.AgentState;
import io.kairo.core.health.AgentCallObserver;
import io.kairo.core.health.AgentHealthInfo;
import io.kairo.core.health.AgentHealthRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Micrometer metrics for Kairo Agent runtime.
 *
 * <p>Registers the following meters:
 *
 * <ul>
 *   <li>{@code kairo.agents.active} — gauge: total non-terminal agents in registry
 *   <li>{@code kairo.agents.running} — gauge: agents in RUNNING state
 *   <li>{@code kairo.agents.idle} — gauge: agents in IDLE state
 *   <li>{@code kairo.agent.calls.total} — counter: incremented on each call() start
 *   <li>{@code kairo.agent.call.duration} — timer: records each call() duration
 * </ul>
 */
public final class AgentMetrics implements AgentCallObserver {

    private final Counter callsTotal;
    private final Timer callDuration;

    public AgentMetrics(MeterRegistry registry) {
        registry.gauge(
                "kairo.agents.active", AgentHealthRegistry.global(), r -> r.snapshot().size());

        registry.gauge(
                "kairo.agents.running",
                AgentHealthRegistry.global(),
                r -> countByState(r.snapshot(), AgentState.RUNNING));

        registry.gauge(
                "kairo.agents.idle",
                AgentHealthRegistry.global(),
                r -> countByState(r.snapshot(), AgentState.IDLE));

        this.callsTotal = registry.counter("kairo.agent.calls.total");
        this.callDuration = Timer.builder("kairo.agent.call.duration").register(registry);
    }

    @Override
    public void onCallStart(String agentId, String agentName) {
        callsTotal.increment();
    }

    @Override
    public void onCallEnd(String agentId, String agentName, Duration duration, boolean success) {
        callDuration.record(duration.toNanos(), TimeUnit.NANOSECONDS);
    }

    private static double countByState(List<AgentHealthInfo> snapshot, AgentState target) {
        return snapshot.stream().filter(i -> i.state() == target).count();
    }
}
