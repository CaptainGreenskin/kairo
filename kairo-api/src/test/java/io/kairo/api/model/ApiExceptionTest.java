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
package io.kairo.api.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiExceptionTest {

    @Test
    void constructorAndGetters() {
        Map<String, Object> meta = Map.of("code", 429);
        ApiException ex = new ApiException(ApiErrorType.RATE_LIMITED, "too many requests", meta);

        assertEquals(ApiErrorType.RATE_LIMITED, ex.getErrorType());
        assertEquals("too many requests", ex.getMessage());
        assertEquals(meta, ex.getMetadata());
    }

    @Test
    void extendsRuntimeException() {
        ApiException ex = new ApiException(ApiErrorType.UNKNOWN, "test", null);
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void nullMetadata() {
        ApiException ex = new ApiException(ApiErrorType.SERVER_ERROR, "fail", null);
        assertNull(ex.getMetadata());
    }
}
