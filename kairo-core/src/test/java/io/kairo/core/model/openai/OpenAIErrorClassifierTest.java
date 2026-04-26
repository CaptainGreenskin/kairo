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
package io.kairo.core.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.core.model.ModelProviderException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class OpenAIErrorClassifierTest {

    private final OpenAIErrorClassifier classifier = new OpenAIErrorClassifier();

    @Test
    void isRetryable_rateLimitException_returnsTrue() {
        var ex = new ModelProviderException.RateLimitException("rate limited", null);
        assertThat(classifier.isRetryable(ex)).isTrue();
    }

    @Test
    void isRetryable_serverError503_returnsTrue() {
        var ex = new ModelProviderException.ApiException("HTTP 503 service unavailable");
        assertThat(classifier.isRetryable(ex)).isTrue();
    }

    @Test
    void isRetryable_serverError500_returnsTrue() {
        var ex = new ModelProviderException.ApiException("HTTP 500 internal server error");
        assertThat(classifier.isRetryable(ex)).isTrue();
    }

    @Test
    void isRetryable_timeoutException_returnsTrue() {
        assertThat(classifier.isRetryable(new TimeoutException("timed out"))).isTrue();
    }

    @Test
    void isRetryable_genericRuntimeException_returnsFalse() {
        assertThat(classifier.isRetryable(new RuntimeException("generic error"))).isFalse();
    }

    @Test
    void isRetryable_nullError_returnsFalse() {
        assertThat(classifier.isRetryable(null)).isFalse();
    }

    @Test
    void isRetryableError_rateLimitWithRetryAfter_returnsTrue() {
        var ex = new ModelProviderException.RateLimitException("429", 60L);
        assertThat(classifier.isRetryableError(ex)).isTrue();
    }
}
