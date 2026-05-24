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
import static org.assertj.core.api.Assertions.within;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentCallMetricsTest {

    private MeterRegistry registry;
    private AgentCallMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AgentCallMetrics(registry);
    }

    @Test
    void onCallStartIncrementsActiveCalls() {
        metrics.onCallStart("agent-1", "TestAgent");

        Gauge active = registry.find("kairo.agent.calls.active").gauge();
        assertThat(active).isNotNull();
        assertThat(active.value()).isEqualTo(1.0);
    }

    @Test
    void onCallEndDecrementsActiveCalls() {
        metrics.onCallStart("agent-1", "TestAgent");
        metrics.onCallEnd("agent-1", "TestAgent", Duration.ofMillis(100), true);

        Gauge active = registry.find("kairo.agent.calls.active").gauge();
        assertThat(active.value()).isEqualTo(0.0);
    }

    @Test
    void onCallEndRecordsCounterWithTags() {
        metrics.onCallEnd("agent-1", "TestAgent", Duration.ofMillis(100), true);

        Counter counter =
                registry.find("kairo.agent.calls.total")
                        .tag("agentName", "TestAgent")
                        .tag("success", "true")
                        .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void onCallEndRecordsFailureCounterWithTags() {
        metrics.onCallEnd("agent-1", "TestAgent", Duration.ofMillis(100), false);

        Counter counter =
                registry.find("kairo.agent.calls.total")
                        .tag("agentName", "TestAgent")
                        .tag("success", "false")
                        .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void onCallEndRecordsTimerWithTags() {
        Duration duration = Duration.ofMillis(150);
        metrics.onCallEnd("agent-1", "TestAgent", duration, true);

        Timer timer =
                registry.find("kairo.agent.call.duration")
                        .tag("agentName", "TestAgent")
                        .tag("success", "true")
                        .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isCloseTo(150.0, within(1.0));
    }

    @Test
    void multipleAgentsHaveIndependentMetrics() {
        metrics.onCallEnd("agent-1", "AgentA", Duration.ofMillis(100), true);
        metrics.onCallEnd("agent-2", "AgentB", Duration.ofMillis(200), false);

        Counter counterA =
                registry.find("kairo.agent.calls.total")
                        .tag("agentName", "AgentA")
                        .tag("success", "true")
                        .counter();
        assertThat(counterA.count()).isEqualTo(1.0);

        Counter counterB =
                registry.find("kairo.agent.calls.total")
                        .tag("agentName", "AgentB")
                        .tag("success", "false")
                        .counter();
        assertThat(counterB.count()).isEqualTo(1.0);
    }

    @Test
    void multipleCallsAccumulateCorrectly() {
        metrics.onCallEnd("agent-1", "TestAgent", Duration.ofMillis(100), true);
        metrics.onCallEnd("agent-1", "TestAgent", Duration.ofMillis(200), true);
        metrics.onCallEnd("agent-1", "TestAgent", Duration.ofMillis(50), false);

        Counter successCounter =
                registry.find("kairo.agent.calls.total")
                        .tag("agentName", "TestAgent")
                        .tag("success", "true")
                        .counter();
        assertThat(successCounter.count()).isEqualTo(2.0);

        Counter failureCounter =
                registry.find("kairo.agent.calls.total")
                        .tag("agentName", "TestAgent")
                        .tag("success", "false")
                        .counter();
        assertThat(failureCounter.count()).isEqualTo(1.0);

        Timer successTimer =
                registry.find("kairo.agent.call.duration")
                        .tag("agentName", "TestAgent")
                        .tag("success", "true")
                        .timer();
        assertThat(successTimer.count()).isEqualTo(2);

        Timer failureTimer =
                registry.find("kairo.agent.call.duration")
                        .tag("agentName", "TestAgent")
                        .tag("success", "false")
                        .timer();
        assertThat(failureTimer.count()).isEqualTo(1);
    }

    @Test
    void activeCallsTracksConcurrentCalls() {
        metrics.onCallStart("agent-1", "AgentA");
        metrics.onCallStart("agent-2", "AgentB");
        metrics.onCallStart("agent-3", "AgentC");

        Gauge active = registry.find("kairo.agent.calls.active").gauge();
        assertThat(active.value()).isEqualTo(3.0);

        metrics.onCallEnd("agent-1", "AgentA", Duration.ofMillis(100), true);
        assertThat(active.value()).isEqualTo(2.0);

        metrics.onCallEnd("agent-2", "AgentB", Duration.ofMillis(200), false);
        assertThat(active.value()).isEqualTo(1.0);

        metrics.onCallEnd("agent-3", "AgentC", Duration.ofMillis(50), true);
        assertThat(active.value()).isEqualTo(0.0);
    }

    @Test
    void emptyAgentNameIsHandled() {
        metrics.onCallStart("", "");
        metrics.onCallEnd("", "", Duration.ofMillis(100), true);

        Counter counter =
                registry.find("kairo.agent.calls.total")
                        .tag("agentName", "")
                        .tag("success", "true")
                        .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
}
