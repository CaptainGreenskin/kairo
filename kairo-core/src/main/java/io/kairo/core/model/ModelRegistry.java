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

import java.util.HashMap;
import java.util.Map;

/**
 * Registry of known model specifications (context window size, max output tokens).
 *
 * <p>Provides static lookup by exact model ID with prefix-matching fallback. Unknown models fall
 * back to sensible defaults (128K context, 8192 max output).
 *
 * <p>Thread-safe: the underlying map is populated in a static initializer and additional
 * registrations are synchronized on the map instance.
 */
public class ModelRegistry {

    private static final Map<String, ModelSpec> MODELS = new HashMap<>();

    /** Default context window for unknown models. */
    private static final int DEFAULT_CONTEXT_WINDOW = 128_000;

    /** Default max output tokens for unknown models. */
    private static final int DEFAULT_MAX_OUTPUT = 8_192;

    static {
        // Claude models
        register("claude-sonnet-4-20250514", 200_000, 20_000);
        register("claude-opus-4-20250514", 200_000, 20_000);
        register("claude-3-5-sonnet-20241022", 200_000, 8_192);
        register("claude-3-5-haiku-20241022", 200_000, 8_192);
        register("claude-3-haiku-20240307", 200_000, 4_096);
        // OpenAI models
        register("gpt-4o", 128_000, 16_384);
        register("gpt-4o-mini", 128_000, 16_384);
        register("gpt-4-turbo", 128_000, 4_096);
        // GLM models
        register("glm-4-plus", 128_000, 4_096);
        register("glm-4-flash", 128_000, 4_096);
        // DeepSeek
        register("deepseek-chat", 64_000, 8_192);
        register("deepseek-coder", 64_000, 8_192);
    }

    /**
     * Specification of a model's token limits.
     *
     * @param contextWindow the total context window size in tokens
     * @param maxOutputTokens the maximum number of output tokens
     */
    public record ModelSpec(int contextWindow, int maxOutputTokens) {}

    private ModelRegistry() {}

    /**
     * Get the context window size for the given model.
     *
     * @param modelId the model identifier
     * @return the context window size, or {@value DEFAULT_CONTEXT_WINDOW} for unknown models
     */
    public static int getContextWindow(String modelId) {
        return getSpec(modelId).contextWindow();
    }

    /**
     * Get the maximum output tokens for the given model.
     *
     * @param modelId the model identifier
     * @return the max output tokens, or {@value DEFAULT_MAX_OUTPUT} for unknown models
     */
    public static int getMaxOutputTokens(String modelId) {
        return getSpec(modelId).maxOutputTokens();
    }

    /**
     * Get the full specification for a model.
     *
     * <p>Tries exact match first, then prefix matching (longest prefix wins). Falls back to
     * defaults for completely unknown models.
     *
     * @param modelId the model identifier
     * @return the model spec (never null)
     */
    public static ModelSpec getSpec(String modelId) {
        if (modelId == null) {
            return new ModelSpec(DEFAULT_CONTEXT_WINDOW, DEFAULT_MAX_OUTPUT);
        }

        // Exact match
        synchronized (MODELS) {
            ModelSpec exact = MODELS.get(modelId);
            if (exact != null) {
                return exact;
            }

            // Prefix matching: find the longest registered key that is a prefix of modelId,
            // or the longest modelId prefix that matches a registered key
            ModelSpec best = null;
            int bestLen = 0;
            for (Map.Entry<String, ModelSpec> entry : MODELS.entrySet()) {
                String key = entry.getKey();
                // Check if registered key starts with modelId (e.g. "claude-sonnet-4" matches
                // "claude-sonnet-4-20250514")
                if (key.startsWith(modelId) && modelId.length() > bestLen) {
                    best = entry.getValue();
                    bestLen = modelId.length();
                }
                // Check if modelId starts with registered key
                if (modelId.startsWith(key) && key.length() > bestLen) {
                    best = entry.getValue();
                    bestLen = key.length();
                }
            }

            if (best != null) {
                return best;
            }
        }

        return new ModelSpec(DEFAULT_CONTEXT_WINDOW, DEFAULT_MAX_OUTPUT);
    }

    /**
     * Register a model specification.
     *
     * @param modelId the model identifier
     * @param contextWindow the context window size in tokens
     * @param maxOutput the maximum output tokens
     */
    public static void register(String modelId, int contextWindow, int maxOutput) {
        synchronized (MODELS) {
            MODELS.put(modelId, new ModelSpec(contextWindow, maxOutput));
        }
    }
}
