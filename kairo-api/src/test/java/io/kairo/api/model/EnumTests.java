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

import org.junit.jupiter.api.Test;

class EnumTests {

    @Test
    void apiErrorTypeValues() {
        ApiErrorType[] values = ApiErrorType.values();
        assertEquals(7, values.length);
        assertNotNull(ApiErrorType.valueOf("PROMPT_TOO_LONG"));
        assertNotNull(ApiErrorType.valueOf("MAX_OUTPUT_TOKENS"));
        assertNotNull(ApiErrorType.valueOf("RATE_LIMITED"));
        assertNotNull(ApiErrorType.valueOf("SERVER_ERROR"));
        assertNotNull(ApiErrorType.valueOf("AUTHENTICATION_ERROR"));
        assertNotNull(ApiErrorType.valueOf("BUDGET_EXCEEDED"));
        assertNotNull(ApiErrorType.valueOf("UNKNOWN"));
    }

    @Test
    void streamChunkTypeValues() {
        StreamChunkType[] values = StreamChunkType.values();
        assertEquals(7, values.length);
        assertNotNull(StreamChunkType.valueOf("TEXT"));
        assertNotNull(StreamChunkType.valueOf("THINKING"));
        assertNotNull(StreamChunkType.valueOf("TOOL_USE_START"));
        assertNotNull(StreamChunkType.valueOf("TOOL_USE_DELTA"));
        assertNotNull(StreamChunkType.valueOf("TOOL_USE_END"));
        assertNotNull(StreamChunkType.valueOf("DONE"));
        assertNotNull(StreamChunkType.valueOf("ERROR"));
    }

    @Test
    void toolVerbosityValues() {
        ToolVerbosity[] values = ToolVerbosity.values();
        assertEquals(3, values.length);
        assertNotNull(ToolVerbosity.valueOf("CONCISE"));
        assertNotNull(ToolVerbosity.valueOf("STANDARD"));
        assertNotNull(ToolVerbosity.valueOf("VERBOSE"));
    }

    @Test
    void apiErrorTypeInvalidValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> ApiErrorType.valueOf("NONEXISTENT"));
    }
}
