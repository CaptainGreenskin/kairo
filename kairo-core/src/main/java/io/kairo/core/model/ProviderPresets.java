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

/**
 * Convenience factory for commonly used model providers with sensible defaults.
 *
 * <p>Each method returns a standard provider instance that users can further configure. This class
 * provides convenience, not lock-in.
 *
 * <pre>{@code
 * // Quick start with Anthropic
 * ModelProvider claude = ProviderPresets.anthropic(System.getenv("ANTHROPIC_API_KEY"));
 *
 * // Quick start with OpenAI
 * ModelProvider gpt = ProviderPresets.openai(System.getenv("OPENAI_API_KEY"));
 *
 * // Quick start with Qwen (DashScope)
 * ModelProvider qwen = ProviderPresets.qwen(System.getenv("DASHSCOPE_API_KEY"));
 * }</pre>
 */
public final class ProviderPresets {

    private ProviderPresets() {
        // utility class — no instances
    }

    /**
     * Claude with sensible defaults.
     *
     * @param apiKey the Anthropic API key
     * @return a configured {@link AnthropicProvider}
     */
    public static AnthropicProvider anthropic(String apiKey) {
        return new AnthropicProvider(apiKey);
    }

    /**
     * GPT with sensible defaults.
     *
     * @param apiKey the OpenAI API key
     * @return a configured {@link OpenAIProvider}
     */
    public static OpenAIProvider openai(String apiKey) {
        return new OpenAIProvider(apiKey);
    }

    /**
     * Qwen (DashScope) — compatible OpenAI provider with Alibaba Cloud endpoint.
     *
     * @param apiKey the DashScope API key
     * @return a configured {@link OpenAIProvider} pointing to DashScope
     */
    public static OpenAIProvider qwen(String apiKey) {
        return new OpenAIProvider(
                apiKey, "https://dashscope.aliyuncs.com/compatible-mode", "/v1/chat/completions");
    }

    /**
     * GLM (Zhipu AI) — compatible OpenAI provider with Zhipu endpoint.
     *
     * @param apiKey the Zhipu AI API key
     * @return a configured {@link OpenAIProvider} pointing to Zhipu AI
     */
    public static OpenAIProvider glm(String apiKey) {
        return new OpenAIProvider(
                apiKey, "https://open.bigmodel.cn/api/paas", "/v4/chat/completions");
    }

    /**
     * DeepSeek — compatible OpenAI provider with DeepSeek endpoint.
     *
     * @param apiKey the DeepSeek API key
     * @return a configured {@link OpenAIProvider} pointing to DeepSeek
     */
    public static OpenAIProvider deepseek(String apiKey) {
        return new OpenAIProvider(apiKey, "https://api.deepseek.com", "/v1/chat/completions");
    }
}
