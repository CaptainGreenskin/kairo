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

import io.kairo.api.exception.ErrorCategory;
import io.kairo.api.exception.KairoException;
import io.kairo.api.exception.MemoryStoreException;
import io.kairo.api.exception.ModelApiException;
import io.kairo.api.exception.ModelRateLimitException;
import io.kairo.api.exception.ModelTimeoutException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import reactor.core.Exceptions;

class ExceptionMapperTest {

    // --- toApiException ---

    @Test
    void rateLimitExceptionMapsToModelRateLimitException() {
        var cause = new ModelProviderException.RateLimitException("rate limited", 30L);
        Throwable mapped = ExceptionMapper.toApiException(cause);

        assertInstanceOf(ModelRateLimitException.class, mapped);
        assertEquals("rate limited", mapped.getMessage());
        assertSame(cause, mapped.getCause());
    }

    @Test
    void apiExceptionMapsToModelApiException() {
        var cause = new ModelProviderException.ApiException("API error: HTTP 500");
        Throwable mapped = ExceptionMapper.toApiException(cause);

        assertInstanceOf(ModelApiException.class, mapped);
        assertEquals("API error: HTTP 500", mapped.getMessage());
        assertSame(cause, mapped.getCause());
    }

    @Test
    void kairoExceptionPassesThrough() {
        var original = new KairoException("already api layer");
        Throwable mapped = ExceptionMapper.toApiException(original);

        assertSame(original, mapped);
    }

    @Test
    void modelRateLimitExceptionPassesThrough() {
        var original = new ModelRateLimitException("already mapped");
        Throwable mapped = ExceptionMapper.toApiException(original);

        assertSame(original, mapped);
    }

    @Test
    void modelApiExceptionPassesThrough() {
        var original = new ModelApiException("already mapped");
        Throwable mapped = ExceptionMapper.toApiException(original);

        assertSame(original, mapped);
    }

    @Test
    void genericRuntimeExceptionWrappedAsKairoException() {
        var cause = new RuntimeException("unexpected");
        Throwable mapped = ExceptionMapper.toApiException(cause);

        assertInstanceOf(KairoException.class, mapped);
        assertEquals("Unexpected error", mapped.getMessage());
        assertSame(cause, mapped.getCause());
    }

    @Test
    void timeoutExceptionMapsToModelTimeoutException() {
        var cause = new TimeoutException("request timed out");
        Throwable mapped = ExceptionMapper.toApiException(cause);

        assertInstanceOf(ModelTimeoutException.class, mapped);
        assertEquals("request timed out", mapped.getMessage());
        assertSame(cause, mapped.getCause());
    }

    @Test
    void retryExhaustedTimeoutExceptionMapsToModelTimeoutException() {
        var timeout = new TimeoutException("deadline exceeded");
        var retryExhausted = Exceptions.retryExhausted("Retries exhausted", timeout);
        Throwable mapped = ExceptionMapper.toApiException(retryExhausted);

        assertInstanceOf(ModelTimeoutException.class, mapped);
        assertEquals("deadline exceeded", mapped.getMessage());
        assertSame(timeout, mapped.getCause());
    }

    @Test
    void apiExceptionWithCausePreservesCause() {
        var root = new RuntimeException("root");
        var apiEx = new ModelProviderException.ApiException("parse failed", root);
        Throwable mapped = ExceptionMapper.toApiException(apiEx);

        assertInstanceOf(ModelApiException.class, mapped);
        assertSame(apiEx, mapped.getCause());
    }

    // --- toStorageException ---

    @Test
    void sqlExceptionMapsToMemoryStoreException() {
        var cause = new java.sql.SQLException("connection refused");
        Throwable mapped = ExceptionMapper.toStorageException(cause);

        assertInstanceOf(MemoryStoreException.class, mapped);
        assertEquals("connection refused", mapped.getMessage());
        assertSame(cause, mapped.getCause());
    }

    @Test
    void kairoExceptionPassesThroughInStorageMapping() {
        var original = new MemoryStoreException("already mapped");
        Throwable mapped = ExceptionMapper.toStorageException(original);

        assertSame(original, mapped);
    }

    @Test
    void genericExceptionMapsToMemoryStoreException() {
        var cause = new RuntimeException("io error");
        Throwable mapped = ExceptionMapper.toStorageException(cause);

        assertInstanceOf(MemoryStoreException.class, mapped);
        assertEquals("io error", mapped.getMessage());
        assertSame(cause, mapped.getCause());
    }

    // --- Structured field propagation ---

    @Test
    void rateLimitMappingPopulatesRetryableAndErrorCode() {
        var cause = new ModelProviderException.RateLimitException("rate limited", 30L);
        Throwable mapped = ExceptionMapper.toApiException(cause);

        assertInstanceOf(ModelRateLimitException.class, mapped);
        var rle = (ModelRateLimitException) mapped;
        assertTrue(rle.isRetryable());
        assertEquals("MODEL_RATE_LIMITED", rle.getErrorCode());
        assertEquals(ErrorCategory.MODEL, rle.getCategory());
    }

    @Test
    void rateLimitMappingConvertsRetryAfterSecondsToMs() {
        var cause = new ModelProviderException.RateLimitException("rate limited", 30L);
        Throwable mapped = ExceptionMapper.toApiException(cause);

        var rle = (ModelRateLimitException) mapped;
        assertEquals(30000L, rle.getRetryAfterMs());
    }

    @Test
    void rateLimitMappingWithNullRetryAfterSeconds() {
        var cause = new ModelProviderException.RateLimitException("rate limited", null);
        Throwable mapped = ExceptionMapper.toApiException(cause);

        var rle = (ModelRateLimitException) mapped;
        assertTrue(rle.isRetryable());
        assertNull(rle.getRetryAfterMs());
    }

    @Test
    void timeoutMappingPopulatesErrorCode() {
        var cause = new TimeoutException("deadline exceeded");
        Throwable mapped = ExceptionMapper.toApiException(cause);

        assertInstanceOf(ModelTimeoutException.class, mapped);
        var mte = (ModelTimeoutException) mapped;
        assertEquals("MODEL_TIMEOUT", mte.getErrorCode());
        assertTrue(mte.isRetryable());
        assertEquals(ErrorCategory.MODEL, mte.getCategory());
    }

    @Test
    void apiExceptionMappingPreservesStructuredFields() {
        var cause = new ModelProviderException.ApiException("API error: HTTP 500");
        Throwable mapped = ExceptionMapper.toApiException(cause);

        assertInstanceOf(ModelApiException.class, mapped);
        var mae = (ModelApiException) mapped;
        assertEquals("MODEL_API_ERROR", mae.getErrorCode());
        assertEquals(ErrorCategory.MODEL, mae.getCategory());
        assertFalse(mae.isRetryable());
    }
}
