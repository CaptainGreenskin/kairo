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

/** Classification of API errors for retry and recovery decisions. */
@Stable(value = "API error type enum; values frozen since v0.2", since = "1.0.0")
public enum ApiErrorType {

    /** The prompt exceeds the model's context window. */
    PROMPT_TOO_LONG,

    /** The response was truncated due to max output token limits. */
    MAX_OUTPUT_TOKENS,

    /** The API rate limit was exceeded. */
    RATE_LIMITED,

    /** The API server returned an internal error. */
    SERVER_ERROR,

    /** Authentication or authorization failed. */
    AUTHENTICATION_ERROR,

    /** The usage budget has been exceeded. */
    BUDGET_EXCEEDED,

    /** An unknown or unclassified error. */
    UNKNOWN
}
