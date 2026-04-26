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

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class ProviderRetryTest {

    @Test
    void defaultMaxAttemptsIs3() {
        assertThat(ProviderRetry.DEFAULT_MAX_ATTEMPTS).isEqualTo(3);
    }

    @Test
    void defaultMinBackoffIs1Second() {
        assertThat(ProviderRetry.DEFAULT_MIN_BACKOFF).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void defaultMaxBackoffIs4Seconds() {
        assertThat(ProviderRetry.DEFAULT_MAX_BACKOFF).isEqualTo(Duration.ofSeconds(4));
    }

    @Test
    void defaultJitterIs025() {
        assertThat(ProviderRetry.DEFAULT_JITTER).isEqualTo(0.25);
    }

    @Test
    void nullIsNotTransient() {
        assertThat(ProviderRetry.isTransientProviderError(null)).isFalse();
    }

    @Test
    void timeoutExceptionIsTransient() {
        assertThat(ProviderRetry.isTransientProviderError(new TimeoutException())).isTrue();
    }

    @Test
    void rateLimitExceptionIsTransient() {
        assertThat(
                        ProviderRetry.isTransientProviderError(
                                new ModelProviderException.RateLimitException("429", null)))
                .isTrue();
    }

    @Test
    void apiExceptionHttp500IsTransient() {
        assertThat(
                        ProviderRetry.isTransientProviderError(
                                new ModelProviderException.ApiException("HTTP 500 server error")))
                .isTrue();
    }

    @Test
    void apiExceptionHttp400IsNotTransient() {
        assertThat(
                        ProviderRetry.isTransientProviderError(
                                new ModelProviderException.ApiException("HTTP 400 Bad Request")))
                .isFalse();
    }

    @Test
    void randomExceptionIsNotTransient() {
        assertThat(ProviderRetry.isTransientProviderError(new IllegalArgumentException("bad")))
                .isFalse();
    }
}
