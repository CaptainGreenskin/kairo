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

import io.kairo.api.tracing.Span;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import java.util.Map;

/**
 * OpenTelemetry-backed implementation of the Kairo {@link Span} interface.
 *
 * <p>Package-private — users create spans via {@link OTelTracer}, not directly.
 * Kairo semantic attribute keys are mapped to OpenTelemetry GenAI semantic
 * convention attribute keys before being set on the underlying OTel span.
 */
class OTelSpan implements Span {

    // Kairo key → OTel attribute key mapping
    private static final Map<String, String> ATTRIBUTE_KEY_MAP = Map.ofEntries(
            Map.entry("token.input", "gen_ai.usage.input_tokens"),
            Map.entry("token.output", "gen_ai.usage.output_tokens"),
            Map.entry("token.cache_read", "gen_ai.usage.cache_read_tokens"),
            Map.entry("token.cache_write", "gen_ai.usage.cache_creation_tokens"),
            Map.entry("tool.name", "gen_ai.tool.name"),
            Map.entry("tool.success", "gen_ai.tool.success"),
            Map.entry("tool.duration_ms", "gen_ai.tool.duration_ms"),
            Map.entry("exception.type", "exception.type"),
            Map.entry("exception.message", "exception.message"),
            Map.entry("compaction.strategy", "gen_ai.compaction.strategy"),
            Map.entry("compaction.tokens_saved", "gen_ai.compaction.tokens_saved"));

    private final io.opentelemetry.api.trace.Span otelSpan;
    private final String spanName;
    private final OTelSpan parentSpan;

    OTelSpan(io.opentelemetry.api.trace.Span otelSpan, String name, OTelSpan parent) {
        this.otelSpan = otelSpan;
        this.spanName = name;
        this.parentSpan = parent;
    }

    /** Returns the underlying OTel span for context propagation. */
    io.opentelemetry.api.trace.Span otelSpan() {
        return otelSpan;
    }

    @Override
    public String spanId() {
        return otelSpan.getSpanContext().getSpanId();
    }

    @Override
    public String name() {
        return spanName;
    }

    @Override
    public Span parent() {
        return parentSpan;
    }

    @Override
    public void setAttribute(String key, Object value) {
        if (key == null || value == null) {
            return;
        }
        String otelKey = ATTRIBUTE_KEY_MAP.getOrDefault(key, key);
        if (value instanceof Long longVal) {
            otelSpan.setAttribute(AttributeKey.longKey(otelKey), longVal);
        } else if (value instanceof Integer intVal) {
            otelSpan.setAttribute(AttributeKey.longKey(otelKey), intVal.longValue());
        } else if (value instanceof Double doubleVal) {
            otelSpan.setAttribute(AttributeKey.doubleKey(otelKey), doubleVal);
        } else if (value instanceof Boolean boolVal) {
            otelSpan.setAttribute(AttributeKey.booleanKey(otelKey), boolVal);
        } else {
            otelSpan.setAttribute(AttributeKey.stringKey(otelKey), value.toString());
        }
    }

    @Override
    public void setStatus(boolean success, String message) {
        if (success) {
            otelSpan.setStatus(StatusCode.OK, message != null ? message : "");
        } else {
            otelSpan.setStatus(StatusCode.ERROR, message != null ? message : "");
        }
    }

    @Override
    public void end() {
        otelSpan.end();
    }
}
