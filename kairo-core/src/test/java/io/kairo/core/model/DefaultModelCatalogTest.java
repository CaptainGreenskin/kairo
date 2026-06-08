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

import io.kairo.api.model.ModelCapability;
import io.kairo.api.model.ModelInfo;
import io.kairo.api.model.NoopModelCatalog;
import io.kairo.api.model.ToolVerbosity;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultModelCatalogTest {

    private DefaultModelCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = DefaultModelCatalog.withBuiltIns();
    }

    @Test
    void resolveExactMatch() {
        Optional<ModelInfo> info = catalog.resolve("claude-sonnet-4");
        assertTrue(info.isPresent());
        assertEquals("anthropic", info.get().providerName());
        assertEquals("claude-sonnet-4", info.get().canonicalModelId());
    }

    @Test
    void resolvePrefixMatch() {
        Optional<ModelInfo> info = catalog.resolve("claude-sonnet-4-20250514");
        assertTrue(info.isPresent());
        assertEquals("anthropic", info.get().providerName());
        assertEquals("claude-sonnet-4-20250514", info.get().canonicalModelId());
    }

    @Test
    void resolveAlias() {
        Optional<ModelInfo> info = catalog.resolve("sonnet");
        assertTrue(info.isPresent());
        assertEquals("anthropic", info.get().providerName());
    }

    @Test
    void resolveOpusAlias() {
        Optional<ModelInfo> info = catalog.resolve("opus");
        assertTrue(info.isPresent());
        assertEquals("anthropic", info.get().providerName());
    }

    @Test
    void resolveGptModel() {
        Optional<ModelInfo> info = catalog.resolve("gpt-4o");
        assertTrue(info.isPresent());
        assertEquals("openai", info.get().providerName());
    }

    @Test
    void resolveGlmModel() {
        Optional<ModelInfo> info = catalog.resolve("glm-4-plus");
        assertTrue(info.isPresent());
        assertEquals("glm", info.get().providerName());
    }

    @Test
    void resolveDeepSeekModel() {
        Optional<ModelInfo> info = catalog.resolve("deepseek-chat");
        assertTrue(info.isPresent());
        assertEquals("deepseek", info.get().providerName());
    }

    @Test
    void resolveGeminiModel() {
        Optional<ModelInfo> info = catalog.resolve("gemini-2.5-pro");
        assertTrue(info.isPresent());
        assertEquals("gemini", info.get().providerName());
    }

    @Test
    void resolveInferredProvider() {
        Optional<ModelInfo> info = catalog.resolve("claude-unknown-future-model");
        assertTrue(info.isPresent());
        assertEquals("anthropic", info.get().providerName());
        assertEquals("claude-unknown-future-model", info.get().canonicalModelId());
    }

    @Test
    void resolveUnknownModelReturnsEmpty() {
        Optional<ModelInfo> info = catalog.resolve("totally-unknown-xyz");
        assertTrue(info.isEmpty());
    }

    @Test
    void resolveNullReturnsEmpty() {
        assertTrue(catalog.resolve(null).isEmpty());
    }

    @Test
    void resolveBlankReturnsEmpty() {
        assertTrue(catalog.resolve("  ").isEmpty());
    }

    @Test
    void registerCustomModel() {
        ModelCapability cap =
                new ModelCapability(
                        "custom", "v1", 32_000, 4096, false, false, ToolVerbosity.STANDARD, null);
        catalog.register("my-custom-model", new ModelInfo("my-provider", "my-custom-model", cap));

        Optional<ModelInfo> info = catalog.resolve("my-custom-model");
        assertTrue(info.isPresent());
        assertEquals("my-provider", info.get().providerName());
        assertEquals(32_000, info.get().capability().contextWindow());
    }

    @Test
    void registerAlias() {
        catalog.registerAlias("fast", "gpt-4o-mini");

        Optional<ModelInfo> info = catalog.resolve("fast");
        assertTrue(info.isPresent());
        assertEquals("openai", info.get().providerName());
    }

    @Test
    void knownModelsIsNonEmpty() {
        assertFalse(catalog.knownModels().isEmpty());
        assertTrue(catalog.knownModels().contains("claude-sonnet-4"));
        assertTrue(catalog.knownModels().contains("gpt-4o"));
    }

    @Test
    void noopCatalogResolvesNothing() {
        NoopModelCatalog noop = NoopModelCatalog.INSTANCE;
        assertTrue(noop.resolve("claude-sonnet-4").isEmpty());
        assertTrue(noop.knownModels().isEmpty());
    }

    @Test
    void aliasIsCaseInsensitive() {
        Optional<ModelInfo> info = catalog.resolve("SONNET");
        assertTrue(info.isPresent());
        assertEquals("anthropic", info.get().providerName());
    }

    @Test
    void qwenModelResolvesCorrectly() {
        Optional<ModelInfo> info = catalog.resolve("qwen-max");
        assertTrue(info.isPresent());
        assertEquals("qwen", info.get().providerName());
    }
}
