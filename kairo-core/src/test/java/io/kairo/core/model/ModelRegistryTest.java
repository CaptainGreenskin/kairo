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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ModelRegistryTest {

    @Test
    @DisplayName("Known model lookup: claude-sonnet-4-20250514 has 200K context window")
    void testKnownModelContextWindow() {
        assertEquals(200_000, ModelRegistry.getContextWindow("claude-sonnet-4-20250514"));
    }

    @Test
    @DisplayName("Known model max output: gpt-4o has 16384 max output tokens")
    void testKnownModelMaxOutput() {
        assertEquals(16_384, ModelRegistry.getMaxOutputTokens("gpt-4o"));
    }

    @Test
    @DisplayName("Known model max output: claude-sonnet-4-20250514 has 20000 max output tokens")
    void testClaudeSonnet4MaxOutput() {
        assertEquals(20_000, ModelRegistry.getMaxOutputTokens("claude-sonnet-4-20250514"));
    }

    @Test
    @DisplayName("Unknown model falls back to defaults: 128K context, 8192 max output")
    void testUnknownModelFallsBackToDefaults() {
        assertEquals(128_000, ModelRegistry.getContextWindow("unknown-model-xyz"));
        assertEquals(8_192, ModelRegistry.getMaxOutputTokens("unknown-model-xyz"));
    }

    @Test
    @DisplayName("Null model ID falls back to defaults")
    void testNullModelId() {
        ModelRegistry.ModelSpec spec = ModelRegistry.getSpec(null);
        assertEquals(128_000, spec.contextWindow());
        assertEquals(8_192, spec.maxOutputTokens());
    }

    @Test
    @DisplayName("Prefix matching: extended model ID resolves to base model spec")
    void testPrefixMatchingExtendedModelId() {
        // "claude-sonnet-4-20250514-extended" starts with "claude-sonnet-4-20250514"
        int contextWindow = ModelRegistry.getContextWindow("claude-sonnet-4-20250514-extended");
        assertEquals(200_000, contextWindow);

        int maxOutput = ModelRegistry.getMaxOutputTokens("claude-sonnet-4-20250514-extended");
        assertEquals(20_000, maxOutput);
    }

    @Test
    @DisplayName("Prefix matching: short prefix resolves to best match")
    void testPrefixMatchingShortPrefix() {
        // "gpt-4o" is a registered key; "gpt-4o-mini" is also registered
        // "gpt-4o-2024" starts with registered key "gpt-4o" — should match
        int contextWindow = ModelRegistry.getContextWindow("gpt-4o-2024");
        assertEquals(128_000, contextWindow);
    }

    @Test
    @DisplayName("Custom registration: register and look up a custom model")
    void testCustomRegistration() {
        ModelRegistry.register("my-custom-model", 500_000, 32_000);

        assertEquals(500_000, ModelRegistry.getContextWindow("my-custom-model"));
        assertEquals(32_000, ModelRegistry.getMaxOutputTokens("my-custom-model"));

        // Full spec
        ModelRegistry.ModelSpec spec = ModelRegistry.getSpec("my-custom-model");
        assertEquals(500_000, spec.contextWindow());
        assertEquals(32_000, spec.maxOutputTokens());
    }

    @Test
    @DisplayName("getSpec returns correct ModelSpec for known model")
    void testGetSpecKnownModel() {
        ModelRegistry.ModelSpec spec = ModelRegistry.getSpec("deepseek-chat");
        assertEquals(64_000, spec.contextWindow());
        assertEquals(8_192, spec.maxOutputTokens());
    }

    @Test
    @DisplayName("Multiple Claude models have distinct specs")
    void testMultipleClaudeModels() {
        assertEquals(200_000, ModelRegistry.getContextWindow("claude-3-5-sonnet-20241022"));
        assertEquals(8_192, ModelRegistry.getMaxOutputTokens("claude-3-5-sonnet-20241022"));

        assertEquals(200_000, ModelRegistry.getContextWindow("claude-3-haiku-20240307"));
        assertEquals(4_096, ModelRegistry.getMaxOutputTokens("claude-3-haiku-20240307"));
    }
}
