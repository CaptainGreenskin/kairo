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

import io.kairo.api.message.Msg;
import java.time.Duration;
import java.util.Map;

/**
 * Tracing interface for observing Agent execution. Uses a Span model aligned with OpenTelemetry
 * semantics for easy bridging in v0.3.0.
 *
 * <p>Span factory methods create parent-child spans. Business convenience methods
 * (recordTokenUsage, recordToolResult, recordCompaction) delegate to span.setAttribute with
 * semantic convention keys.
 */
public interface Tracer {

    // --- Span factories (override these) ---

    default Span startAgentSpan(String agentName, Msg input) {
        return NoopSpan.INSTANCE;
    }

    default Span startIterationSpan(Span parent, int iteration) {
        return NoopSpan.INSTANCE;
    }

    default Span startReasoningSpan(Span parent, String modelName, int messageCount) {
        return NoopSpan.INSTANCE;
    }

    default Span startToolSpan(Span parent, String toolName, Map<String, Object> input) {
        return NoopSpan.INSTANCE;
    }

    // --- Business convenience methods (use setAttribute internally) ---

    default void recordTokenUsage(Span span, int input, int output, int cacheRead, int cacheWrite) {
        span.setAttribute("token.input", input);
        span.setAttribute("token.output", output);
        span.setAttribute("token.cache_read", cacheRead);
        span.setAttribute("token.cache_write", cacheWrite);
    }

    default void recordToolResult(Span span, String toolName, boolean success, Duration duration) {
        span.setAttribute("tool.name", toolName);
        span.setAttribute("tool.success", success);
        span.setAttribute("tool.duration_ms", duration.toMillis());
    }

    default void recordCompaction(Span span, String strategy, int tokensSaved) {
        span.setAttribute("compaction.strategy", strategy);
        span.setAttribute("compaction.tokens_saved", tokensSaved);
    }

    /** Record an exception on the given span, categorized by exception type. */
    default void recordException(Span span, Throwable exception) {
        span.setAttribute("exception.type", exception.getClass().getName());
        span.setAttribute("exception.message", exception.getMessage());
        span.setStatus(false, exception.getMessage());
    }
}
