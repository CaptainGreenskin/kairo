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
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class ProviderRetryTest {

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
    void defaultJitterInValidRange() {
        assertTrue(
                ProviderRetry.DEFAULT_JITTER >= 0.0 && ProviderRetry.DEFAULT_JITTER <= 1.0,
                "Jitter should be in [0.0, 1.0] but was " + ProviderRetry.DEFAULT_JITTER);
    }

    @Test
    void nullIsNotTransient() {
        assertFalse(ProviderRetry.isTransientProviderError(null));
    }

    @Test
    void timeoutExceptionIsTransient() {
        assertTrue(ProviderRetry.isTransientProviderError(new TimeoutException("timeout")));
    }

    @Test
    void modelRateLimitExceptionIsTransient() {
        assertTrue(ProviderRetry.isTransientProviderError(new ModelRateLimitException("429")));
    }

    @Test
    void internalRateLimitExceptionIsTransient() {
        assertTrue(
                ProviderRetry.isTransientProviderError(
                        new ModelProviderException.RateLimitException("limit", null)));
    }

    @Test
    void genericRuntimeExceptionIsNotTransient() {
        assertFalse(ProviderRetry.isTransientProviderError(new RuntimeException("oops")));
    }

    @Test
    void modelApiExceptionHttp500IsTransient() {
        assertTrue(ProviderRetry.isTransientProviderError(new ModelApiException("HTTP 500 error")));
    }

    @Test
    void modelApiExceptionHttp503IsTransient() {
        assertTrue(
                ProviderRetry.isTransientProviderError(
                        new ModelApiException("HTTP 503 unavailable")));
    }

    @Test
    void modelApiExceptionNullMessageIsNotTransient() {
        assertFalse(ProviderRetry.isTransientProviderError(new ModelApiException(null)));
    }

    @Test
    void constructorIsPrivate() throws Exception {
        var ctor = ProviderRetry.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(ctor.getModifiers()));
    }
}
