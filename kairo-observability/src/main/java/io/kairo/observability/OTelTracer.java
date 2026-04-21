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

import io.kairo.api.message.Msg;
import io.kairo.api.tracing.Span;
import io.kairo.api.tracing.Tracer;
import io.opentelemetry.context.Context;
import java.util.Map;
import java.util.Objects;

/**
 * OpenTelemetry-backed implementation of the Kairo {@link Tracer} interface.
 *
 * <p>Wraps an {@link io.opentelemetry.api.trace.Tracer} to produce real OTel spans with proper
 * parent-child relationships, enabling export to any OTel-compatible backend (Jaeger, Zipkin, OTLP
 * collectors, etc.).
 *
 * <p>Span factory methods create {@link OTelSpan} instances that map Kairo semantic attribute keys
 * to OpenTelemetry GenAI semantic conventions. Business convenience methods ({@link
 * #recordTokenUsage}, {@link #recordToolResult}, {@link #recordCompaction}) use the default
 * implementations from the {@link Tracer} interface, which delegate to {@link Span#setAttribute}.
 *
 * <p><strong>Thread safety:</strong> This class is safe for concurrent use. The underlying OTel
 * tracer and span operations are thread-safe by contract.
 *
 * <p><strong>Usage example:</strong>
 *
 * <pre>{@code
 * OpenTelemetry otel = GlobalOpenTelemetry.get();
 * Tracer tracer = new OTelTracer(otel.getTracer("kairo"));
 * Span agentSpan = tracer.startAgentSpan("my-agent", input);
 * // ... agent loop ...
 * agentSpan.end();
 * }</pre>
 *
 * @see OTelSpan
 * @see Tracer
 */
public class OTelTracer implements Tracer {

    private final io.opentelemetry.api.trace.Tracer otelTracer;

    /**
     * Creates a new OTelTracer wrapping the given OpenTelemetry tracer.
     *
     * @param otelTracer the OpenTelemetry tracer to delegate span creation to; must not be null
     * @throws NullPointerException if {@code otelTracer} is null
     */
    public OTelTracer(io.opentelemetry.api.trace.Tracer otelTracer) {
        this.otelTracer = Objects.requireNonNull(otelTracer, "otelTracer must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Creates a root OTel span named {@code "agent:<agentName>"} with the {@code agent.name}
     * attribute set.
     */
    @Override
    public Span startAgentSpan(String agentName, Msg input) {
        io.opentelemetry.api.trace.Span otelSpan =
                otelTracer.spanBuilder("agent:" + agentName).startSpan();
        OTelSpan span = new OTelSpan(otelSpan, "agent:" + agentName, null);
        span.setAttribute("agent.name", agentName);
        return span;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Creates a child OTel span named {@code "iteration-<n>"} under the given parent and records
     * the iteration index as {@code agent.iteration} for downstream GenAI semconv mapping.
     */
    @Override
    public Span startIterationSpan(Span parent, int iteration) {
        String spanName = "iteration-" + iteration;
        OTelSpan span = (OTelSpan) startChildSpan(parent, spanName);
        span.setAttribute("agent.iteration", (long) iteration);
        return span;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Creates a child OTel span named {@code "reasoning:<modelName>"} under the given parent,
     * with the {@code message.count} attribute set (mapped to {@code gen_ai.request.message_count}
     * by the Kairo → GenAI key registry).
     */
    @Override
    public Span startReasoningSpan(Span parent, String modelName, int messageCount) {
        String spanName = "reasoning:" + modelName;
        OTelSpan span = (OTelSpan) startChildSpan(parent, spanName);
        span.setAttribute("message.count", messageCount);
        return span;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Creates a child OTel span named {@code "tool:<toolName>"} under the given parent.
     */
    @Override
    public Span startToolSpan(Span parent, String toolName, Map<String, Object> input) {
        return startChildSpan(parent, "tool:" + toolName);
    }

    /** Creates a child OTel span linked to the given parent span via OTel context propagation. */
    private Span startChildSpan(Span parent, String spanName) {
        OTelSpan otelParent = (parent instanceof OTelSpan) ? (OTelSpan) parent : null;
        io.opentelemetry.api.trace.Span otelSpan;
        if (otelParent != null) {
            Context parentContext = Context.current().with(otelParent.otelSpan());
            otelSpan = otelTracer.spanBuilder(spanName).setParent(parentContext).startSpan();
        } else {
            otelSpan = otelTracer.spanBuilder(spanName).startSpan();
        }
        return new OTelSpan(otelSpan, spanName, otelParent);
    }
}
