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

import io.kairo.api.agent.AgentDiagnostics;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link AgentDiagnostics} providing both read (public SPI) and write
 * (package-private {@link MutableDiagnostics}) access.
 *
 * <p>Thread-safe: all fields are either volatile or atomic, allowing concurrent reads from
 * monitoring threads while the agent loop writes during execution.
 */
final class DefaultAgentDiagnostics implements AgentDiagnostics, MutableDiagnostics {

    private volatile boolean running = true;
    private final AtomicLong lastEventAtEpochMs = new AtomicLong(System.currentTimeMillis());
    private final ConcurrentHashMap<String, AtomicLong> eventCountsMap = new ConcurrentHashMap<>();
    private final AtomicReference<ToolInvocationSnapshot> activeTool = new AtomicReference<>();
    private volatile String traceId;
    private volatile String currentSpanId;
    // Token + iteration counters are owned by DefaultReActAgent / ReActLoop /
    // ReasoningPhase — by delegating reads to those shared atomics we get live
    // values without having to wire setX calls into every mutation site.
    private final AtomicLong totalTokens;
    private final AtomicInteger currentIteration;

    DefaultAgentDiagnostics() {
        this(new AtomicLong(0), new AtomicInteger(0));
    }

    DefaultAgentDiagnostics(AtomicLong totalTokens, AtomicInteger currentIteration) {
        this.totalTokens = totalTokens;
        this.currentIteration = currentIteration;
    }

    // ---- AgentDiagnostics (read) ----

    @Override
    public boolean running() {
        return running;
    }

    @Override
    public Instant lastEventAt() {
        return Instant.ofEpochMilli(lastEventAtEpochMs.get());
    }

    @Override
    public long msSinceLastEvent() {
        return System.currentTimeMillis() - lastEventAtEpochMs.get();
    }

    @Override
    public Map<String, Long> eventCounts() {
        return eventCountsMap.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> e.getValue().get()));
    }

    @Override
    public Optional<ToolInvocationSnapshot> activeToolInvocation() {
        return Optional.ofNullable(activeTool.get());
    }

    @Override
    public long totalTokensConsumed() {
        return totalTokens.get();
    }

    @Override
    public int currentIteration() {
        return currentIteration.get();
    }

    @Override
    public Optional<String> traceId() {
        return Optional.ofNullable(traceId);
    }

    @Override
    public Optional<String> currentSpanId() {
        return Optional.ofNullable(currentSpanId);
    }

    // ---- MutableDiagnostics (write) ----

    @Override
    public void recordEvent(String eventType) {
        lastEventAtEpochMs.set(System.currentTimeMillis());
        eventCountsMap.computeIfAbsent(eventType, k -> new AtomicLong()).incrementAndGet();
    }

    @Override
    public void setActiveTool(ToolInvocationSnapshot snapshot) {
        activeTool.set(snapshot);
    }

    @Override
    public void clearActiveTool() {
        activeTool.set(null);
    }

    @Override
    public void setRunning(boolean running) {
        this.running = running;
    }

    @Override
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    @Override
    public void setCurrentSpanId(String spanId) {
        this.currentSpanId = spanId;
    }

    @Override
    public void setTotalTokens(long tokens) {
        this.totalTokens.set(tokens);
    }

    @Override
    public void setCurrentIteration(int iteration) {
        this.currentIteration.set(iteration);
    }
}
