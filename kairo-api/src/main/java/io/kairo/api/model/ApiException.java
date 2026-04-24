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
import java.util.Map;

/** An exception representing a classified API error. */
@Stable(value = "Classified API exception; shape frozen since v0.2", since = "1.0.0")
public class ApiException extends RuntimeException {

    private final ApiErrorType errorType;
    private final Map<String, Object> metadata;

    /**
     * Create a new API exception.
     *
     * @param errorType the classified error type
     * @param message the error message
     * @param metadata additional metadata about the error
     */
    public ApiException(ApiErrorType errorType, String message, Map<String, Object> metadata) {
        super(message);
        this.errorType = errorType;
        this.metadata = metadata;
    }

    /** Return the classified error type. */
    public ApiErrorType getErrorType() {
        return errorType;
    }

    /** Return additional metadata about the error. */
    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
