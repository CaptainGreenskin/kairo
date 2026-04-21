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

import io.kairo.api.exception.KairoException;
import io.kairo.api.exception.MemoryStoreException;
import io.kairo.api.exception.ModelApiException;
import io.kairo.api.exception.ModelRateLimitException;
import reactor.core.Exceptions;

/**
 * Maps core-internal exceptions to public API exception types at module boundaries.
 *
 * <p>Designed to be used as a reactive boundary operator: {@code
 * .onErrorMap(ExceptionMapper::toApiException)}.
 */
public final class ExceptionMapper {

    private ExceptionMapper() {}

    /**
     * Map a model-layer throwable to the appropriate API exception type.
     *
     * <p>Pass-through for exceptions that are already API-layer types ({@link KairoException}
     * hierarchy). Maps internal {@link ModelProviderException} subtypes to their public API
     * equivalents.
     *
     * @param e the throwable to map
     * @return the mapped API-layer exception
     */
    public static Throwable toApiException(Throwable e) {
        if (e instanceof KairoException) return e; // already API-layer, pass through
        // Unwrap RetryExhaustedException to map the underlying cause
        if (Exceptions.isRetryExhausted(e) && e.getCause() != null) {
            return toApiException(e.getCause());
        }
        if (e instanceof ModelProviderException.RateLimitException) {
            return new ModelRateLimitException(e.getMessage(), e);
        }
        if (e instanceof ModelProviderException.ApiException) {
            return new ModelApiException(e.getMessage(), e);
        }
        return new KairoException("Unexpected error", e);
    }

    /**
     * Map a storage-layer throwable to a {@link MemoryStoreException}.
     *
     * <p>Pass-through for exceptions that are already API-layer types ({@link KairoException}
     * hierarchy).
     *
     * @param e the throwable to map
     * @return the mapped storage exception
     */
    public static Throwable toStorageException(Throwable e) {
        if (e instanceof KairoException) return e;
        return new MemoryStoreException(e.getMessage(), e);
    }
}
