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
package io.kairo.api.exception;

import io.kairo.api.Stable;

/**
 * Base unchecked exception for all Kairo framework errors.
 *
 * <p>All domain-specific exceptions in the Kairo framework extend this class, enabling unified
 * catch-and-handle patterns across agent, model, and tool subsystems. Because it extends {@link
 * RuntimeException}, it propagates through reactive pipelines without requiring explicit
 * checked-exception handling.
 *
 * <p>Typical subclasses include model invocation failures, tool execution errors, and context
 * budget violations. Callers that need to distinguish error categories should catch the specific
 * subclass rather than this base type.
 *
 * <p>Structured error fields ({@code errorCode}, {@code category}, {@code retryable}, {@code
 * retryAfterMs}) provide machine-readable metadata for programmatic error routing, observability,
 * and retry decisions.
 *
 * <pre>{@code
 * try {
 *     agent.call(input).block();
 * } catch (KairoException e) {
 *     log.error("Agent failed: {}", e.getMessage(), e);
 * }
 * }</pre>
 */
@Stable(
        value = "Base exception with structured error fields; shape frozen since v0.7",
        since = "1.0.0")
public class KairoException extends RuntimeException {

    private final String errorCode;
    private final ErrorCategory category;
    private final boolean retryable;
    private final Long retryAfterMs;

    /**
     * Create a new KairoException with the given message.
     *
     * @param message the detail message
     */
    public KairoException(String message) {
        super(message);
        this.errorCode = null;
        this.category = null;
        this.retryable = false;
        this.retryAfterMs = null;
    }

    /**
     * Create a new KairoException with the given message and cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public KairoException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
        this.category = null;
        this.retryable = false;
        this.retryAfterMs = null;
    }

    /**
     * Create a new KairoException with the given cause.
     *
     * @param cause the underlying cause
     */
    public KairoException(Throwable cause) {
        super(cause);
        this.errorCode = null;
        this.category = null;
        this.retryable = false;
        this.retryAfterMs = null;
    }

    /**
     * Create a new KairoException with all structured error fields.
     *
     * <p>Intended for use by subclasses that provide domain-specific defaults.
     *
     * @param message the detail message
     * @param cause the underlying cause (may be null)
     * @param errorCode machine-readable error code (e.g. "MODEL_RATE_LIMITED")
     * @param category the error category for routing and metrics
     * @param retryable whether the operation that caused this error is retryable
     * @param retryAfterMs suggested retry delay in milliseconds, or null if not applicable
     */
    protected KairoException(
            String message,
            Throwable cause,
            String errorCode,
            ErrorCategory category,
            boolean retryable,
            Long retryAfterMs) {
        super(message, cause);
        this.errorCode = errorCode;
        this.category = category;
        this.retryable = retryable;
        this.retryAfterMs = retryAfterMs;
    }

    /**
     * Get the machine-readable error code.
     *
     * @return the error code, or null if not set
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Get the error category.
     *
     * @return the category, or null if not set
     */
    public ErrorCategory getCategory() {
        return category;
    }

    /**
     * Check whether the error is retryable.
     *
     * @return true if the operation may be retried
     */
    public boolean isRetryable() {
        return retryable;
    }

    /**
     * Get the suggested retry delay in milliseconds.
     *
     * @return the retry delay in milliseconds, or null if not applicable
     */
    public Long getRetryAfterMs() {
        return retryAfterMs;
    }
}
