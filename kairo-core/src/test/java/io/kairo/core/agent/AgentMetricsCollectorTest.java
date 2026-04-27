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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AgentMetricsCollectorTest {

    private static AgentSessionMetrics success(String id) {
        Instant now = Instant.now();
        return AgentSessionMetrics.success(
                id, "agent", now, now.plusSeconds(1), 100L, 2, 1, Map.of());
    }

    private static AgentSessionMetrics failure(String id) {
        Instant now = Instant.now();
        return AgentSessionMetrics.failure(
                id, "agent", now, now.plusSeconds(1), 50L, 1, 0, Map.of(), "error");
    }

    @Test
    void initialStateIsEmpty() {
        AgentMetricsCollector c = new AgentMetricsCollector();

        assertThat(c.size()).isZero();
        assertThat(c.getRecent(10)).isEmpty();
    }

    @Test
    void recordIncreasesSize() {
        AgentMetricsCollector c = new AgentMetricsCollector();

        c.record(success("a1"));
        c.record(success("a2"));

        assertThat(c.size()).isEqualTo(2);
    }

    @Test
    void getRecentReturnsNewestFirst() {
        AgentMetricsCollector c = new AgentMetricsCollector();
        for (int i = 1; i <= 5; i++) {
            c.record(success("a" + i));
        }

        List<AgentSessionMetrics> recent = c.getRecent(3);

        assertThat(recent).hasSize(3);
        assertThat(recent.get(2).agentId()).isEqualTo("a5");
        assertThat(recent.get(0).agentId()).isEqualTo("a3");
    }

    @Test
    void circularBufferEvictsOldestWhenFull() {
        AgentMetricsCollector c = new AgentMetricsCollector(3);
        c.record(success("old1"));
        c.record(success("old2"));
        c.record(success("old3"));
        c.record(success("new1"));

        assertThat(c.size()).isEqualTo(3);
        List<AgentSessionMetrics> recent = c.getRecent(10);
        assertThat(recent.stream().map(AgentSessionMetrics::agentId)).doesNotContain("old1");
        assertThat(recent.stream().map(AgentSessionMetrics::agentId))
                .contains("old2", "old3", "new1");
    }

    @Test
    void getSummaryOnEmptyCollector() {
        AgentMetricsCollector.AgentMetricsSummary s = new AgentMetricsCollector().getSummary();

        assertThat(s.totalInvocations()).isZero();
        assertThat(s.successCount()).isZero();
        assertThat(s.successRate()).isZero();
    }

    @Test
    void getSummaryCalculatesSuccessRate() {
        AgentMetricsCollector c = new AgentMetricsCollector();
        c.record(success("a1"));
        c.record(success("a2"));
        c.record(failure("a3"));

        AgentMetricsCollector.AgentMetricsSummary s = c.getSummary();

        assertThat(s.totalInvocations()).isEqualTo(3);
        assertThat(s.successCount()).isEqualTo(2);
        assertThat(s.successRate())
                .isCloseTo(2.0 / 3.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void getSummaryCalculatesAvgTokens() {
        AgentMetricsCollector c = new AgentMetricsCollector();
        Instant now = Instant.now();
        c.record(new AgentSessionMetrics("a", "n", now, now, 200L, 1, 0, Map.of(), true, null));
        c.record(new AgentSessionMetrics("b", "n", now, now, 400L, 1, 0, Map.of(), true, null));

        assertThat(c.getSummary().avgTokensUsed())
                .isCloseTo(300.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void recordIgnoresNull() {
        AgentMetricsCollector c = new AgentMetricsCollector();
        c.record(null);

        assertThat(c.size()).isZero();
    }

    @Test
    void rejectsZeroCapacity() {
        assertThatThrownBy(() -> new AgentMetricsCollector(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void concurrentRecordsAreThreadSafe() throws InterruptedException {
        AgentMetricsCollector c = new AgentMetricsCollector(500);
        int threads = 8;
        int perThread = 50;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicReference<Throwable> error = new AtomicReference<>();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            int tid = t;
            pool.submit(
                    () -> {
                        try {
                            start.await();
                            for (int i = 0; i < perThread; i++) {
                                c.record(success("t" + tid + "-" + i));
                                AgentMetricsCollector.AgentMetricsSummary s = c.getSummary();
                                assertThat(s).isNotNull();
                            }
                        } catch (Throwable e) {
                            error.compareAndSet(null, e);
                        } finally {
                            done.countDown();
                        }
                    });
        }
        start.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(error.get()).isNull();
        assertThat(c.size()).isLessThanOrEqualTo(500);
    }
}
