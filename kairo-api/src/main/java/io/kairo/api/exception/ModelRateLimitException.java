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

/** Thrown when a model provider returns a rate-limit (HTTP 429) error. */
@Stable(value = "Model rate-limit exception; shape frozen since v0.7", since = "1.0.0")
public class ModelRateLimitException extends ModelException {

    private static final String DEFAULT_ERROR_CODE = "MODEL_RATE_LIMITED";

    /**
     * Create a new ModelRateLimitException with the given message.
     *
     * @param message the detail message
     */
    public ModelRateLimitException(String message) {
        super(message, null, DEFAULT_ERROR_CODE, true, null);
    }

    /**
     * Create a new ModelRateLimitException with the given message and cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public ModelRateLimitException(String message, Throwable cause) {
        super(message, cause, DEFAULT_ERROR_CODE, true, null);
    }

    /**
     * Create a new ModelRateLimitException with the given message, cause, and retry delay.
     *
     * @param message the detail message
     * @param cause the underlying cause
     * @param retryAfterMs suggested retry delay in milliseconds, or null if not provided
     */
    public ModelRateLimitException(String message, Throwable cause, Long retryAfterMs) {
        super(message, cause, DEFAULT_ERROR_CODE, true, retryAfterMs);
    }
}
