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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.model.ApiErrorType;
import io.kairo.api.model.ApiException;
import io.kairo.api.model.ClassifiedError;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ApiErrorClassifierImpl}. */
class ApiErrorClassifierImplTest {

    private final ApiErrorClassifierImpl classifier = new ApiErrorClassifierImpl();

    // ===== PROMPT_TOO_LONG =====

    @Test
    void classify_promptTooLong_anthropicMessage_returnsPromptTooLong() {
        ClassifiedError result = classifier.classify(new RuntimeException("prompt is too long"));
        assertThat(result.type()).isEqualTo(ApiErrorType.PROMPT_TOO_LONG);
    }

    @Test
    void classify_promptTooLong_openAiContextExceeded_returnsPromptTooLong() {
        ClassifiedError result =
                classifier.classify(new RuntimeException("context_length_exceeded: 200k tokens"));
        assertThat(result.type()).isEqualTo(ApiErrorType.PROMPT_TOO_LONG);
    }

    @Test
    void classify_promptTooLong_maximumContextLength_returnsPromptTooLong() {
        ClassifiedError result =
                classifier.classify(
                        new RuntimeException("maximum context length is 128000 tokens"));
        assertThat(result.type()).isEqualTo(ApiErrorType.PROMPT_TOO_LONG);
    }

    // ===== MAX_OUTPUT_TOKENS =====

    @Test
    void classify_maxOutputTokens_returnsMaxOutputTokens() {
        ClassifiedError result = classifier.classify(new RuntimeException("max_tokens exceeded"));
        assertThat(result.type()).isEqualTo(ApiErrorType.MAX_OUTPUT_TOKENS);
    }

    // ===== RATE_LIMITED =====

    @Test
    void classify_rateLimited_keyword_returnsRateLimited() {
        ClassifiedError result = classifier.classify(new RuntimeException("rate limit exceeded"));
        assertThat(result.type()).isEqualTo(ApiErrorType.RATE_LIMITED);
    }

    @Test
    void classify_rateLimited_status429_returnsRateLimited() {
        ClassifiedError result =
                classifier.classify(new RuntimeException("HTTP 429 too many requests"));
        assertThat(result.type()).isEqualTo(ApiErrorType.RATE_LIMITED);
    }

    @Test
    void classify_rateLimited_withRetryAfter_extractsSeconds() {
        ClassifiedError result =
                classifier.classify(
                        new RuntimeException("rate limit exceeded. retry-after: 30 seconds"));
        assertThat(result.type()).isEqualTo(ApiErrorType.RATE_LIMITED);
        assertThat(result.retryAfter()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void classify_rateLimited_noRetryAfter_defaultsFiveSeconds() {
        ClassifiedError result = classifier.classify(new RuntimeException("rate limit"));
        assertThat(result.type()).isEqualTo(ApiErrorType.RATE_LIMITED);
        assertThat(result.retryAfter()).isEqualTo(Duration.ofSeconds(5));
    }

    // ===== SERVER_ERROR =====

    @Test
    void classify_serverError_500_returnsServerError() {
        ClassifiedError result =
                classifier.classify(new RuntimeException("HTTP 500 internal server error"));
        assertThat(result.type()).isEqualTo(ApiErrorType.SERVER_ERROR);
    }

    @Test
    void classify_serverError_503_returnsServerError() {
        ClassifiedError result = classifier.classify(new RuntimeException("HTTP 503 unavailable"));
        assertThat(result.type()).isEqualTo(ApiErrorType.SERVER_ERROR);
    }

    @Test
    void classify_serverError_overloaded_returnsServerError() {
        ClassifiedError result = classifier.classify(new RuntimeException("API overloaded"));
        assertThat(result.type()).isEqualTo(ApiErrorType.SERVER_ERROR);
    }

    // ===== AUTHENTICATION_ERROR =====

    @Test
    void classify_authError_401_returnsAuthError() {
        ClassifiedError result = classifier.classify(new RuntimeException("HTTP 401 unauthorized"));
        assertThat(result.type()).isEqualTo(ApiErrorType.AUTHENTICATION_ERROR);
    }

    @Test
    void classify_authError_invalidApiKey_returnsAuthError() {
        ClassifiedError result =
                classifier.classify(new RuntimeException("invalid api key provided"));
        assertThat(result.type()).isEqualTo(ApiErrorType.AUTHENTICATION_ERROR);
    }

    // ===== ApiException pass-through =====

    @Test
    void classify_alreadyApiException_returnsSameType() {
        ApiException ae =
                new ApiException(
                        ApiErrorType.BUDGET_EXCEEDED, "budget exceeded", Map.of("limit", 100));
        ClassifiedError result = classifier.classify(ae);
        assertThat(result.type()).isEqualTo(ApiErrorType.BUDGET_EXCEEDED);
        assertThat(result.message()).isEqualTo("budget exceeded");
    }

    // ===== UNKNOWN =====

    @Test
    void classify_unknownError_returnsUnknown() {
        ClassifiedError result =
                classifier.classify(new RuntimeException("something weird happened"));
        assertThat(result.type()).isEqualTo(ApiErrorType.UNKNOWN);
    }
}
