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
 * <pre>{@code
 * try {
 *     agent.call(input).block();
 * } catch (KairoException e) {
 *     log.error("Agent failed: {}", e.getMessage(), e);
 * }
 * }</pre>
 */
public class KairoException extends RuntimeException {

    /**
     * Create a new KairoException with the given message.
     *
     * @param message the detail message
     */
    public KairoException(String message) {
        super(message);
    }

    /**
     * Create a new KairoException with the given message and cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public KairoException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Create a new KairoException with the given cause.
     *
     * @param cause the underlying cause
     */
    public KairoException(Throwable cause) {
        super(cause);
    }
}
