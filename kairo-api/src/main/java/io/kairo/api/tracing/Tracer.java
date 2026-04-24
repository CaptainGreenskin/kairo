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

import io.kairo.api.Stable;
import io.kairo.api.message.Msg;
import java.time.Duration;
import java.util.Map;

/**
 * Tracing interface for observing agent execution.
 *
 * <p>Uses a {@link Span} model aligned with OpenTelemetry semantics for easy bridging to production
 * observability backends. Span factory methods create parent-child relationships that mirror the
 * agent's reasoning/acting loop structure.
 *
 * <p>Business convenience methods ({@link #recordTokenUsage}, {@link #recordToolResult}, {@link
 * #recordCompaction}) delegate to {@link Span#setAttribute} with semantic convention keys, so
 * implementations only need to override the span factories.
 *
 * <p>The default implementation returns {@link NoopSpan#INSTANCE} for all span factories, making
 * this interface safe to use without a tracing backend configured.
 *
 * <p><strong>Thread safety:</strong> Implementations must be safe for concurrent use; spans from
 * parallel tool executions may be recorded simultaneously.
 *
 * @see Span
 * @see NoopSpan
 */
@Stable(value = "Tracer SPI; OTel-aligned shape frozen since v0.3", since = "1.0.0")
public interface Tracer {

    // --- Span factories (override these) ---

    /**
     * Start a root span for an agent invocation.
     *
     * @param agentName the name of the agent being traced
     * @param input the input message that triggered the agent
     * @return a new {@link Span} representing the agent execution, never {@code null}
     */
    default Span startAgentSpan(String agentName, Msg input) {
        return NoopSpan.INSTANCE;
    }

    /**
     * Start a child span for a single iteration of the agent loop.
     *
     * @param parent the parent span (typically the agent span)
     * @param iteration the zero-based iteration index
     * @return a new child {@link Span}, never {@code null}
     */
    default Span startIterationSpan(Span parent, int iteration) {
        return NoopSpan.INSTANCE;
    }

    /**
     * Start a child span for a model reasoning call.
     *
     * @param parent the parent span (typically the iteration span)
     * @param modelName the model identifier used for this call
     * @param messageCount the number of messages sent to the model
     * @return a new child {@link Span}, never {@code null}
     */
    default Span startReasoningSpan(Span parent, String modelName, int messageCount) {
        return NoopSpan.INSTANCE;
    }

    /**
     * Start a child span for a tool execution.
     *
     * @param parent the parent span (typically the iteration span)
     * @param toolName the name of the tool being executed
     * @param input the tool input parameters
     * @return a new child {@link Span}, never {@code null}
     */
    default Span startToolSpan(Span parent, String toolName, Map<String, Object> input) {
        return NoopSpan.INSTANCE;
    }

    // --- Business convenience methods (use setAttribute internally) ---

    /**
     * Record token usage metrics on a span.
     *
     * @param span the span to annotate
     * @param input the number of input tokens consumed
     * @param output the number of output tokens generated
     * @param cacheRead the number of tokens read from cache
     * @param cacheWrite the number of tokens written to cache
     */
    default void recordTokenUsage(Span span, int input, int output, int cacheRead, int cacheWrite) {
        span.setAttribute("token.input", input);
        span.setAttribute("token.output", output);
        span.setAttribute("token.cache_read", cacheRead);
        span.setAttribute("token.cache_write", cacheWrite);
    }

    /**
     * Record the outcome of a tool execution on a span.
     *
     * @param span the span to annotate
     * @param toolName the name of the executed tool
     * @param success {@code true} if the tool completed successfully
     * @param duration the wall-clock duration of the tool execution
     */
    default void recordToolResult(Span span, String toolName, boolean success, Duration duration) {
        span.setAttribute("tool.name", toolName);
        span.setAttribute("tool.success", success);
        span.setAttribute("tool.duration_ms", duration.toMillis());
    }

    /**
     * Record context compaction metrics on a span.
     *
     * @param span the span to annotate
     * @param strategy the compaction strategy used (e.g. "summarize", "truncate")
     * @param tokensSaved the number of tokens reclaimed by compaction
     */
    default void recordCompaction(Span span, String strategy, int tokensSaved) {
        span.setAttribute("compaction.strategy", strategy);
        span.setAttribute("compaction.tokens_saved", tokensSaved);
    }

    /**
     * Record an exception on the given span, categorized by exception type.
     *
     * @param span the span to annotate
     * @param exception the exception to record
     */
    default void recordException(Span span, Throwable exception) {
        span.setAttribute("exception.type", exception.getClass().getName());
        span.setAttribute("exception.message", exception.getMessage());
        span.setStatus(false, exception.getMessage());
    }
}
