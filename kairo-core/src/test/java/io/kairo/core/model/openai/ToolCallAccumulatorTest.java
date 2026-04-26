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
    void initialIdIsNull() {
        assertThat(new ToolCallAccumulator().id).isNull();
    }

    @Test
    void initialNameIsNull() {
        assertThat(new ToolCallAccumulator().name).isNull();
    }

    @Test
    void initialArgumentsIsEmpty() {
        assertThat(new ToolCallAccumulator().arguments.toString()).isEmpty();
    }

    @Test
    void idCanBeSet() {
        ToolCallAccumulator acc = new ToolCallAccumulator();
        acc.id = "call-123";
        assertThat(acc.id).isEqualTo("call-123");
    }

    @Test
    void nameCanBeSet() {
        ToolCallAccumulator acc = new ToolCallAccumulator();
        acc.name = "bash";
        assertThat(acc.name).isEqualTo("bash");
    }

    @Test
    void argumentsAccumulateAcrossAppends() {
        ToolCallAccumulator acc = new ToolCallAccumulator();
        acc.arguments.append("{\"cmd\":");
        acc.arguments.append("\"ls\"}");
        assertThat(acc.arguments.toString()).isEqualTo("{\"cmd\":\"ls\"}");
    }

    @Test
    void multipleAccumulatorsAreIndependent() {
        ToolCallAccumulator a = new ToolCallAccumulator();
        ToolCallAccumulator b = new ToolCallAccumulator();
        a.id = "id-a";
        b.id = "id-b";
        assertThat(a.id).isEqualTo("id-a");
        assertThat(b.id).isEqualTo("id-b");
    }

    @Test
    void argumentsBuilderIsNotShared() {
        ToolCallAccumulator a = new ToolCallAccumulator();
        ToolCallAccumulator b = new ToolCallAccumulator();
        a.arguments.append("argA");
        assertThat(b.arguments.toString()).isEmpty();
    }
}
