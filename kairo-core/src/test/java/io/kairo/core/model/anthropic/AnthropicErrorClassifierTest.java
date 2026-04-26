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

import io.kairo.api.model.ProviderPipeline;
import io.kairo.core.model.ModelProviderException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class AnthropicErrorClassifierTest {

    private final AnthropicErrorClassifier classifier = new AnthropicErrorClassifier();

    @Test
    void implementsErrorClassifier() {
        assertThat(classifier).isInstanceOf(ProviderPipeline.ErrorClassifier.class);
    }

    @Test
    void timeoutIsRetryable() {
        assertThat(classifier.isRetryable(new TimeoutException("timeout"))).isTrue();
    }

    @Test
    void rateLimitExceptionIsRetryable() {
        ModelProviderException.RateLimitException ex =
                new ModelProviderException.RateLimitException("rate limited", null);
        assertThat(classifier.isRetryable(ex)).isTrue();
    }

    @Test
    void apiExceptionWith500IsRetryable() {
        ModelProviderException.ApiException ex =
                new ModelProviderException.ApiException("HTTP 500 Internal Server Error");
        assertThat(classifier.isRetryable(ex)).isTrue();
    }

    @Test
    void apiExceptionWith503IsRetryable() {
        ModelProviderException.ApiException ex =
                new ModelProviderException.ApiException("HTTP 503 Service Unavailable");
        assertThat(classifier.isRetryable(ex)).isTrue();
    }

    @Test
    void genericRuntimeExceptionIsNotRetryable() {
        assertThat(classifier.isRetryable(new RuntimeException("auth error"))).isFalse();
    }

    @Test
    void isRetryableErrorDelegatesToIsRetryable() {
        TimeoutException ex = new TimeoutException("timeout");
        assertThat(classifier.isRetryableError(ex)).isEqualTo(classifier.isRetryable(ex));
    }

    @Test
    void nullReturnsFalse() {
        assertThat(classifier.isRetryable(null)).isFalse();
    }
}
