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

import io.kairo.api.exception.ModelApiException;
import io.kairo.api.exception.ModelRateLimitException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class ProviderRetryTest {

    // ---- isTransientProviderError: null ----

    @Test
    void nullIsNotTransient() {
        assertFalse(ProviderRetry.isTransientProviderError(null));
    }

    // ---- isTransientProviderError: TimeoutException ----

    @Test
    void timeoutExceptionIsTransient() {
        assertTrue(ProviderRetry.isTransientProviderError(new TimeoutException("timed out")));
    }

    // ---- isTransientProviderError: internal RateLimitException ----

    @Test
    void internalRateLimitExceptionIsTransient() {
        assertTrue(
                ProviderRetry.isTransientProviderError(
                        new ModelProviderException.RateLimitException("429", null)));
    }

    // ---- isTransientProviderError: public ModelRateLimitException ----

    @Test
    void publicModelRateLimitExceptionIsTransient() {
        assertTrue(
                ProviderRetry.isTransientProviderError(
                        new ModelRateLimitException("429 rate limit")));
    }

    // ---- isTransientProviderError: ApiException with HTTP 5xx ----

    @Test
    void apiExceptionHttp500IsTransient() {
        assertTrue(
                ProviderRetry.isTransientProviderError(
                        new ModelProviderException.ApiException("HTTP 500 Internal Server Error")));
    }

    @Test
    void apiExceptionHttp502IsTransient() {
        assertTrue(
                ProviderRetry.isTransientProviderError(
                        new ModelProviderException.ApiException("HTTP 502 Bad Gateway")));
    }

    @Test
    void apiExceptionHttp503IsTransient() {
        assertTrue(
                ProviderRetry.isTransientProviderError(
                        new ModelProviderException.ApiException("HTTP 503 Service Unavailable")));
    }

    @Test
    void apiExceptionServerErrorIsTransient() {
        assertTrue(
                ProviderRetry.isTransientProviderError(
                        new ModelProviderException.ApiException("server error occurred")));
    }

    @Test
    void apiExceptionHttp400IsNotTransient() {
        assertFalse(
                ProviderRetry.isTransientProviderError(
                        new ModelProviderException.ApiException("HTTP 400 Bad Request")));
    }

    @Test
    void apiExceptionNullMessageIsNotTransient() {
        assertFalse(
                ProviderRetry.isTransientProviderError(
                        new ModelProviderException.ApiException((String) null)));
    }

    // ---- isTransientProviderError: public ModelApiException ----

    @Test
    void publicModelApiExceptionHttp500IsTransient() {
        assertTrue(
                ProviderRetry.isTransientProviderError(
                        new ModelApiException("HTTP 500 Internal Server Error")));
    }

    @Test
    void publicModelApiExceptionHttp400IsNotTransient() {
        assertFalse(
                ProviderRetry.isTransientProviderError(
                        new ModelApiException("HTTP 400 Bad Request")));
    }

    // ---- isTransientProviderError: unrelated exceptions ----

    @Test
    void genericRuntimeExceptionIsNotTransient() {
        assertFalse(ProviderRetry.isTransientProviderError(new RuntimeException("random error")));
    }

    @Test
    void illegalArgumentExceptionIsNotTransient() {
        assertFalse(
                ProviderRetry.isTransientProviderError(new IllegalArgumentException("bad arg")));
    }

    // ---- Default constants ----

    @Test
    void defaultMaxAttemptsIsThree() {
        assertEquals(3L, ProviderRetry.DEFAULT_MAX_ATTEMPTS);
    }

    @Test
    void defaultMinBackoffIsOneSecond() {
        assertEquals(Duration.ofSeconds(1), ProviderRetry.DEFAULT_MIN_BACKOFF);
    }

    @Test
    void defaultMaxBackoffIsFourSeconds() {
        assertEquals(Duration.ofSeconds(4), ProviderRetry.DEFAULT_MAX_BACKOFF);
    }

    @Test
    void defaultJitterIsQuarterPoint() {
        assertEquals(0.25, ProviderRetry.DEFAULT_JITTER, 1e-9);
    }
}
