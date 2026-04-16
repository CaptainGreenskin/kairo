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
import io.kairo.api.tracing.Span;
import io.kairo.api.tracing.Tracer;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Tracer} implementation that logs spans as structured messages via SLF4J.
 *
 * <p>Each span records its name, parent, duration, status, and attributes. On {@link Span#end()},
 * the span is logged at INFO level with all collected data.
 */
public class StructuredLogTracer implements Tracer {
    private static final Logger log = LoggerFactory.getLogger(StructuredLogTracer.class);

    @Override
    public Span startAgentSpan(String agentName, Msg input) {
        return new StructuredLogSpan("agent", agentName, null);
    }

    @Override
    public Span startIterationSpan(Span parent, int iteration) {
        return new StructuredLogSpan("iteration", "iteration-" + iteration, parent);
    }

    @Override
    public Span startReasoningSpan(Span parent, String modelName, int messageCount) {
        StructuredLogSpan span = new StructuredLogSpan("reasoning", modelName, parent);
        span.setAttribute("message_count", messageCount);
        return span;
    }

    @Override
    public Span startToolSpan(Span parent, String toolName, Map<String, Object> input) {
        return new StructuredLogSpan("tool", toolName, parent);
    }

    /** A Span implementation that collects attributes and logs them on {@link #end()}. */
    static class StructuredLogSpan implements Span {
        private final String id;
        private final String spanName;
        private final Span parentSpan;
        private final Instant startTime;
        private final Map<String, Object> attributes = new ConcurrentHashMap<>();
        private boolean success = true;
        private String statusMessage;

        StructuredLogSpan(String type, String name, Span parent) {
            this.id = UUID.randomUUID().toString().substring(0, 8);
            this.spanName = type + ":" + name;
            this.parentSpan = parent;
            this.startTime = Instant.now();
        }

        @Override
        public String spanId() {
            return id;
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
            if (key != null && value != null) {
                attributes.put(key, value);
            }
        }

        @Override
        public void setStatus(boolean success, String message) {
            this.success = success;
            this.statusMessage = message;
        }

        @Override
        public void end() {
            Duration duration = Duration.between(startTime, Instant.now());
            log.info(
                    "[Span] {} | id={} | parent={} | duration={}ms | status={} | attrs={}",
                    spanName,
                    id,
                    parentSpan != null ? parentSpan.spanId() : "root",
                    duration.toMillis(),
                    success
                            ? "OK"
                            : "ERROR" + (statusMessage != null ? ":" + statusMessage : ""),
                    attributes.isEmpty() ? "{}" : attributes);
        }
    }
}
