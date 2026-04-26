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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DetectedToolCallTest {

    @Test
    void constructorDoesNotThrow() {
        DetectedToolCall tc = new DetectedToolCall("id-1", "bash", Map.of("cmd", "ls"), false);
        assertThat(tc).isNotNull();
    }

    @Test
    void toolCallIdAccessor() {
        DetectedToolCall tc = new DetectedToolCall("call-42", "read", Map.of(), true);
        assertThat(tc.toolCallId()).isEqualTo("call-42");
    }

    @Test
    void toolNameAccessor() {
        DetectedToolCall tc = new DetectedToolCall("id", "write_file", Map.of(), false);
        assertThat(tc.toolName()).isEqualTo("write_file");
    }

    @Test
    void argsAccessor() {
        Map<String, Object> args = Map.of("path", "/tmp/a", "content", "hello");
        DetectedToolCall tc = new DetectedToolCall("id", "write", args, false);
        assertThat(tc.args()).containsEntry("path", "/tmp/a");
    }

    @Test
    void isLastToolAccessorTrue() {
        DetectedToolCall tc = new DetectedToolCall("id", "tool", Map.of(), true);
        assertThat(tc.isLastTool()).isTrue();
    }

    @Test
    void isLastToolAccessorFalse() {
        DetectedToolCall tc = new DetectedToolCall("id", "tool", Map.of(), false);
        assertThat(tc.isLastTool()).isFalse();
    }

    @Test
    void recordEquality() {
        Map<String, Object> args = Map.of("x", 1);
        DetectedToolCall a = new DetectedToolCall("id", "tool", args, false);
        DetectedToolCall b = new DetectedToolCall("id", "tool", args, false);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void recordInequalityOnDifferentId() {
        DetectedToolCall a = new DetectedToolCall("id-1", "tool", Map.of(), false);
        DetectedToolCall b = new DetectedToolCall("id-2", "tool", Map.of(), false);
        assertThat(a).isNotEqualTo(b);
    }
}
