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
    // Depth counter so parallel/nested tool calls don't let the first clearActiveTool()
    // prematurely clear while siblings are still running — only the last clear resets state.
    private final AtomicInteger activeToolDepth = new AtomicInteger(0);
    private volatile long activeToolStartedAtMs = 0;
    private volatile boolean activeModelCall = false;
    private volatile long modelCallStartMs = 0;
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
        ToolInvocationSnapshot snap = activeTool.get();
        if (snap == null) return Optional.empty();
        // Self-heal: if a tool has been "active" past the hard limit, presume clearActiveTool()
        // was missed (e.g. a reactive chain that never terminated so doFinally never fired) and
        // clear it — otherwise the StallDetector would stay suppressed forever and a genuinely
        // hung tool would only be caught by the 4h overall timeout.
        if (activeToolStartedAtMs > 0
                && System.currentTimeMillis() - activeToolStartedAtMs > activeToolHardLimitMs) {
            activeToolDepth.set(0);
            activeTool.set(null);
            activeToolStartedAtMs = 0;
            return Optional.empty();
        }
        return Optional.of(snap);
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
        activeToolStartedAtMs = System.currentTimeMillis();
        activeToolDepth.incrementAndGet();
        recordEvent("tool_call_start");
    }

    @Override
    public void clearActiveTool() {
        if (activeToolDepth.decrementAndGet() <= 0) {
            activeToolDepth.set(0);
            activeTool.set(null);
            activeToolStartedAtMs = 0;
            // Refresh the stall clock so a long tool that just finished doesn't immediately trip
            // the StallDetector before the next model call has a chance to start.
            recordEvent("tool_call_end");
        }
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

    private static final long MODEL_CALL_HARD_LIMIT_MS = 600_000L; // 10 minutes

    // Backstop for a leaked activeTool (missed clearActiveTool()). Generous by default — longer
    // than any typical tool/build so it never false-clears a legitimately long tool; it only fires
    // when a clear was genuinely lost. Raise via KAIRO_ACTIVE_TOOL_HARD_LIMIT_MS for multi-hour
    // subagents. This is a bug-backstop, not a policy timeout — real per-tool limits live in
    // DefaultToolExecutor.
    private static final long ACTIVE_TOOL_HARD_LIMIT_MS = resolveActiveToolHardLimit();

    // Instance-level copy so tests can shrink it without waiting out the 30-minute default.
    private volatile long activeToolHardLimitMs = ACTIVE_TOOL_HARD_LIMIT_MS;

    /**
     * Package-private override for tests — shrink the self-heal window to assert it
     * deterministically.
     */
    void setActiveToolHardLimitMs(long ms) {
        this.activeToolHardLimitMs = ms;
    }

    private static long resolveActiveToolHardLimit() {
        String env = System.getenv("KAIRO_ACTIVE_TOOL_HARD_LIMIT_MS");
        if (env != null && !env.isBlank()) {
            try {
                long parsed = Long.parseLong(env.trim());
                if (parsed > 0) return parsed;
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return 1_800_000L; // 30 minutes
    }

    boolean isActiveModelCall() {
        if (!activeModelCall) return false;
        if (modelCallStartMs > 0
                && System.currentTimeMillis() - modelCallStartMs > MODEL_CALL_HARD_LIMIT_MS) {
            activeModelCall = false;
            return false;
        }
        return true;
    }

    void setActiveModelCall(boolean active) {
        this.activeModelCall = active;
        this.modelCallStartMs = active ? System.currentTimeMillis() : 0;
        if (active) {
            recordEvent("model_call_start");
        }
    }
}
