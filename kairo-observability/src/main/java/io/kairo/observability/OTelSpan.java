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
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.StatusCode;
import java.util.Map;

/**
 * OpenTelemetry-backed implementation of the Kairo {@link Span} interface.
 *
 * <p>Package-private — users create spans via {@link OTelTracer}, not directly. Kairo semantic
 * attribute keys are mapped to OpenTelemetry GenAI semantic convention attribute keys before being
 * set on the underlying OTel span.
 */
class OTelSpan implements Span {

    // Kairo short-key → OTel attribute key mapping is the single source of truth in
    // {@link GenAiSemanticAttributes}. This class delegates every lookup so that adding a
    // new Kairo key only requires editing the registry.

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
        // Resolve the canonical OTel AttributeKey from the single registry. Falls back to a
        // string-keyed attribute when the key is unknown so users can still stash arbitrary
        // metadata without registering it.
        AttributeKey<?> canonical = GenAiSemanticAttributes.kairoKeyToOTel(key);
        String otelKey = canonical != null ? canonical.getKey() : key;
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
    public void addEvent(String name, Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            otelSpan.addEvent(name);
        } else {
            var builder = Attributes.builder();
            for (var entry : attributes.entrySet()) {
                Object val = entry.getValue();
                if (val instanceof String s) {
                    builder.put(AttributeKey.stringKey(entry.getKey()), s);
                } else if (val instanceof Long l) {
                    builder.put(AttributeKey.longKey(entry.getKey()), l);
                } else if (val instanceof Integer i) {
                    builder.put(AttributeKey.longKey(entry.getKey()), i.longValue());
                } else if (val instanceof Double d) {
                    builder.put(AttributeKey.doubleKey(entry.getKey()), d);
                } else if (val instanceof Boolean b) {
                    builder.put(AttributeKey.booleanKey(entry.getKey()), b);
                } else {
                    builder.put(AttributeKey.stringKey(entry.getKey()), String.valueOf(val));
                }
            }
            otelSpan.addEvent(name, builder.build());
        }
    }

    @Override
    public void end() {
        otelSpan.end();
    }
}
