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

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.model.ApiErrorType;
import io.kairo.api.model.ApiException;
import io.kairo.api.model.ClassifiedError;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiErrorClassifierTest {

    private ApiErrorClassifierImpl classifier;

    @BeforeEach
    void setUp() {
        classifier = new ApiErrorClassifierImpl();
    }

    @Test
    @DisplayName("Classifies prompt too long — Anthropic format")
    void classifiesPromptTooLong_anthropicFormat() {
        ClassifiedError err = classifier.classify(new RuntimeException("prompt is too long"));
        assertEquals(ApiErrorType.PROMPT_TOO_LONG, err.type());
    }

    @Test
    @DisplayName("Classifies prompt too long — OpenAI context_length_exceeded")
    void classifiesPromptTooLong_openaiContextLengthExceeded() {
        ClassifiedError err =
                classifier.classify(new RuntimeException("context_length_exceeded: max 128000"));
        assertEquals(ApiErrorType.PROMPT_TOO_LONG, err.type());
    }

    @Test
    @DisplayName("Classifies prompt too long — OpenAI maximum context length")
    void classifiesPromptTooLong_openaiMaximumContextLength() {
        ClassifiedError err =
                classifier.classify(
                        new RuntimeException(
                                "This model's maximum context length is 128000 tokens"));
        assertEquals(ApiErrorType.PROMPT_TOO_LONG, err.type());
    }

    @Test
    @DisplayName("Classifies max output tokens exceeded")
    void classifiesMaxOutputTokens() {
        ClassifiedError err =
                classifier.classify(new RuntimeException("max_tokens limit exceeded"));
        assertEquals(ApiErrorType.MAX_OUTPUT_TOKENS, err.type());
    }

    @Test
    @DisplayName("Classifies rate limited")
    void classifiesRateLimited() {
        ClassifiedError err = classifier.classify(new RuntimeException("rate limit exceeded, 429"));
        assertEquals(ApiErrorType.RATE_LIMITED, err.type());
        assertNotNull(err.retryAfter());
    }

    @Test
    @DisplayName("Classifies rate limited and extracts retry-after seconds")
    void classifiesRateLimited_extractsRetryAfter() {
        ClassifiedError err =
                classifier.classify(
                        new RuntimeException("rate limit exceeded. Retry after 10 seconds"));
        assertEquals(ApiErrorType.RATE_LIMITED, err.type());
        assertEquals(Duration.ofSeconds(10), err.retryAfter());
    }

    @Test
    @DisplayName("Classifies rate limited with default retry-after when not specified")
    void classifiesRateLimited_defaultRetryAfter() {
        ClassifiedError err = classifier.classify(new RuntimeException("too many requests"));
        assertEquals(ApiErrorType.RATE_LIMITED, err.type());
        assertEquals(Duration.ofSeconds(5), err.retryAfter());
    }

    @Test
    @DisplayName("Classifies server error — 500")
    void classifiesServerError_500() {
        ClassifiedError err =
                classifier.classify(new RuntimeException("HTTP 500 internal server error"));
        assertEquals(ApiErrorType.SERVER_ERROR, err.type());
    }

    @Test
    @DisplayName("Classifies server error — 503")
    void classifiesServerError_503() {
        ClassifiedError err =
                classifier.classify(new RuntimeException("Service unavailable (503)"));
        assertEquals(ApiErrorType.SERVER_ERROR, err.type());
    }

    @Test
    @DisplayName("Classifies server error — overloaded")
    void classifiesServerError_overloaded() {
        ClassifiedError err =
                classifier.classify(new RuntimeException("The server is currently overloaded"));
        assertEquals(ApiErrorType.SERVER_ERROR, err.type());
    }

    @Test
    @DisplayName("Classifies authentication error")
    void classifiesAuthenticationError() {
        ClassifiedError err =
                classifier.classify(new RuntimeException("401 unauthorized: invalid api key"));
        assertEquals(ApiErrorType.AUTHENTICATION_ERROR, err.type());
    }

    @Test
    @DisplayName("Classifies unknown error for unrecognized messages")
    void classifiesUnknownError() {
        ClassifiedError err =
                classifier.classify(new RuntimeException("something completely unexpected"));
        assertEquals(ApiErrorType.UNKNOWN, err.type());
        assertEquals("something completely unexpected", err.message());
    }

    @Test
    @DisplayName("Already classified ApiException is preserved as-is")
    void classifiesAlreadyClassifiedApiException() {
        ApiException apiEx =
                new ApiException(
                        ApiErrorType.BUDGET_EXCEEDED, "budget exceeded", Map.of("limit", 100));
        ClassifiedError err = classifier.classify(apiEx);
        assertEquals(ApiErrorType.BUDGET_EXCEEDED, err.type());
        assertEquals("budget exceeded", err.message());
        assertEquals(Map.of("limit", 100), err.metadata());
    }

    @Test
    @DisplayName("Null message in exception is handled gracefully")
    void classifiesNullMessage() {
        ClassifiedError err = classifier.classify(new RuntimeException((String) null));
        assertEquals(ApiErrorType.UNKNOWN, err.type());
    }
}
