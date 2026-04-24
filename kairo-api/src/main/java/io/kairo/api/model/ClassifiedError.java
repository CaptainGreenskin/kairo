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
package io.kairo.api.model;

import io.kairo.api.Stable;
import java.time.Duration;
import java.util.Map;

/**
 * A classified API error with retry metadata.
 *
 * @param type the error classification
 * @param message the error message
 * @param retryAfter suggested retry delay, or null (only meaningful for {@link
 *     ApiErrorType#RATE_LIMITED})
 * @param metadata additional metadata about the error
 */
@Stable(value = "Classified API error record; shape frozen since v0.2", since = "1.0.0")
public record ClassifiedError(
        ApiErrorType type, String message, Duration retryAfter, Map<String, Object> metadata) {

    /**
     * Convert this classified error to a runtime exception.
     *
     * @return a new {@link ApiException}
     */
    public RuntimeException toException() {
        return new ApiException(type, message, metadata);
    }
}
