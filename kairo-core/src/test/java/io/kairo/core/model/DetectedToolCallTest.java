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
    void storesToolCallId() {
        DetectedToolCall call = new DetectedToolCall("id-1", "bash", Map.of(), false);
        assertThat(call.toolCallId()).isEqualTo("id-1");
    }

    @Test
    void storesToolName() {
        DetectedToolCall call = new DetectedToolCall("id-1", "bash", Map.of(), false);
        assertThat(call.toolName()).isEqualTo("bash");
    }

    @Test
    void storesArgs() {
        Map<String, Object> args = Map.of("command", "ls");
        DetectedToolCall call = new DetectedToolCall("id-1", "bash", args, false);
        assertThat(call.args()).containsEntry("command", "ls");
    }

    @Test
    void isLastToolFalse() {
        DetectedToolCall call = new DetectedToolCall("id-1", "bash", Map.of(), false);
        assertThat(call.isLastTool()).isFalse();
    }

    @Test
    void isLastToolTrue() {
        DetectedToolCall call = new DetectedToolCall("id-1", "bash", Map.of(), true);
        assertThat(call.isLastTool()).isTrue();
    }

    @Test
    void equalityByFields() {
        Map<String, Object> args = Map.of("k", "v");
        DetectedToolCall a = new DetectedToolCall("id-1", "tool", args, true);
        DetectedToolCall b = new DetectedToolCall("id-1", "tool", args, true);
        assertThat(a).isEqualTo(b);
    }
}
