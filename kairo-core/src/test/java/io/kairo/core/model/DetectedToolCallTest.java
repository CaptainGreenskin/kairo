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

import java.util.Map;
import org.junit.jupiter.api.Test;

class DetectedToolCallTest {

    @Test
    void fieldAccessorsReturnConstructorValues() {
        Map<String, Object> args = Map.of("file", "foo.txt", "lines", 10);
        DetectedToolCall call = new DetectedToolCall("call-1", "read_file", args, false);
        assertEquals("call-1", call.toolCallId());
        assertEquals("read_file", call.toolName());
        assertEquals(args, call.args());
        assertFalse(call.isLastTool());
    }

    @Test
    void isLastToolTrueReflected() {
        DetectedToolCall call = new DetectedToolCall("call-2", "write_file", Map.of(), true);
        assertTrue(call.isLastTool());
    }

    @Test
    void emptyArgsAllowed() {
        DetectedToolCall call = new DetectedToolCall("id", "bash", Map.of(), false);
        assertTrue(call.args().isEmpty());
    }

    @Test
    void recordEquality() {
        Map<String, Object> args = Map.of("key", "value");
        DetectedToolCall a = new DetectedToolCall("id-x", "tool", args, true);
        DetectedToolCall b = new DetectedToolCall("id-x", "tool", args, true);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void inequalityOnDifferentId() {
        DetectedToolCall a = new DetectedToolCall("id-1", "tool", Map.of(), false);
        DetectedToolCall b = new DetectedToolCall("id-2", "tool", Map.of(), false);
        assertNotEquals(a, b);
    }

    @Test
    void inequalityOnDifferentToolName() {
        DetectedToolCall a = new DetectedToolCall("id", "read", Map.of(), false);
        DetectedToolCall b = new DetectedToolCall("id", "write", Map.of(), false);
        assertNotEquals(a, b);
    }

    @Test
    void toStringContainsToolName() {
        DetectedToolCall call = new DetectedToolCall("id", "grep_tool", Map.of(), false);
        assertTrue(call.toString().contains("grep_tool"));
    }

    @Test
    void toStringContainsToolCallId() {
        DetectedToolCall call = new DetectedToolCall("abc-123", "bash", Map.of(), false);
        assertTrue(call.toString().contains("abc-123"));
    }
}
