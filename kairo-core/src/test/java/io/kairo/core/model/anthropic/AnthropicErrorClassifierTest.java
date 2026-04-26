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
package io.kairo.core.model.anthropic;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.exception.ModelRateLimitException;
import io.kairo.api.model.ProviderPipeline;
import io.kairo.core.model.ModelProviderException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnthropicErrorClassifierTest {

    private AnthropicErrorClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new AnthropicErrorClassifier();
    }

    @Test
    void implementsErrorClassifierInterface() {
        assertInstanceOf(ProviderPipeline.ErrorClassifier.class, classifier);
    }

    @Test
    void timeoutExceptionIsRetryable() {
        assertTrue(classifier.isRetryable(new TimeoutException("timed out")));
    }

    @Test
    void modelRateLimitExceptionIsRetryable() {
        assertTrue(classifier.isRetryable(new ModelRateLimitException("rate limited")));
    }

    @Test
    void internalRateLimitExceptionIsRetryable() {
        assertTrue(
                classifier.isRetryable(
                        new ModelProviderException.RateLimitException("rate limit", null)));
    }

    @Test
    void apiExceptionHttp500IsRetryable() {
        assertTrue(
                classifier.isRetryable(
                        new ModelProviderException.ApiException("HTTP 500 internal error")));
    }

    @Test
    void apiExceptionHttp503IsRetryable() {
        assertTrue(
                classifier.isRetryable(
                        new ModelProviderException.ApiException("HTTP 503 unavailable")));
    }

    @Test
    void apiExceptionNullMessageIsNotRetryable() {
        assertFalse(classifier.isRetryable(new ModelProviderException.ApiException(null)));
    }

    @Test
    void genericRuntimeExceptionIsNotRetryable() {
        assertFalse(classifier.isRetryable(new RuntimeException("unexpected")));
    }

    @Test
    void isRetryableErrorDelegatesToIsRetryable() {
        assertEquals(
                classifier.isRetryable(new TimeoutException()),
                classifier.isRetryableError(new TimeoutException()));
    }
}
