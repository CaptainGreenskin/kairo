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
package io.kairo.core.tracing;

import io.kairo.api.message.Msg;
import io.kairo.api.tracing.ObservationData;
import io.kairo.api.tracing.Span;
import io.kairo.api.tracing.Tracer;
import java.time.Duration;
import java.util.Map;

/**
 * Decorator that stamps session and user identifiers on every span.
 *
 * <p>Covers OpenTelemetry GenAI ({@code session.id}) and Langfuse ({@code langfuse.session.id},
 * {@code langfuse.user.id}) conventions so spans from multi-turn sessions group correctly in
 * dashboards.
 */
public final class SessionAwareTracer implements Tracer {

    private final Tracer delegate;
    private final String sessionId;
    private final String userId;

    public SessionAwareTracer(Tracer delegate, String sessionId) {
        this(delegate, sessionId, null);
    }

    public SessionAwareTracer(Tracer delegate, String sessionId, String userId) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate Tracer required");
        }
        this.delegate = delegate;
        this.sessionId = sessionId;
        this.userId = userId;
    }

    private Span stamp(Span span) {
        if (span == null) return span;
        if (sessionId != null) {
            span.setAttribute("session.id", sessionId);
            span.setAttribute("langfuse.session.id", sessionId);
        }
        if (userId != null) {
            span.setAttribute("user.id", userId);
            span.setAttribute("langfuse.user.id", userId);
        }
        return span;
    }

    @Override
    public Span startAgentSpan(String agentName, Msg input) {
        return stamp(delegate.startAgentSpan(agentName, input));
    }

    @Override
    public Span startIterationSpan(Span parent, int iteration) {
        return stamp(delegate.startIterationSpan(parent, iteration));
    }

    @Override
    public Span startReasoningSpan(Span parent, String modelName, int messageCount) {
        return stamp(delegate.startReasoningSpan(parent, modelName, messageCount));
    }

    @Override
    public Span startToolSpan(Span parent, String toolName, Map<String, Object> input) {
        return stamp(delegate.startToolSpan(parent, toolName, input));
    }

    @Override
    public Span startHookSpan(Span parent, String phase, String hookName) {
        return stamp(delegate.startHookSpan(parent, phase, hookName));
    }

    @Override
    public Span startGuardrailSpan(Span parent, String policyName, String phase) {
        return stamp(delegate.startGuardrailSpan(parent, policyName, phase));
    }

    @Override
    public void recordTokenUsage(Span span, int input, int output, int cacheRead, int cacheWrite) {
        delegate.recordTokenUsage(span, input, output, cacheRead, cacheWrite);
    }

    @Override
    public void recordToolResult(Span span, String toolName, boolean success, Duration duration) {
        delegate.recordToolResult(span, toolName, success, duration);
    }

    @Override
    public void recordCompaction(Span span, String strategy, int tokensSaved) {
        delegate.recordCompaction(span, strategy, tokensSaved);
    }

    @Override
    public void recordException(Span span, Throwable exception) {
        delegate.recordException(span, exception);
    }

    @Override
    public void recordObservation(Span span, ObservationData data) {
        delegate.recordObservation(span, data);
    }
}
