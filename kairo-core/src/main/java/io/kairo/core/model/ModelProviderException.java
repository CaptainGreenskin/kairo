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
package io.kairo.core.model;

/**
 * Shared exception types for model providers (Anthropic, OpenAI, etc.).
 *
 * <p>Previously these were inner classes of {@code AnthropicProvider}; they are now shared so that
 * all providers use the same exception hierarchy for retry logic and error classification.
 */
public final class ModelProviderException {

    private ModelProviderException() {} // prevent instantiation

    /** Thrown when the API returns a 429 rate limit response. */
    public static class RateLimitException extends RuntimeException {
        private final Long retryAfterSeconds;

        public RateLimitException(String message, Long retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        /** Server-suggested retry delay in seconds, or null if not provided. */
        public Long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    /** General API error for non-2xx responses or response parse failures. */
    public static class ApiException extends RuntimeException {
        public ApiException(String message) {
            super(message);
        }

        public ApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
