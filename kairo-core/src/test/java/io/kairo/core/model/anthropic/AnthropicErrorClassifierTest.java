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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.core.model.ModelProviderException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class AnthropicErrorClassifierTest {

    private final AnthropicErrorClassifier classifier = new AnthropicErrorClassifier();

    @Test
    void isRetryable_rateLimitException_returnsTrue() {
        var ex = new ModelProviderException.RateLimitException("rate limited", null);
        assertThat(classifier.isRetryable(ex)).isTrue();
    }

    @Test
    void isRetryable_serverError500_returnsTrue() {
        var ex = new ModelProviderException.ApiException("HTTP 500 internal server error");
        assertThat(classifier.isRetryable(ex)).isTrue();
    }

    @Test
    void isRetryable_serverError503_returnsTrue() {
        var ex = new ModelProviderException.ApiException("HTTP 503 service unavailable");
        assertThat(classifier.isRetryable(ex)).isTrue();
    }

    @Test
    void isRetryable_timeoutException_returnsTrue() {
        assertThat(classifier.isRetryable(new TimeoutException("timed out"))).isTrue();
    }

    @Test
    void isRetryable_clientError400_returnsFalse() {
        var ex = new ModelProviderException.ApiException("HTTP 400 bad request");
        assertThat(classifier.isRetryable(ex)).isFalse();
    }

    @Test
    void isRetryable_nullError_returnsFalse() {
        assertThat(classifier.isRetryable(null)).isFalse();
    }

    @Test
    void isRetryableError_rateLimitWithRetryAfter_returnsTrue() {
        var ex = new ModelProviderException.RateLimitException("429 too many requests", 30L);
        assertThat(classifier.isRetryableError(ex)).isTrue();
    }
}
