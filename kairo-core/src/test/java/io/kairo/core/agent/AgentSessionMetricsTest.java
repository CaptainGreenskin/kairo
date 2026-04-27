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
package io.kairo.core.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentSessionMetricsTest {

    @Test
    void successFactoryPopulatesAllFields() {
        Instant start = Instant.parse("2026-04-27T10:00:00Z");
        Instant end = Instant.parse("2026-04-27T10:00:05Z");

        AgentSessionMetrics m =
                AgentSessionMetrics.success(
                        "agent-1",
                        "myAgent",
                        start,
                        end,
                        2048L,
                        5,
                        3,
                        Map.of("read", 2, "write", 1));

        assertThat(m.agentId()).isEqualTo("agent-1");
        assertThat(m.agentName()).isEqualTo("myAgent");
        assertThat(m.startTime()).isEqualTo(start);
        assertThat(m.endTime()).isEqualTo(end);
        assertThat(m.totalTokensUsed()).isEqualTo(2048L);
        assertThat(m.totalIterations()).isEqualTo(5);
        assertThat(m.totalToolCalls()).isEqualTo(3);
        assertThat(m.toolCallCounts()).containsEntry("read", 2).containsEntry("write", 1);
        assertThat(m.succeeded()).isTrue();
        assertThat(m.failureReason()).isNull();
    }

    @Test
    void failureFactoryPopulatesFailureReason() {
        Instant start = Instant.now();
        Instant end = start.plusSeconds(2);

        AgentSessionMetrics m =
                AgentSessionMetrics.failure(
                        "agent-2",
                        "failing",
                        start,
                        end,
                        100L,
                        1,
                        0,
                        Map.of(),
                        "max iterations exceeded");

        assertThat(m.succeeded()).isFalse();
        assertThat(m.failureReason()).isEqualTo("max iterations exceeded");
    }

    @Test
    void durationMsCalculatedCorrectly() {
        Instant start = Instant.ofEpochMilli(1000L);
        Instant end = Instant.ofEpochMilli(3500L);

        AgentSessionMetrics m =
                AgentSessionMetrics.success("a", "b", start, end, 0L, 0, 0, Map.of());

        assertThat(m.durationMs()).isEqualTo(2500L);
    }

    @Test
    void durationMsIsNegativeOneWhenEndTimeNull() {
        AgentSessionMetrics m =
                new AgentSessionMetrics(
                        "a", "b", Instant.now(), null, 0L, 0, 0, Map.of(), true, null);

        assertThat(m.durationMs()).isEqualTo(-1L);
    }

    @Test
    void toolCallCountsAreCopiedDefensively() {
        java.util.HashMap<String, Integer> original = new java.util.HashMap<>();
        original.put("tool1", 5);

        AgentSessionMetrics m =
                AgentSessionMetrics.success(
                        "a", "b", Instant.now(), Instant.now(), 0L, 0, 0, original);
        original.put("tool2", 99);

        assertThat(m.toolCallCounts()).doesNotContainKey("tool2");
    }

    @Test
    void recordIsImmutableValueObject() {
        Instant now = Instant.now();
        AgentSessionMetrics a =
                AgentSessionMetrics.success("x", "y", now, now, 100L, 2, 1, Map.of("t", 1));
        AgentSessionMetrics b =
                AgentSessionMetrics.success("x", "y", now, now, 100L, 2, 1, Map.of("t", 1));

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
