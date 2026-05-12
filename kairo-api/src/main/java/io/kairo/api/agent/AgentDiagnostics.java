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
package io.kairo.api.agent;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only diagnostics snapshot for a running agent session.
 *
 * <p>Scope: per-session — one {@code Agent.call()} invocation corresponds to one AgentDiagnostics
 * lifecycle. A new instance is created at session start and becomes invalid (returns stale data)
 * after session terminal event.
 *
 * @since 1.2.0
 */
public interface AgentDiagnostics {

    /** Whether the agent is currently executing (not yet terminal). */
    boolean running();

    /** Timestamp of last meaningful event emission. */
    Instant lastEventAt();

    /** Milliseconds since last event (derived: now - lastEventAt). */
    long msSinceLastEvent();

    /** Per-event-type counters for debugging emission anomalies (double-fire, missing). */
    Map<String, Long> eventCounts();

    /** Snapshot of the currently executing tool, if any. */
    Optional<ToolInvocationSnapshot> activeToolInvocation();

    /** Cumulative token consumption. */
    long totalTokensConsumed();

    /** Current iteration number (1-based). */
    int currentIteration();

    /** Active trace ID for linking to Langfuse/OTel UI. Empty if tracing disabled. */
    Optional<String> traceId();

    /** Active span ID of the current iteration. Empty if tracing disabled. */
    Optional<String> currentSpanId();

    /** Snapshot of an in-flight tool invocation. */
    record ToolInvocationSnapshot(String toolUseId, String toolName, Instant startedAt) {}
}
