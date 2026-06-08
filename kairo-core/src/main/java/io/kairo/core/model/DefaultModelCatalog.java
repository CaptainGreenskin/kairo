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

import io.kairo.api.model.ModelCapability;
import io.kairo.api.model.ModelCatalog;
import io.kairo.api.model.ModelInfo;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default in-memory {@link ModelCatalog} backed by {@link ModelCapabilityRegistry}.
 *
 * <p>Lookup follows a three-level fallback:
 *
 * <ol>
 *   <li>Alias resolution (e.g., "sonnet" → "claude-sonnet-4")
 *   <li>Exact match in the entries map
 *   <li>Prefix match (e.g., "claude-sonnet-4-20250514" matches "claude-sonnet-4")
 * </ol>
 *
 * <p>If none match, infers the provider from the model name prefix (claude → anthropic, gpt →
 * openai, etc.) and delegates capability lookup to {@link ModelCapabilityRegistry}.
 *
 * <p>Thread-safe: all maps are {@link ConcurrentHashMap}.
 */
public final class DefaultModelCatalog implements ModelCatalog {

    private final ConcurrentHashMap<String, ModelInfo> entries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> aliases = new ConcurrentHashMap<>();

    /**
     * Create a catalog pre-populated with all models known to {@link ModelCapabilityRegistry} and
     * common aliases.
     */
    public static DefaultModelCatalog withBuiltIns() {
        DefaultModelCatalog catalog = new DefaultModelCatalog();

        // Claude family
        catalog.put("claude-3-5-haiku", "anthropic");
        catalog.put("claude-3-haiku", "anthropic");
        catalog.put("claude-3-5-sonnet", "anthropic");
        catalog.put("claude-sonnet-4", "anthropic");
        catalog.put("claude-3-opus", "anthropic");
        catalog.put("claude-opus-4", "anthropic");
        catalog.put("claude-haiku-4", "anthropic");

        // GPT family
        catalog.put("gpt-5", "openai");
        catalog.put("gpt-4o", "openai");
        catalog.put("gpt-4o-mini", "openai");
        catalog.put("gpt-4-turbo", "openai");
        catalog.put("gpt-4", "openai");
        catalog.put("gpt-3.5", "openai");

        // GLM family
        catalog.put("glm-5.1", "glm");
        catalog.put("glm-4.5", "glm");
        catalog.put("glm-4-plus", "glm");
        catalog.put("glm-4-flash", "glm");
        catalog.put("glm-4-long", "glm");
        catalog.put("glm-4", "glm");

        // Gemini family
        catalog.put("gemini-2.5-pro", "gemini");
        catalog.put("gemini-2.5-flash", "gemini");
        catalog.put("gemini-2.0-flash", "gemini");

        // DeepSeek family
        catalog.put("deepseek-chat", "deepseek");
        catalog.put("deepseek-coder", "deepseek");

        // Qwen family
        catalog.put("qwen-max", "qwen");
        catalog.put("qwen-plus", "qwen");
        catalog.put("qwen-turbo", "qwen");

        // Common aliases
        catalog.registerAlias("sonnet", "claude-sonnet-4");
        catalog.registerAlias("opus", "claude-opus-4");
        catalog.registerAlias("haiku", "claude-haiku-4");

        return catalog;
    }

    private void put(String modelId, String providerName) {
        ModelCapability cap = ModelCapabilityRegistry.lookup(modelId);
        entries.put(modelId, new ModelInfo(providerName, modelId, cap));
    }

    @Override
    public Optional<ModelInfo> resolve(String modelNameOrAlias) {
        if (modelNameOrAlias == null || modelNameOrAlias.isBlank()) {
            return Optional.empty();
        }

        String normalized = modelNameOrAlias.trim();

        // 1. Alias resolution
        String aliasTarget = aliases.get(normalized.toLowerCase(Locale.ROOT));
        if (aliasTarget != null) {
            ModelInfo info = entries.get(aliasTarget);
            if (info != null) {
                return Optional.of(info);
            }
        }

        // 2. Exact match
        ModelInfo exact = entries.get(normalized);
        if (exact != null) {
            return Optional.of(exact);
        }

        // 3. Prefix match (longest wins)
        String bestKey = null;
        for (String key : entries.keySet()) {
            if (normalized.startsWith(key)
                    && (bestKey == null || key.length() > bestKey.length())) {
                bestKey = key;
            }
        }
        if (bestKey != null) {
            ModelInfo prefixMatch = entries.get(bestKey);
            return Optional.of(
                    new ModelInfo(
                            prefixMatch.providerName(), normalized, prefixMatch.capability()));
        }

        // 4. Infer provider from model name prefix
        String inferredProvider = inferProvider(normalized);
        if (inferredProvider != null) {
            ModelCapability cap = ModelCapabilityRegistry.lookup(normalized);
            return Optional.of(new ModelInfo(inferredProvider, normalized, cap));
        }

        return Optional.empty();
    }

    @Override
    public void register(String modelId, ModelInfo info) {
        entries.put(modelId, info);
        ModelCapabilityRegistry.register(modelId, info.capability());
    }

    @Override
    public void registerAlias(String alias, String canonicalModelId) {
        aliases.put(alias.toLowerCase(Locale.ROOT), canonicalModelId);
    }

    @Override
    public Set<String> knownModels() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    private static String inferProvider(String modelName) {
        String lower = modelName.toLowerCase(Locale.ROOT);
        if (lower.startsWith("claude")) return "anthropic";
        if (lower.startsWith("gpt") || lower.startsWith("o1") || lower.startsWith("o3"))
            return "openai";
        if (lower.startsWith("glm")) return "glm";
        if (lower.startsWith("gemini")) return "gemini";
        if (lower.startsWith("deepseek")) return "deepseek";
        if (lower.startsWith("qwen")) return "qwen";
        if (lower.startsWith("minimax")) return "minimax";
        if (lower.startsWith("moonshot") || lower.startsWith("kimi")) return "moonshot";
        if (lower.startsWith("mistral")) return "mistral";
        return null;
    }
}
