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

import io.kairo.api.exception.KairoException;
import io.kairo.api.exception.MemoryStoreException;
import io.kairo.api.exception.ModelApiException;
import io.kairo.api.exception.ModelRateLimitException;
import org.junit.jupiter.api.Test;

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
}
