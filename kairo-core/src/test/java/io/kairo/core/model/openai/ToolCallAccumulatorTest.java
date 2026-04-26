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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ToolCallAccumulatorTest {

    private ToolCallAccumulator acc;

    @BeforeEach
    void setUp() {
        acc = new ToolCallAccumulator();
    }

    @Test
    void initialIdIsNull() {
        assertNull(acc.id);
    }

    @Test
    void initialNameIsNull() {
        assertNull(acc.name);
    }

    @Test
    void initialArgumentsIsEmptyNotNull() {
        assertNotNull(acc.arguments);
        assertEquals(0, acc.arguments.length());
    }

    @Test
    void canSetId() {
        acc.id = "call-abc";
        assertEquals("call-abc", acc.id);
    }

    @Test
    void canSetName() {
        acc.name = "bash";
        assertEquals("bash", acc.name);
    }

    @Test
    void singleAppendToArguments() {
        acc.arguments.append("{\"cmd\":\"ls\"}");
        assertEquals("{\"cmd\":\"ls\"}", acc.arguments.toString());
    }

    @Test
    void multipleAppendsAreConcatenated() {
        acc.arguments.append("{\"cmd\":");
        acc.arguments.append("\"ls\"}");
        assertEquals("{\"cmd\":\"ls\"}", acc.arguments.toString());
    }

    @Test
    void argumentsLengthGrowsWithAppends() {
        acc.arguments.append("abc");
        assertEquals(3, acc.arguments.length());
        acc.arguments.append("def");
        assertEquals(6, acc.arguments.length());
    }

    @Test
    void fullAccumulationScenario() {
        acc.id = "tc-1";
        acc.name = "write_file";
        acc.arguments.append("{\"path\":");
        acc.arguments.append("\"foo.txt\"}");
        assertEquals("tc-1", acc.id);
        assertEquals("write_file", acc.name);
        assertEquals("{\"path\":\"foo.txt\"}", acc.arguments.toString());
    }
}
