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

import io.kairo.api.Stable;

/**
 * Describes the capabilities and characteristics of a specific model.
 *
 * @param modelFamily the model family, e.g. "claude", "gpt", "glm"
 * @param modelTier the model tier, e.g. "haiku", "sonnet", "opus", "4o", "4o-mini"
 * @param contextWindow the maximum context window size in tokens
 * @param maxOutputTokens the maximum number of output tokens
 * @param supportsThinking whether the model supports extended thinking / chain-of-thought
 * @param supportsCaching whether the model supports prompt caching
 * @param toolVerbosity the recommended tool description verbosity for this model
 * @param thinkingBudgetRange the thinking budget range, or null if thinking is not supported
 * @param promptGuidance model-specific guidance injected into the system prompt, or empty
 */
@Stable(value = "Model capability record; shape frozen since v0.3", since = "1.0.0")
public record ModelCapability(
        String modelFamily,
        String modelTier,
        int contextWindow,
        int maxOutputTokens,
        boolean supportsThinking,
        boolean supportsCaching,
        ToolVerbosity toolVerbosity,
        IntRange thinkingBudgetRange,
        String promptGuidance) {

    /** Backward-compatible constructor without promptGuidance. */
    public ModelCapability(
            String modelFamily,
            String modelTier,
            int contextWindow,
            int maxOutputTokens,
            boolean supportsThinking,
            boolean supportsCaching,
            ToolVerbosity toolVerbosity,
            IntRange thinkingBudgetRange) {
        this(
                modelFamily,
                modelTier,
                contextWindow,
                maxOutputTokens,
                supportsThinking,
                supportsCaching,
                toolVerbosity,
                thinkingBudgetRange,
                defaultPromptGuidance(modelFamily));
    }

    /**
     * Return the default model-specific prompt guidance for a given model family.
     *
     * @param modelFamily the model family identifier
     * @return guidance string, or empty if none is needed
     */
    public static String defaultPromptGuidance(String modelFamily) {
        if (modelFamily == null) {
            return "";
        }
        return switch (modelFamily.toLowerCase(java.util.Locale.ROOT)) {
            case "gpt", "codex" ->
                    "Always use tools to take action; do not describe what you would do.";
            case "gemini" -> "Use absolute paths; read files before modifying them.";
            default -> "";
        };
    }
}
