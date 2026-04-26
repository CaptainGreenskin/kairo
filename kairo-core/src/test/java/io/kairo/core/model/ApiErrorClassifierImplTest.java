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
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiErrorClassifierImplTest {

    private final ApiErrorClassifierImpl classifier = new ApiErrorClassifierImpl();

    @Test
    void classify_alreadyApiException_preservesType() {
        var ex = new ApiException(ApiErrorType.BUDGET_EXCEEDED, "budget exceeded", Map.of());
        assertThat(classifier.classify(ex).type()).isEqualTo(ApiErrorType.BUDGET_EXCEEDED);
    }

    @Test
    void classify_rateLimitKeyword_returnsRateLimited() {
        var result = classifier.classify(new RuntimeException("rate limit exceeded"));
        assertThat(result.type()).isEqualTo(ApiErrorType.RATE_LIMITED);
    }

    @Test
    void classify_429InMessage_returnsRateLimited() {
        var result = classifier.classify(new RuntimeException("HTTP 429 too many requests"));
        assertThat(result.type()).isEqualTo(ApiErrorType.RATE_LIMITED);
    }

    @Test
    void classify_serverError503_returnsServerError() {
        var result = classifier.classify(new RuntimeException("HTTP 503 service unavailable"));
        assertThat(result.type()).isEqualTo(ApiErrorType.SERVER_ERROR);
    }

    @Test
    void classify_serverError500_returnsServerError() {
        var result = classifier.classify(new RuntimeException("HTTP 500 internal server error"));
        assertThat(result.type()).isEqualTo(ApiErrorType.SERVER_ERROR);
    }

    @Test
    void classify_promptTooLong_returnsPromptTooLong() {
        var result = classifier.classify(new RuntimeException("prompt is too long for model"));
        assertThat(result.type()).isEqualTo(ApiErrorType.PROMPT_TOO_LONG);
    }

    @Test
    void classify_unknownError_returnsUnknown() {
        var result = classifier.classify(new RuntimeException("some unexpected error"));
        assertThat(result.type()).isEqualTo(ApiErrorType.UNKNOWN);
    }

    @Test
    void classify_retryAfterExtractedFromRateLimitMessage() {
        var result =
                classifier.classify(new RuntimeException("rate limit retry-after: 30 seconds"));
        assertThat(result.type()).isEqualTo(ApiErrorType.RATE_LIMITED);
        assertThat(result.retryAfter()).isNotNull();
        assertThat(result.retryAfter().toSeconds()).isEqualTo(30);
    }

    @Test
    void classify_rateLimitNoRetryAfter_defaultsTo5Seconds() {
        var result = classifier.classify(new RuntimeException("rate limit exceeded"));
        assertThat(result.retryAfter().toSeconds()).isEqualTo(5);
    }
}
