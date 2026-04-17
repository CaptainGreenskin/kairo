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
package io.kairo.api.tracing;

import java.util.Map;

/**
 * Represents a unit of work in a trace. Designed to map 1:1 to OpenTelemetry Span for zero-adapter
 * bridging in v0.3.0.
 *
 * <p>Span only has generic operations (setAttribute, setStatus, end). Business-specific recording
 * (tokens, tools, compaction) lives on {@link Tracer} as convenience methods that delegate to
 * setAttribute.
 */
public interface Span {
    String spanId();

    String name();

    Span parent();

    void setAttribute(String key, Object value);

    void setStatus(boolean success, String message);

    void end();

    /**
     * Record a timestamped event on this span.
     * Events enable deterministic replay by capturing full input/output snapshots.
     *
     * @param name event name (e.g., "model.request", "tool.result", "compaction.before")
     * @param attributes event attributes (key-value pairs)
     */
    default void addEvent(String name, Map<String, Object> attributes) {
        // no-op by default — implementations override
    }

    /**
     * Record a simple named event without attributes.
     *
     * @param name event name
     */
    default void addEvent(String name) {
        addEvent(name, Map.of());
    }
}
