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
package io.kairo.core.prompt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.kairo.api.model.ModelCapability;
import io.kairo.api.model.ToolVerbosity;
import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolRegistry;
import io.kairo.api.tool.ToolSideEffect;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for Task 2.9 — System Prompt Enhancement: tool usage guidance and model-specific prompt
 * guidance.
 */
class SystemPromptEnhancementTest {

    // ---- Part A: Tool Usage Guidance ----

    @Test
    void usageGuidanceAppearsInToolOverview() {
        ToolRegistry registry = mock(ToolRegistry.class);
        ToolDefinition tool =
                new ToolDefinition(
                        "read_file",
                        "Read a file from disk",
                        ToolCategory.FILE_AND_CODE,
                        new JsonSchema("object", Map.of(), List.of(), null),
                        Object.class,
                        null,
                        ToolSideEffect.READ_ONLY,
                        "Use for quick file reads; for large files use GrepTool instead");
        when(registry.getAll()).thenReturn(List.of(tool));

        String prompt =
                SystemPromptBuilder.create()
                        .section("identity", "You are a helper.")
                        .addToolOverview(registry)
                        .build();

        assertTrue(prompt.contains("read_file"));
        assertTrue(prompt.contains("Read a file from disk"));
        assertTrue(
                prompt.contains(
                        "Usage guidance: Use for quick file reads; for large files use GrepTool instead"));
    }

    @Test
    void emptyUsageGuidanceNotShown() {
        ToolRegistry registry = mock(ToolRegistry.class);
        ToolDefinition tool =
                new ToolDefinition(
                        "bash",
                        "Run shell commands",
                        ToolCategory.EXECUTION,
                        new JsonSchema("object", Map.of(), List.of(), null),
                        Object.class,
                        null,
                        ToolSideEffect.SYSTEM_CHANGE,
                        "");
        when(registry.getAll()).thenReturn(List.of(tool));

        String prompt =
                SystemPromptBuilder.create()
                        .section("identity", "You are a helper.")
                        .addToolOverview(registry)
                        .build();

        assertTrue(prompt.contains("bash"));
        assertTrue(prompt.contains("Run shell commands"));
        assertFalse(prompt.contains("Usage guidance:"));
    }

    @Test
    void nullUsageGuidanceNotShown() {
        ToolRegistry registry = mock(ToolRegistry.class);
        // Use backward-compat constructor (no usageGuidance) — defaults to ""
        ToolDefinition tool =
                new ToolDefinition(
                        "list_dir",
                        "List directory contents",
                        ToolCategory.FILE_AND_CODE,
                        new JsonSchema("object", Map.of(), List.of(), null),
                        Object.class);
        when(registry.getAll()).thenReturn(List.of(tool));

        String prompt =
                SystemPromptBuilder.create()
                        .section("identity", "Helper")
                        .addToolOverview(registry)
                        .build();

        assertTrue(prompt.contains("list_dir"));
        assertFalse(prompt.contains("Usage guidance:"));
    }

    @Test
    void backwardCompatToolDefinitionStillWorks() {
        // 5-param constructor
        ToolDefinition def5 =
                new ToolDefinition(
                        "tool_a", "desc", ToolCategory.GENERAL, null, Object.class);
        assertEquals("", def5.usageGuidance());
        assertEquals(ToolSideEffect.READ_ONLY, def5.sideEffect());

        // 6-param constructor
        ToolDefinition def6 =
                new ToolDefinition(
                        "tool_b", "desc", ToolCategory.GENERAL, null, Object.class, null);
        assertEquals("", def6.usageGuidance());

        // 7-param constructor
        ToolDefinition def7 =
                new ToolDefinition(
                        "tool_c", "desc", ToolCategory.GENERAL, null, Object.class, null,
                        ToolSideEffect.SYSTEM_CHANGE);
        assertEquals("", def7.usageGuidance());
        assertEquals(ToolSideEffect.SYSTEM_CHANGE, def7.sideEffect());
    }

    // ---- Part B: Model-Specific Prompt Guidance ----

    @Test
    void gptModelGuidanceInjectedIntoPrompt() {
        ModelCapability gpt =
                new ModelCapability(
                        "gpt", "4o", 128_000, 4096, false, false, ToolVerbosity.STANDARD, null);

        String prompt =
                SystemPromptBuilder.create()
                        .forModel(gpt)
                        .section("identity", "You are a helper.")
                        .build();

        assertTrue(
                prompt.contains(
                        "Always use tools to take action; do not describe what you would do."));
    }

    @Test
    void geminiModelGuidanceInjectedIntoPrompt() {
        ModelCapability gemini =
                new ModelCapability(
                        "gemini", "1.5-pro", 1_000_000, 8192, false, false,
                        ToolVerbosity.STANDARD, null);

        String prompt =
                SystemPromptBuilder.create()
                        .forModel(gemini)
                        .section("identity", "You are a helper.")
                        .build();

        assertTrue(prompt.contains("Use absolute paths; read files before modifying them."));
    }

    @Test
    void claudeModelNoGuidanceInjected() {
        ModelCapability claude =
                new ModelCapability(
                        "claude", "sonnet", 200_000, 8192, true, true,
                        ToolVerbosity.STANDARD, null);

        String prompt =
                SystemPromptBuilder.create()
                        .forModel(claude)
                        .section("identity", "You are a helper.")
                        .build();

        assertFalse(prompt.contains("# Model-guidance"));
    }

    @Test
    void noModelCapabilityNoGuidance() {
        String prompt =
                SystemPromptBuilder.create()
                        .section("identity", "You are a helper.")
                        .build();

        assertFalse(prompt.contains("model-guidance"));
        assertFalse(prompt.contains("Model-guidance"));
    }

    @Test
    void customPromptGuidanceOverridesDefault() {
        ModelCapability custom =
                new ModelCapability(
                        "gpt", "4o", 128_000, 4096, false, false,
                        ToolVerbosity.STANDARD, (io.kairo.api.model.IntRange) null,
                        "My custom model guidance");

        String prompt =
                SystemPromptBuilder.create()
                        .forModel(custom)
                        .section("identity", "You are a helper.")
                        .build();

        assertTrue(prompt.contains("My custom model guidance"));
        assertFalse(
                prompt.contains(
                        "Always use tools to take action; do not describe what you would do."));
    }

    @Test
    void modelGuidanceInjectedOnceEvenWithBuildResultThenBuild() {
        ModelCapability gpt =
                new ModelCapability(
                        "gpt", "4o", 128_000, 4096, false, false, ToolVerbosity.STANDARD, null);

        SystemPromptBuilder builder =
                SystemPromptBuilder.create()
                        .forModel(gpt)
                        .section("identity", "You are a helper.");

        // Build twice — guidance should appear only once
        SystemPromptResult result = builder.buildResult();
        String fullPrompt = result.fullPrompt();

        int firstIdx = fullPrompt.indexOf("Always use tools to take action");
        int lastIdx = fullPrompt.lastIndexOf("Always use tools to take action");
        assertEquals(firstIdx, lastIdx, "Model guidance should appear only once");
    }

    // ---- Combined: tool guidance + model guidance ----

    @Test
    void bothToolGuidanceAndModelGuidancePresent() {
        ToolRegistry registry = mock(ToolRegistry.class);
        ToolDefinition tool =
                new ToolDefinition(
                        "bash",
                        "Run shell commands",
                        ToolCategory.EXECUTION,
                        new JsonSchema("object", Map.of(), List.of(), null),
                        Object.class,
                        null,
                        ToolSideEffect.SYSTEM_CHANGE,
                        "Danger: may modify system state");
        when(registry.getAll()).thenReturn(List.of(tool));

        ModelCapability gpt =
                new ModelCapability(
                        "gpt", "4o", 128_000, 4096, false, false, ToolVerbosity.STANDARD, null);

        String prompt =
                SystemPromptBuilder.create()
                        .forModel(gpt)
                        .section("identity", "You are a helper.")
                        .addToolOverview(registry)
                        .build();

        assertTrue(prompt.contains("Usage guidance: Danger: may modify system state"));
        assertTrue(
                prompt.contains(
                        "Always use tools to take action; do not describe what you would do."));
    }
}
