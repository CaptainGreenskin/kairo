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

class ApiErrorClassifierImplTest {

    private final ApiErrorClassifierImpl classifier = new ApiErrorClassifierImpl();

    @Test
    void rateLimitKeywordMapsToRateLimited() {
        ClassifiedError result = classifier.classify(new RuntimeException("rate limit exceeded"));
        assertThat(result.type()).isEqualTo(ApiErrorType.RATE_LIMITED);
    }

    @Test
    void status429MapsToRateLimited() {
        ClassifiedError result =
                classifier.classify(new RuntimeException("HTTP 429 Too Many Requests"));
        assertThat(result.type()).isEqualTo(ApiErrorType.RATE_LIMITED);
    }

    @Test
    void tooManyRequestsMapsToRateLimited() {
        ClassifiedError result = classifier.classify(new RuntimeException("too many requests"));
        assertThat(result.type()).isEqualTo(ApiErrorType.RATE_LIMITED);
    }

    @Test
    void rateLimitedIncludesDefaultRetryAfter() {
        ClassifiedError result = classifier.classify(new RuntimeException("rate limit exceeded"));
        assertThat(result.retryAfter()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void retryAfterExtractedFromMessage() {
        ClassifiedError result =
                classifier.classify(new RuntimeException("rate limit exceeded retry-after: 30"));
        assertThat(result.retryAfter()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void unauthorizedMapsToAuthenticationError() {
        ClassifiedError result = classifier.classify(new RuntimeException("unauthorized"));
        assertThat(result.type()).isEqualTo(ApiErrorType.AUTHENTICATION_ERROR);
    }

    @Test
    void status401MapsToAuthenticationError() {
        ClassifiedError result = classifier.classify(new RuntimeException("HTTP 401 Unauthorized"));
        assertThat(result.type()).isEqualTo(ApiErrorType.AUTHENTICATION_ERROR);
    }

    @Test
    void invalidApiKeyMapsToAuthenticationError() {
        ClassifiedError result =
                classifier.classify(new RuntimeException("invalid api key provided"));
        assertThat(result.type()).isEqualTo(ApiErrorType.AUTHENTICATION_ERROR);
    }

    @Test
    void serverErrorMapsToServerError() {
        ClassifiedError result = classifier.classify(new RuntimeException("internal server error"));
        assertThat(result.type()).isEqualTo(ApiErrorType.SERVER_ERROR);
    }

    @Test
    void status500MapsToServerError() {
        ClassifiedError result = classifier.classify(new RuntimeException("HTTP 500 error"));
        assertThat(result.type()).isEqualTo(ApiErrorType.SERVER_ERROR);
    }

    @Test
    void promptTooLongMapsToPromptTooLong() {
        ClassifiedError result = classifier.classify(new RuntimeException("prompt is too long"));
        assertThat(result.type()).isEqualTo(ApiErrorType.PROMPT_TOO_LONG);
    }

    @Test
    void contextLengthExceededMapsToPromptTooLong() {
        ClassifiedError result =
                classifier.classify(new RuntimeException("context_length_exceeded limit reached"));
        assertThat(result.type()).isEqualTo(ApiErrorType.PROMPT_TOO_LONG);
    }

    @Test
    void maxTokensKeywordMapsToMaxOutputTokens() {
        ClassifiedError result = classifier.classify(new RuntimeException("max_tokens exceeded"));
        assertThat(result.type()).isEqualTo(ApiErrorType.MAX_OUTPUT_TOKENS);
    }

    @Test
    void unknownErrorMapsToUnknown() {
        ClassifiedError result =
                classifier.classify(new RuntimeException("something weird happened"));
        assertThat(result.type()).isEqualTo(ApiErrorType.UNKNOWN);
    }

    @Test
    void messageIsPreserved() {
        String msg = "some unexpected error";
        ClassifiedError result = classifier.classify(new RuntimeException(msg));
        assertThat(result.message()).isEqualTo(msg);
    }

    @Test
    void nullMessageHandled() {
        ClassifiedError result = classifier.classify(new RuntimeException((String) null));
        assertThat(result.type()).isEqualTo(ApiErrorType.UNKNOWN);
        assertThat(result.message()).isEqualTo("");
    }

    @Test
    void alreadyClassifiedApiExceptionPassedThrough() {
        ApiException ex =
                new ApiException(ApiErrorType.BUDGET_EXCEEDED, "budget exceeded", Map.of());
        ClassifiedError result = classifier.classify(ex);
        assertThat(result.type()).isEqualTo(ApiErrorType.BUDGET_EXCEEDED);
    }
}
