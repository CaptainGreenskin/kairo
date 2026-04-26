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
import org.junit.jupiter.api.Test;

class ApiErrorClassifierImplTest {

    private ApiErrorClassifierImpl classifier;

    @BeforeEach
    void setUp() {
        classifier = new ApiErrorClassifierImpl();
    }

    @Test
    void alreadyClassifiedApiExceptionPassthrough() {
        ApiException ae = new ApiException(ApiErrorType.RATE_LIMITED, "rate limited", Map.of());
        ClassifiedError result = classifier.classify(ae);
        assertEquals(ApiErrorType.RATE_LIMITED, result.type());
        assertEquals("rate limited", result.message());
    }

    @Test
    void promptTooLongAnthropicKeyword() {
        ClassifiedError result = classifier.classify(new RuntimeException("prompt is too long"));
        assertEquals(ApiErrorType.PROMPT_TOO_LONG, result.type());
    }

    @Test
    void promptTooLongOpenAIContextLengthExceeded() {
        ClassifiedError result =
                classifier.classify(new RuntimeException("context_length_exceeded error"));
        assertEquals(ApiErrorType.PROMPT_TOO_LONG, result.type());
    }

    @Test
    void promptTooLongMaximumContextLength() {
        ClassifiedError result =
                classifier.classify(new RuntimeException("maximum context length exceeded"));
        assertEquals(ApiErrorType.PROMPT_TOO_LONG, result.type());
    }

    @Test
    void promptTooLongTooManyTokens() {
        ClassifiedError result = classifier.classify(new RuntimeException("too many tokens"));
        assertEquals(ApiErrorType.PROMPT_TOO_LONG, result.type());
    }

    @Test
    void maxOutputTokensKeyword() {
        ClassifiedError result =
                classifier.classify(new RuntimeException("max_tokens limit reached"));
        assertEquals(ApiErrorType.MAX_OUTPUT_TOKENS, result.type());
    }

    @Test
    void maxOutputTokensMaximumOutput() {
        ClassifiedError result =
                classifier.classify(new RuntimeException("maximum output exceeded"));
        assertEquals(ApiErrorType.MAX_OUTPUT_TOKENS, result.type());
    }

    @Test
    void rateLimitedByKeyword() {
        ClassifiedError result = classifier.classify(new RuntimeException("rate limit exceeded"));
        assertEquals(ApiErrorType.RATE_LIMITED, result.type());
    }

    @Test
    void rateLimitedBy429() {
        ClassifiedError result =
                classifier.classify(new RuntimeException("HTTP 429 Too Many Requests"));
        assertEquals(ApiErrorType.RATE_LIMITED, result.type());
    }

    @Test
    void rateLimitedDefaultRetryAfterFiveSeconds() {
        ClassifiedError result = classifier.classify(new RuntimeException("rate limit exceeded"));
        assertEquals(Duration.ofSeconds(5), result.retryAfter());
    }

    @Test
    void rateLimitedExtractsRetryAfterFromMessage() {
        ClassifiedError result =
                classifier.classify(new RuntimeException("rate limit exceeded retry-after: 30"));
        assertEquals(Duration.ofSeconds(30), result.retryAfter());
    }

    @Test
    void serverError500() {
        ClassifiedError result = classifier.classify(new RuntimeException("HTTP 500 error"));
        assertEquals(ApiErrorType.SERVER_ERROR, result.type());
    }

    @Test
    void serverError503() {
        ClassifiedError result =
                classifier.classify(new RuntimeException("503 service unavailable"));
        assertEquals(ApiErrorType.SERVER_ERROR, result.type());
    }

    @Test
    void serverErrorOverloaded() {
        ClassifiedError result = classifier.classify(new RuntimeException("model is overloaded"));
        assertEquals(ApiErrorType.SERVER_ERROR, result.type());
    }

    @Test
    void authenticationError401() {
        ClassifiedError result = classifier.classify(new RuntimeException("401 unauthorized"));
        assertEquals(ApiErrorType.AUTHENTICATION_ERROR, result.type());
    }

    @Test
    void authenticationErrorInvalidApiKey() {
        ClassifiedError result =
                classifier.classify(new RuntimeException("invalid api key provided"));
        assertEquals(ApiErrorType.AUTHENTICATION_ERROR, result.type());
    }

    @Test
    void unknownForUnrecognizedError() {
        ClassifiedError result = classifier.classify(new RuntimeException("something unexpected"));
        assertEquals(ApiErrorType.UNKNOWN, result.type());
    }

    @Test
    void nullMessageDoesNotThrow() {
        ClassifiedError result = classifier.classify(new RuntimeException((String) null));
        assertNotNull(result);
        assertEquals(ApiErrorType.UNKNOWN, result.type());
    }

    @Test
    void classifiedErrorMessagePreserved() {
        String msg = "rate limit exceeded";
        ClassifiedError result = classifier.classify(new RuntimeException(msg));
        assertEquals(msg, result.message());
    }
}
