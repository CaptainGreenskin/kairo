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
 */
public record ModelCapability(
        String modelFamily,
        String modelTier,
        int contextWindow,
        int maxOutputTokens,
        boolean supportsThinking,
        boolean supportsCaching,
        ToolVerbosity toolVerbosity,
        IntRange thinkingBudgetRange) {}
