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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe collector of per-session agent execution metrics.
 *
 * <p>Maintains an in-memory circular buffer of the most recent {@link AgentSessionMetrics} records.
 * Older records are evicted once the buffer reaches its capacity limit.
 */
public class AgentMetricsCollector {

    private static final int DEFAULT_CAPACITY = 1000;

    private final int capacity;
    private final Deque<AgentSessionMetrics> buffer;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /** Create a collector with the default capacity of 1000 records. */
    public AgentMetricsCollector() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Create a collector with a custom capacity.
     *
     * @param capacity maximum number of records to retain
     */
    public AgentMetricsCollector(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
        this.buffer = new ArrayDeque<>(Math.min(capacity, 256));
    }

    /**
     * Record a completed session's metrics.
     *
     * <p>If the buffer is at capacity the oldest record is evicted.
     */
    public void record(AgentSessionMetrics metrics) {
        if (metrics == null) return;
        lock.writeLock().lock();
        try {
            if (buffer.size() >= capacity) {
                buffer.pollFirst();
            }
            buffer.addLast(metrics);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Return the most recent {@code n} records, newest last.
     *
     * @param n maximum number of records to return
     */
    public List<AgentSessionMetrics> getRecent(int n) {
        lock.readLock().lock();
        try {
            List<AgentSessionMetrics> all = new ArrayList<>(buffer);
            int from = Math.max(0, all.size() - n);
            return List.copyOf(all.subList(from, all.size()));
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Return aggregate statistics across all buffered records. */
    public AgentMetricsSummary getSummary() {
        lock.readLock().lock();
        try {
            if (buffer.isEmpty()) {
                return new AgentMetricsSummary(0, 0, 0.0, 0.0, 0.0);
            }
            long totalInvocations = buffer.size();
            long successCount = buffer.stream().filter(AgentSessionMetrics::succeeded).count();
            double avgTokens =
                    buffer.stream()
                            .mapToLong(AgentSessionMetrics::totalTokensUsed)
                            .average()
                            .orElse(0.0);
            double avgIterations =
                    buffer.stream()
                            .mapToInt(AgentSessionMetrics::totalIterations)
                            .average()
                            .orElse(0.0);
            double successRate = (double) successCount / totalInvocations;
            return new AgentMetricsSummary(
                    totalInvocations, successCount, avgTokens, avgIterations, successRate);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Total number of records currently buffered. */
    public int size() {
        lock.readLock().lock();
        try {
            return buffer.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Aggregate statistics across all buffered {@link AgentSessionMetrics} records.
     *
     * @param totalInvocations number of sessions recorded
     * @param successCount number of successful sessions
     * @param avgTokensUsed average tokens per session
     * @param avgIterations average iterations per session
     * @param successRate fraction of sessions that succeeded (0.0–1.0)
     */
    public record AgentMetricsSummary(
            long totalInvocations,
            long successCount,
            double avgTokensUsed,
            double avgIterations,
            double successRate) {}
}
