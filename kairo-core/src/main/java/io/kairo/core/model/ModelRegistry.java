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

/**
 * Token-window facade over {@link ModelCapabilityRegistry}.
 *
 * <p>Historically maintained its own static table of {@code modelId → (contextWindow,
 * maxOutputTokens)} that drifted from {@link ModelCapabilityRegistry}'s richer table —
 * claude-sonnet-4-20250514 reported 20000 max output here but 16384 there, and glm-4-long's 1M
 * context was missing entirely so prefix fallback reported 128K and triggered premature context
 * compaction. Now delegates so there's one source of truth: add a model to {@code
 * ModelCapabilityRegistry} and both this class's callers (TokenBudgetManager,
 * ContextCompactionEngine, kairo-code's CodeAgentFactory) and the per-provider RequestBuilder
 * callers see it.
 *
 * <p>The narrow surface — context window and max output only — is what {@code TokenBudgetManager} /
 * {@code CompactionPipeline} have always cared about; exposing it via a separate facade lets those
 * modules stay decoupled from the full {@code ModelCapability} record (which carries
 * thinking-budget / caching / verbosity that the budget code has no use for).
 *
 * <p>Backward-compatible: same public method signatures + {@link ModelSpec} record.
 */
public class ModelRegistry {

    /**
     * Default context window for unknown models. Matches the universal default in {@link
     * ModelCapabilityRegistry}.
     */
    private static final int DEFAULT_CONTEXT_WINDOW = 128_000;

    /**
     * Default max output tokens for unknown models. Matches the universal default in {@link
     * ModelCapabilityRegistry}.
     */
    private static final int DEFAULT_MAX_OUTPUT = 8_192;

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
     * <p>Looks up via {@link ModelCapabilityRegistry#lookup(String)}, which does exact → prefix →
     * family-default → universal-default fallback. Always non-null.
     *
     * @param modelId the model identifier
     * @return the model spec (never null)
     */
    public static ModelSpec getSpec(String modelId) {
        if (modelId == null) {
            return new ModelSpec(DEFAULT_CONTEXT_WINDOW, DEFAULT_MAX_OUTPUT);
        }
        ModelCapability cap = ModelCapabilityRegistry.lookup(modelId);
        return new ModelSpec(cap.contextWindow(), cap.maxOutputTokens());
    }

    /**
     * Register a model specification. Persists into the underlying {@link ModelCapabilityRegistry}
     * with sensible capability defaults (no thinking, no caching, STANDARD tool verbosity) so
     * future {@code lookup()} calls from request builders also see it.
     *
     * <p>Mainly used by tests + embedders that need to teach the runtime about a model the
     * framework doesn't ship knowledge for.
     *
     * @param modelId the model identifier
     * @param contextWindow the context window size in tokens
     * @param maxOutput the maximum output tokens
     */
    public static void register(String modelId, int contextWindow, int maxOutput) {
        ModelCapability cap =
                new ModelCapability(
                        familyOf(modelId),
                        "custom",
                        contextWindow,
                        maxOutput,
                        false,
                        false,
                        ToolVerbosity.STANDARD,
                        (IntRange) null);
        ModelCapabilityRegistry.register(modelId, cap);
    }

    private static String familyOf(String modelId) {
        if (modelId == null) return "unknown";
        if (modelId.startsWith("claude")) return "claude";
        if (modelId.startsWith("gpt") || modelId.startsWith("openai")) return "gpt";
        if (modelId.startsWith("glm")) return "glm";
        if (modelId.startsWith("deepseek")) return "deepseek";
        if (modelId.startsWith("qwen")) return "qwen";
        return "unknown";
    }
}
