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

import io.kairo.api.model.ModelConfig;
import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelConfigBuilderTest {

    @Test
    void createWithAllFields() {
        ModelConfig config =
                ModelConfigBuilder.create()
                        .model("gpt-4o")
                        .maxTokens(2048)
                        .temperature(0.5)
                        .thinking(true, 5000)
                        .systemPrompt("You are helpful")
                        .build();
        assertEquals("gpt-4o", config.model());
        assertEquals(2048, config.maxTokens());
        assertEquals(0.5, config.temperature(), 0.001);
        assertTrue(config.thinking().enabled());
        assertEquals(5000, config.thinking().budgetTokens());
        assertEquals("You are helpful", config.systemPrompt());
    }

    @Test
    void claudeSonnetPreset() {
        ModelConfig config = ModelConfigBuilder.claudeSonnet().build();
        assertEquals("claude-sonnet-4-20250514", config.model());
        assertEquals(8096, config.maxTokens());
        assertEquals(1.0, config.temperature(), 0.001);
    }

    @Test
    void claudeSonnetWithThinkingPreset() {
        ModelConfig config = ModelConfigBuilder.claudeSonnetWithThinking(15000).build();
        assertEquals("claude-sonnet-4-20250514", config.model());
        assertTrue(config.thinking().enabled());
        assertEquals(15000, config.thinking().budgetTokens());
    }

    @Test
    void addToolViaBuilder() {
        ToolDefinition tool =
                new ToolDefinition(
                        "bash",
                        "run command",
                        ToolCategory.EXECUTION,
                        new JsonSchema("object", Map.of(), List.of(), null),
                        Object.class);
        ModelConfig config = ModelConfigBuilder.create().model("test").addTool(tool).build();
        assertEquals(1, config.tools().size());
        assertEquals("bash", config.tools().get(0).name());
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
                ModelConfigBuilder.create().model("test").addTool(t1).tools(List.of(t2)).build();
        assertEquals(1, config.tools().size());
        assertEquals("b", config.tools().get(0).name());
    }

    @Test
    void thinkingConfigObject() {
        ModelConfig.ThinkingConfig tc = new ModelConfig.ThinkingConfig(true, 8000);
        ModelConfig config = ModelConfigBuilder.create().model("test").thinking(tc).build();
        assertTrue(config.thinking().enabled());
        assertEquals(8000, config.thinking().budgetTokens());
    }

    @Test
    void modelRequired() {
        assertThrows(NullPointerException.class, () -> ModelConfigBuilder.create().build());
    }
}
