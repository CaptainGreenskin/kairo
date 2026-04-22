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

/** Base exception for model provider errors such as rate limiting, timeouts, and API failures. */
public class ModelException extends KairoException {

    /**
     * Create a new ModelException with the given message.
     *
     * @param message the detail message
     */
    public ModelException(String message) {
        this(message, null, null, false, null);
    }

    /**
     * Create a new ModelException with the given message and cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public ModelException(String message, Throwable cause) {
        this(message, cause, null, false, null);
    }

    /**
     * Create a new ModelException with all structured error fields.
     *
     * @param message the detail message
     * @param cause the underlying cause (may be null)
     * @param errorCode machine-readable error code
     * @param retryable whether the operation is retryable
     * @param retryAfterMs suggested retry delay in milliseconds
     */
    protected ModelException(
            String message,
            Throwable cause,
            String errorCode,
            boolean retryable,
            Long retryAfterMs) {
        super(message, cause, errorCode, ErrorCategory.MODEL, retryable, retryAfterMs);
    }
}
