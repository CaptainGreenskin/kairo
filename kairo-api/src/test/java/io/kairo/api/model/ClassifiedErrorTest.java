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

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClassifiedErrorTest {

    @Test
    void constructionAndFieldAccess() {
        ClassifiedError error =
                new ClassifiedError(
                        ApiErrorType.RATE_LIMITED,
                        "rate limited",
                        Duration.ofSeconds(30),
                        Map.of("key", "value"));

        assertEquals(ApiErrorType.RATE_LIMITED, error.type());
        assertEquals("rate limited", error.message());
        assertEquals(Duration.ofSeconds(30), error.retryAfter());
        assertEquals(Map.of("key", "value"), error.metadata());
    }

    @Test
    void toExceptionCreatesApiException() {
        Map<String, Object> meta = Map.of("region", "us-east-1");
        ClassifiedError error =
                new ClassifiedError(ApiErrorType.SERVER_ERROR, "internal error", null, meta);

        RuntimeException ex = error.toException();
        assertInstanceOf(ApiException.class, ex);

        ApiException apiEx = (ApiException) ex;
        assertEquals(ApiErrorType.SERVER_ERROR, apiEx.getErrorType());
        assertEquals("internal error", apiEx.getMessage());
        assertEquals(meta, apiEx.getMetadata());
    }

    @Test
    void toExceptionWithNullMetadata() {
        ClassifiedError error = new ClassifiedError(ApiErrorType.UNKNOWN, "unknown", null, null);
        RuntimeException ex = error.toException();
        assertInstanceOf(ApiException.class, ex);
        assertNull(((ApiException) ex).getMetadata());
    }
}
