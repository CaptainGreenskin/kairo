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

import org.junit.jupiter.api.Test;

class ModelProviderExceptionTest {

    @Test
    void rateLimitExceptionWithRetryAfter() {
        var ex = new ModelProviderException.RateLimitException("rate limited", 30L);
        assertEquals("rate limited", ex.getMessage());
        assertEquals(30L, ex.getRetryAfterSeconds());
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void rateLimitExceptionNullRetryAfter() {
        var ex = new ModelProviderException.RateLimitException("rate limited", null);
        assertNull(ex.getRetryAfterSeconds());
    }

    @Test
    void apiExceptionMessageOnly() {
        var ex = new ModelProviderException.ApiException("API error");
        assertEquals("API error", ex.getMessage());
        assertNull(ex.getCause());
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void apiExceptionWithCause() {
        var cause = new RuntimeException("root cause");
        var ex = new ModelProviderException.ApiException("API error", cause);
        assertEquals("API error", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    void anthropicProviderExceptionsExtendShared() {
        // Backward compatibility: AnthropicProvider inner classes extend shared ones
        var rle = new AnthropicProvider.RateLimitException("test", 10L);
        assertInstanceOf(ModelProviderException.RateLimitException.class, rle);
        assertEquals(10L, rle.getRetryAfterSeconds());

        var ae = new AnthropicProvider.ApiException("test");
        assertInstanceOf(ModelProviderException.ApiException.class, ae);

        var aeWithCause = new AnthropicProvider.ApiException("test", new RuntimeException());
        assertInstanceOf(ModelProviderException.ApiException.class, aeWithCause);
    }
}
