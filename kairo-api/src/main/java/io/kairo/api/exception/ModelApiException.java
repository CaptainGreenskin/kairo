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
 * Thrown when a model provider API returns an error (non-200 HTTP status, response parse failure).
 */
@Stable(value = "Model API exception; shape frozen since v0.7", since = "1.0.0")
public class ModelApiException extends ModelException {

    private static final String DEFAULT_ERROR_CODE = "MODEL_API_ERROR";

    /**
     * Create a new ModelApiException with the given message.
     *
     * @param message the detail message
     */
    public ModelApiException(String message) {
        super(message, null, DEFAULT_ERROR_CODE, false, null);
    }

    /**
     * Create a new ModelApiException with the given message and cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public ModelApiException(String message, Throwable cause) {
        super(message, cause, DEFAULT_ERROR_CODE, false, null);
    }
}
