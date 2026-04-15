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

import io.kairo.api.model.ApiErrorType;
import io.kairo.api.model.ApiException;
import io.kairo.api.model.ClassifiedError;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Classifies exceptions from model API calls into structured {@link ClassifiedError} instances.
 *
 * <p>Supports both Anthropic and OpenAI error formats by inspecting error messages for known
 * keywords and HTTP status codes.
 */
public class ApiErrorClassifierImpl {

    private static final Pattern RETRY_AFTER_PATTERN =
            Pattern.compile("(?:retry[- ]after:?\\s*)(\\d+)", Pattern.CASE_INSENSITIVE);

    /**
     * Classify the given throwable into a structured {@link ClassifiedError}.
     *
     * @param error the exception thrown by an API call
     * @return a classified error with type, message, and retry metadata
     */
    public ClassifiedError classify(Throwable error) {
        String message = error.getMessage() != null ? error.getMessage() : "";
        String lowerMsg = message.toLowerCase();

        // Already classified
        if (error instanceof ApiException ae) {
            return new ClassifiedError(ae.getErrorType(), ae.getMessage(), null, ae.getMetadata());
        }

        // Prompt too long (Anthropic: "prompt is too long", OpenAI: "context_length_exceeded",
        // "maximum context length")
        if (lowerMsg.contains("prompt is too long")
                || lowerMsg.contains("context_length_exceeded")
                || lowerMsg.contains("maximum context length")
                || lowerMsg.contains("too many tokens")) {
            return new ClassifiedError(ApiErrorType.PROMPT_TOO_LONG, message, null, Map.of());
        }

        // Max output tokens exceeded
        if (lowerMsg.contains("max_tokens") || lowerMsg.contains("maximum output")) {
            return new ClassifiedError(ApiErrorType.MAX_OUTPUT_TOKENS, message, null, Map.of());
        }

        // Rate limited (look for 429 or rate limit keywords)
        if (lowerMsg.contains("rate limit")
                || lowerMsg.contains("429")
                || lowerMsg.contains("too many requests")) {
            Duration retryAfter = extractRetryAfter(message);
            return new ClassifiedError(ApiErrorType.RATE_LIMITED, message, retryAfter, Map.of());
        }

        // Server error (500, 502, 503, 529)
        if (lowerMsg.contains("500")
                || lowerMsg.contains("502")
                || lowerMsg.contains("503")
                || lowerMsg.contains("529")
                || lowerMsg.contains("internal server error")
                || lowerMsg.contains("overloaded")) {
            return new ClassifiedError(ApiErrorType.SERVER_ERROR, message, null, Map.of());
        }

        // Authentication
        if (lowerMsg.contains("401")
                || lowerMsg.contains("403")
                || lowerMsg.contains("unauthorized")
                || lowerMsg.contains("invalid api key")
                || lowerMsg.contains("invalid_api_key")
                || lowerMsg.contains("authentication")) {
            return new ClassifiedError(ApiErrorType.AUTHENTICATION_ERROR, message, null, Map.of());
        }

        return new ClassifiedError(ApiErrorType.UNKNOWN, message, null, Map.of());
    }

    /**
     * Extract retry-after duration from an error message.
     *
     * <p>Looks for patterns like "retry after X seconds" or "Retry-After: X". Defaults to 5 seconds
     * if no value is found.
     *
     * @param message the error message
     * @return the retry-after duration
     */
    private Duration extractRetryAfter(String message) {
        try {
            var matcher = RETRY_AFTER_PATTERN.matcher(message);
            if (matcher.find()) {
                return Duration.ofSeconds(Long.parseLong(matcher.group(1)));
            }
        } catch (Exception ignored) {
            // Fall through to default
        }
        return Duration.ofSeconds(5);
    }
}
