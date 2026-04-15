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

import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelConfigTest {

    @Test
    void builderWithAllFields() {
        ToolDefinition tool =
                new ToolDefinition(
                        "bash",
                        "run command",
                        ToolCategory.EXECUTION,
                        new JsonSchema("object", Map.of(), List.of(), null),
                        Object.class);
        ModelConfig config =
                ModelConfig.builder()
                        .model("gpt-4o")
                        .maxTokens(2048)
                        .temperature(0.5)
                        .addTool(tool)
                        .thinking(new ModelConfig.ThinkingConfig(true, 8000))
                        .systemPrompt("You are helpful")
                        .build();

        assertEquals("gpt-4o", config.model());
        assertEquals(2048, config.maxTokens());
        assertEquals(0.5, config.temperature(), 0.001);
        assertEquals(1, config.tools().size());
        assertEquals("bash", config.tools().get(0).name());
        assertTrue(config.thinking().enabled());
        assertEquals(8000, config.thinking().budgetTokens());
        assertEquals("You are helpful", config.systemPrompt());
    }

    @Test
    void builderDefaults() {
        ModelConfig config = ModelConfig.builder().model("test-model").build();
        assertEquals(4096, config.maxTokens());
        assertEquals(1.0, config.temperature(), 0.001);
        assertTrue(config.tools().isEmpty());
        assertFalse(config.thinking().enabled());
        assertEquals(0, config.thinking().budgetTokens());
        assertNull(config.systemPrompt());
    }

    @Test
    void modelIsRequired() {
        assertThrows(NullPointerException.class, () -> ModelConfig.builder().build());
    }

    @Test
    void toolsListIsImmutable() {
        ModelConfig config = ModelConfig.builder().model("test").build();
        assertThrows(UnsupportedOperationException.class, () -> config.tools().add(null));
    }

    @Test
    void toolsSetterReplacesTools() {
        ToolDefinition t1 =
                new ToolDefinition(
                        "a",
                        "desc",
                        ToolCategory.GENERAL,
                        new JsonSchema("object", null, null, null),
                        Object.class);
        ToolDefinition t2 =
                new ToolDefinition(
                        "b",
                        "desc",
                        ToolCategory.GENERAL,
                        new JsonSchema("object", null, null, null),
                        Object.class);
        ModelConfig config =
                ModelConfig.builder().model("test").addTool(t1).tools(List.of(t2)).build();
        assertEquals(1, config.tools().size());
        assertEquals("b", config.tools().get(0).name());
    }

    @Test
    void thinkingConfigRecord() {
        ModelConfig.ThinkingConfig tc = new ModelConfig.ThinkingConfig(true, 10000);
        assertTrue(tc.enabled());
        assertEquals(10000, tc.budgetTokens());
        assertEquals(tc, new ModelConfig.ThinkingConfig(true, 10000));
    }
}
