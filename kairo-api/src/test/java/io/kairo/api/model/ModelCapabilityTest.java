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

class ModelCapabilityTest {

    @Test
    void thinkingModelWithBudgetRange() {
        IntRange budget = new IntRange(1024, 8192);
        ModelCapability cap =
                new ModelCapability(
                        "claude",
                        "sonnet",
                        200_000,
                        8192,
                        true,
                        true,
                        ToolVerbosity.STANDARD,
                        budget);

        assertEquals("claude", cap.modelFamily());
        assertEquals("sonnet", cap.modelTier());
        assertEquals(200_000, cap.contextWindow());
        assertEquals(8192, cap.maxOutputTokens());
        assertTrue(cap.supportsThinking());
        assertTrue(cap.supportsCaching());
        assertEquals(ToolVerbosity.STANDARD, cap.toolVerbosity());
        assertEquals(budget, cap.thinkingBudgetRange());
    }

    @Test
    void nonThinkingModelNullBudgetRange() {
        ModelCapability cap =
                new ModelCapability(
                        "gpt", "4o-mini", 128_000, 4096, false, false, ToolVerbosity.CONCISE, null);

        assertFalse(cap.supportsThinking());
        assertNull(cap.thinkingBudgetRange());
    }

    @Test
    void backwardCompatConstructorDeriveGptPromptGuidance() {
        ModelCapability cap =
                new ModelCapability(
                        "gpt", "4o", 128_000, 4096, false, false, ToolVerbosity.STANDARD, null);
        assertEquals(
                "Always use tools to take action; do not describe what you would do.",
                cap.promptGuidance());
    }

    @Test
    void backwardCompatConstructorDeriveGeminiPromptGuidance() {
        ModelCapability cap =
                new ModelCapability(
                        "gemini",
                        "1.5-pro",
                        1_000_000,
                        8192,
                        false,
                        false,
                        ToolVerbosity.STANDARD,
                        null);
        assertEquals("Use absolute paths; read files before modifying them.", cap.promptGuidance());
    }

    @Test
    void backwardCompatConstructorClaudeNoGuidance() {
        ModelCapability cap =
                new ModelCapability(
                        "claude",
                        "sonnet",
                        200_000,
                        8192,
                        true,
                        true,
                        ToolVerbosity.STANDARD,
                        new IntRange(1024, 8192));
        assertEquals("", cap.promptGuidance());
    }

    @Test
    void canonicalConstructorWithExplicitPromptGuidance() {
        ModelCapability cap =
                new ModelCapability(
                        "gpt",
                        "4o",
                        128_000,
                        4096,
                        false,
                        false,
                        ToolVerbosity.STANDARD,
                        null,
                        "Custom guidance");
        assertEquals("Custom guidance", cap.promptGuidance());
    }

    @Test
    void defaultPromptGuidanceForCodexFamily() {
        assertEquals(
                "Always use tools to take action; do not describe what you would do.",
                ModelCapability.defaultPromptGuidance("codex"));
    }

    @Test
    void defaultPromptGuidanceForNullFamily() {
        assertEquals("", ModelCapability.defaultPromptGuidance(null));
    }

    @Test
    void defaultPromptGuidanceForGlmFamily() {
        assertEquals("", ModelCapability.defaultPromptGuidance("glm"));
    }
}
