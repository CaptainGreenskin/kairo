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
package io.kairo.core.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ToolCallAccumulatorTest {

    @Test
    void initialState_idIsNull() {
        var acc = new ToolCallAccumulator();
        assertThat(acc.id).isNull();
    }

    @Test
    void initialState_nameIsNull() {
        var acc = new ToolCallAccumulator();
        assertThat(acc.name).isNull();
    }

    @Test
    void initialState_argumentsIsEmpty() {
        var acc = new ToolCallAccumulator();
        assertThat(acc.arguments.length()).isZero();
    }

    @Test
    void canSetId() {
        var acc = new ToolCallAccumulator();
        acc.id = "call_123";
        assertThat(acc.id).isEqualTo("call_123");
    }

    @Test
    void canSetName() {
        var acc = new ToolCallAccumulator();
        acc.name = "bash_tool";
        assertThat(acc.name).isEqualTo("bash_tool");
    }

    @Test
    void appendsArgumentsIncrementally() {
        var acc = new ToolCallAccumulator();
        acc.arguments.append("{\"cmd\":");
        acc.arguments.append("\"ls\"}");
        assertThat(acc.arguments.toString()).isEqualTo("{\"cmd\":\"ls\"}");
    }
}
