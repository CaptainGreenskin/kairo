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

import io.kairo.api.model.IntRange;
import io.kairo.api.model.ModelCapability;
import io.kairo.api.model.ToolVerbosity;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of per-model behavioral capabilities.
 *
 * <p>Extends the {@link ModelRegistry} concept with richer metadata: model family, tier, thinking
 * support, caching support, recommended tool verbosity, and thinking budget range.
 *
 * <p>Lookup follows a four-level fallback strategy:
 *
 * <ol>
 *   <li>Exact match on model ID
 *   <li>Prefix match (e.g. "claude-3-5-sonnet-20241022" matches "claude-3-5-sonnet")
 *   <li>Family match (e.g. "claude-xxx" falls back to default Claude capability)
 *   <li>Universal default
 * </ol>
 *
 * <p>Thread-safe: backed by a {@link ConcurrentHashMap}.
 */
public class ModelCapabilityRegistry {

    private static final Map<String, ModelCapability> CAPABILITIES = new ConcurrentHashMap<>();

    static {
        // Claude family
        register(
                "claude-3-5-haiku",
                new ModelCapability(
                        "claude",
                        "haiku",
                        200_000,
                        8192,
                        false,
                        true,
                        ToolVerbosity.CONCISE,
                        new IntRange(1024, 4096)));
        register(
                "claude-3-haiku",
                new ModelCapability(
                        "claude",
                        "haiku",
                        200_000,
                        4096,
                        false,
                        true,
                        ToolVerbosity.CONCISE,
                        new IntRange(1024, 4096)));
        register(
                "claude-3-5-sonnet",
                new ModelCapability(
                        "claude",
                        "sonnet",
                        200_000,
                        8192,
                        true,
                        true,
                        ToolVerbosity.STANDARD,
                        new IntRange(2048, 16384)));
        register(
                "claude-sonnet-4",
                new ModelCapability(
                        "claude",
                        "sonnet",
                        200_000,
                        16384,
                        true,
                        true,
                        ToolVerbosity.STANDARD,
                        new IntRange(2048, 16384)));
        register(
                "claude-3-opus",
                new ModelCapability(
                        "claude",
                        "opus",
                        200_000,
                        4096,
                        true,
                        true,
                        ToolVerbosity.VERBOSE,
                        new IntRange(4096, 32768)));
        register(
                "claude-opus-4",
                new ModelCapability(
                        "claude",
                        "opus",
                        200_000,
                        20000,
                        true,
                        true,
                        ToolVerbosity.VERBOSE,
                        new IntRange(4096, 32768)));

        // GPT family
        register(
                "gpt-4o",
                new ModelCapability(
                        "gpt", "4o", 128_000, 16384, false, false, ToolVerbosity.STANDARD, null));
        register(
                "gpt-4o-mini",
                new ModelCapability(
                        "gpt",
                        "4o-mini",
                        128_000,
                        16384,
                        false,
                        false,
                        ToolVerbosity.CONCISE,
                        null));
        register(
                "gpt-4-turbo",
                new ModelCapability(
                        "gpt",
                        "4-turbo",
                        128_000,
                        4096,
                        false,
                        false,
                        ToolVerbosity.STANDARD,
                        null));

        // GLM family
        register(
                "glm-4-plus",
                new ModelCapability(
                        "glm", "4-plus", 128_000, 4096, false, false, ToolVerbosity.CONCISE, null));
        register(
                "glm-4",
                new ModelCapability(
                        "glm", "4", 128_000, 4096, false, false, ToolVerbosity.CONCISE, null));

        // DeepSeek family
        register(
                "deepseek-chat",
                new ModelCapability(
                        "deepseek",
                        "chat",
                        64_000,
                        8192,
                        false,
                        false,
                        ToolVerbosity.STANDARD,
                        null));
        register(
                "deepseek-coder",
                new ModelCapability(
                        "deepseek",
                        "coder",
                        64_000,
                        8192,
                        false,
                        false,
                        ToolVerbosity.STANDARD,
                        null));
    }

    private ModelCapabilityRegistry() {}

    /**
     * Register a model capability.
     *
     * @param modelId the model identifier
     * @param capability the capability descriptor
     */
    public static void register(String modelId, ModelCapability capability) {
        CAPABILITIES.put(modelId, capability);
    }

    /**
     * Look up model capability using the four-level fallback strategy.
     *
     * @param modelId the model identifier
     * @return the capability (never null)
     */
    public static ModelCapability lookup(String modelId) {
        if (modelId == null) {
            return defaultCapability();
        }

        // 1. Exact match
        ModelCapability cap = CAPABILITIES.get(modelId);
        if (cap != null) {
            return cap;
        }

        // 2. Prefix match (e.g., "claude-3-5-sonnet-20241022" matches "claude-3-5-sonnet")
        ModelCapability best = null;
        int bestLen = 0;
        for (Map.Entry<String, ModelCapability> entry : CAPABILITIES.entrySet()) {
            String key = entry.getKey();
            if (modelId.startsWith(key) && key.length() > bestLen) {
                best = entry.getValue();
                bestLen = key.length();
            }
        }
        if (best != null) {
            return best;
        }

        // 3. Family match
        if (modelId.startsWith("claude")) {
            return defaultClaude();
        }
        if (modelId.startsWith("gpt")) {
            return defaultGpt();
        }
        if (modelId.startsWith("glm")) {
            return defaultGlm();
        }
        if (modelId.startsWith("deepseek")) {
            return defaultDeepSeek();
        }

        // 4. Universal default
        return defaultCapability();
    }

    private static ModelCapability defaultClaude() {
        return new ModelCapability(
                "claude",
                "sonnet",
                200_000,
                8192,
                true,
                true,
                ToolVerbosity.STANDARD,
                new IntRange(2048, 16384));
    }

    private static ModelCapability defaultGpt() {
        return new ModelCapability(
                "gpt", "4o", 128_000, 16384, false, false, ToolVerbosity.STANDARD, null);
    }

    private static ModelCapability defaultGlm() {
        return new ModelCapability(
                "glm", "4", 128_000, 4096, false, false, ToolVerbosity.CONCISE, null);
    }

    private static ModelCapability defaultDeepSeek() {
        return new ModelCapability(
                "deepseek", "chat", 64_000, 8192, false, false, ToolVerbosity.STANDARD, null);
    }

    private static ModelCapability defaultCapability() {
        return new ModelCapability(
                "unknown", "default", 128_000, 8192, false, false, ToolVerbosity.STANDARD, null);
    }
}
