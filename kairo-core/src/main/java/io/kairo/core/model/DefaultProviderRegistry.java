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

import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ProviderFactory;
import io.kairo.api.model.ProviderRegistry;
import io.kairo.api.model.ProviderSpec;
import io.kairo.core.model.openai.OpenAIProvider;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * In-memory {@link ProviderRegistry}. {@link #withBuiltIns()} pre-registers all entries in {@link
 * ProviderPresets} under their canonical names so callers can do {@code
 * DefaultProviderRegistry.withBuiltIns().create("minimax", spec)} without further setup.
 *
 * <p>Local-only providers ({@code ollama}, {@code lm-studio}) accept any non-null api key and use
 * {@code baseUrl} if provided. {@code openai-compatible} is a generic escape hatch — {@code
 * baseUrl} is required, otherwise it throws.
 */
public final class DefaultProviderRegistry implements ProviderRegistry {

    private final Map<String, ProviderFactory> factories = new LinkedHashMap<>();

    public DefaultProviderRegistry() {}

    /** Factory with every built-in preset registered. */
    public static DefaultProviderRegistry withBuiltIns() {
        DefaultProviderRegistry r = new DefaultProviderRegistry();
        r.registerApiKeyOnly("anthropic", ProviderPresets::anthropic);
        r.registerApiKeyOnly("openai", ProviderPresets::openai);
        r.registerApiKeyOnly("gemini", ProviderPresets::gemini);
        r.registerApiKeyOnly("google", ProviderPresets::gemini);
        r.registerApiKeyOnly("qwen", ProviderPresets::qwen);
        r.registerApiKeyOnly("glm", ProviderPresets::glm);
        r.registerApiKeyOnly("deepseek", ProviderPresets::deepseek);
        r.registerApiKeyOnly("minimax", ProviderPresets::minimax);
        r.registerApiKeyOnly("kimi", ProviderPresets::kimi);
        r.registerApiKeyOnly("moonshot", ProviderPresets::kimi);
        r.registerApiKeyOnly("groq", ProviderPresets::groq);
        r.registerApiKeyOnly("xai", ProviderPresets::xai);
        r.registerApiKeyOnly("grok", ProviderPresets::xai);
        r.registerApiKeyOnly("openrouter", ProviderPresets::openrouter);

        // Local providers — api key ignored, base URL optional.
        r.register(
                "ollama",
                spec ->
                        spec.baseUrl() == null
                                ? ProviderPresets.ollama()
                                : ProviderPresets.ollama(spec.baseUrl()));
        r.register(
                "lm-studio",
                spec ->
                        spec.baseUrl() == null
                                ? ProviderPresets.lmStudio()
                                : ProviderPresets.lmStudio(spec.baseUrl()));

        // Generic OpenAI-compatible — baseUrl required, allows the user to point at anything.
        r.register(
                "openai-compatible",
                spec -> {
                    if (spec.apiKey() == null || spec.apiKey().isBlank()) {
                        throw new IllegalArgumentException(
                                "Provider 'openai-compatible' requires an API key");
                    }
                    if (spec.baseUrl() == null || spec.baseUrl().isBlank()) {
                        throw new IllegalArgumentException(
                                "Provider 'openai-compatible' requires baseUrl");
                    }
                    return new OpenAIProvider(spec.apiKey(), spec.baseUrl());
                });
        return r;
    }

    @Override
    public void register(String name, ProviderFactory factory) {
        factories.put(canonical(name), factory);
    }

    @Override
    public boolean isRegistered(String name) {
        return factories.containsKey(canonical(name));
    }

    @Override
    public Set<String> names() {
        return Set.copyOf(factories.keySet());
    }

    @Override
    public ModelProvider create(String name, ProviderSpec spec) {
        ProviderFactory f = factories.get(canonical(name));
        if (f == null) {
            throw new IllegalArgumentException(
                    "Unknown provider '" + name + "'. Registered: " + factories.keySet());
        }
        return f.create(spec);
    }

    private static String canonical(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("provider name must not be blank");
        }
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
