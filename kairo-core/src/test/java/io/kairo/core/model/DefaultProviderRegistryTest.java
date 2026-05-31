/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.model.ProviderRegistry;
import io.kairo.api.model.ProviderSpec;
import org.junit.jupiter.api.Test;

class DefaultProviderRegistryTest {

    private static final String KEY = "test-api-key-12345";

    @Test
    void builtInsContainAllExpectedProviders() {
        ProviderRegistry registry = DefaultProviderRegistry.withBuiltIns();
        assertThat(registry.names())
                .contains(
                        "anthropic",
                        "openai",
                        "gemini",
                        "google",
                        "qwen",
                        "glm",
                        "deepseek",
                        "minimax",
                        "kimi",
                        "moonshot",
                        "groq",
                        "xai",
                        "grok",
                        "openrouter",
                        "ollama",
                        "lm-studio",
                        "openai-compatible");
    }

    @Test
    void builtInsContainNewOpenAiCompatibleProviders() {
        ProviderRegistry registry = DefaultProviderRegistry.withBuiltIns();
        assertThat(registry.names())
                .contains(
                        "together",
                        "fireworks",
                        "novita",
                        "nvidia",
                        "stepfun",
                        "perplexity",
                        "cerebras",
                        "mistral");
        // each resolves to an OpenAI-compatible provider (name() == "openai")
        for (String name :
                new String[] {
                    "together", "fireworks", "novita", "nvidia",
                    "stepfun", "perplexity", "cerebras", "mistral"
                }) {
            assertThat(registry.create(name, ProviderSpec.of(KEY)).name())
                    .as("provider %s", name)
                    .isEqualTo("openai");
        }
    }

    @Test
    void createIsCaseInsensitive() {
        ProviderRegistry registry = DefaultProviderRegistry.withBuiltIns();
        assertThat(registry.create("OpenAI", ProviderSpec.of(KEY)).name()).isEqualTo("openai");
        assertThat(registry.create("ANTHROPIC", ProviderSpec.of(KEY)).name())
                .isEqualTo("anthropic");
    }

    @Test
    void unknownProviderThrows() {
        ProviderRegistry registry = DefaultProviderRegistry.withBuiltIns();
        assertThatThrownBy(() -> registry.create("nonexistent-llm-vendor", ProviderSpec.of(KEY)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown provider");
    }

    @Test
    void apiKeyRequiredForKeyOnlyProviders() {
        ProviderRegistry registry = DefaultProviderRegistry.withBuiltIns();
        assertThatThrownBy(() -> registry.create("openai", ProviderSpec.of(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("API key");
    }

    @Test
    void ollamaWorksWithoutApiKey() {
        ProviderRegistry registry = DefaultProviderRegistry.withBuiltIns();
        assertThat(registry.create("ollama", ProviderSpec.of(null)).name()).isEqualTo("openai");
    }

    @Test
    void ollamaHonorsCustomBaseUrl() throws Exception {
        ProviderRegistry registry = DefaultProviderRegistry.withBuiltIns();
        var provider = registry.create("ollama", ProviderSpec.of(null, "http://my-host:9999"));
        var field = provider.getClass().getDeclaredField("baseUrl");
        field.setAccessible(true);
        assertThat(field.get(provider)).isEqualTo("http://my-host:9999");
    }

    @Test
    void openaiCompatibleRequiresBaseUrl() {
        ProviderRegistry registry = DefaultProviderRegistry.withBuiltIns();
        assertThatThrownBy(() -> registry.create("openai-compatible", ProviderSpec.of(KEY)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseUrl");
    }

    @Test
    void openaiCompatibleAcceptsKeyAndBaseUrl() {
        ProviderRegistry registry = DefaultProviderRegistry.withBuiltIns();
        var p =
                registry.create(
                        "openai-compatible",
                        ProviderSpec.of(KEY, "https://my-corp-llm.example.com"));
        assertThat(p.name()).isEqualTo("openai");
    }

    @Test
    void aliasesPointToSameImplementation() {
        ProviderRegistry registry = DefaultProviderRegistry.withBuiltIns();
        // kimi / moonshot both → OpenAIProvider pointed at Moonshot
        assertThat(registry.create("kimi", ProviderSpec.of(KEY)).getClass())
                .isEqualTo(registry.create("moonshot", ProviderSpec.of(KEY)).getClass());
        // xai / grok both → OpenAIProvider pointed at xAI
        assertThat(registry.create("xai", ProviderSpec.of(KEY)).getClass())
                .isEqualTo(registry.create("grok", ProviderSpec.of(KEY)).getClass());
        // gemini / google both → GeminiProvider
        assertThat(registry.create("gemini", ProviderSpec.of(KEY)).getClass())
                .isEqualTo(registry.create("google", ProviderSpec.of(KEY)).getClass());
    }

    @Test
    void registerCustomFactoryWorks() {
        DefaultProviderRegistry registry = new DefaultProviderRegistry();
        registry.registerApiKeyOnly("custom", k -> ProviderPresets.openai(k));
        assertThat(registry.create("custom", ProviderSpec.of(KEY)).name()).isEqualTo("openai");
    }

    @Test
    void emptyRegistryRejectsAnyName() {
        DefaultProviderRegistry registry = new DefaultProviderRegistry();
        assertThatThrownBy(() -> registry.create("anthropic", ProviderSpec.of(KEY)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
