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

import io.kairo.core.model.anthropic.AnthropicProvider;
import io.kairo.core.model.gemini.GeminiProvider;
import io.kairo.core.model.openai.OpenAIProvider;

/**
 * Convenience factory for commonly used model providers with sensible defaults.
 *
 * <p>Each method returns a standard provider instance that users can further configure. This class
 * provides convenience, not lock-in.
 *
 * <p>OpenAI-compatible backends (Qwen / GLM / DeepSeek / MiniMax / Groq / Kimi / xAI / OpenRouter /
 * Ollama) all reuse {@link OpenAIProvider} with the appropriate base URL — they get full streaming
 * + tool-calling support without a new transport. Use the dedicated {@link #anthropic} for Claude
 * (native Messages API) and {@link #gemini} once the Gemini provider lands (native generateContent
 * API).
 *
 * <pre>{@code
 * // Quick start with Anthropic
 * ModelProvider claude = ProviderPresets.anthropic(System.getenv("ANTHROPIC_API_KEY"));
 *
 * // Quick start with OpenAI
 * ModelProvider gpt = ProviderPresets.openai(System.getenv("OPENAI_API_KEY"));
 *
 * // Local Ollama
 * ModelProvider local = ProviderPresets.ollama();
 * }</pre>
 */
public final class ProviderPresets {

    private ProviderPresets() {
        // utility class — no instances
    }

    // ---- Native providers -----------------------------------------------------------------

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
     * Gemini (native generateContent API) with sensible defaults.
     *
     * @param apiKey the Google AI Studio API key
     * @return a configured {@link GeminiProvider}
     */
    public static GeminiProvider gemini(String apiKey) {
        return new GeminiProvider(apiKey);
    }

    // ---- OpenAI-compatible backends (Chinese) ---------------------------------------------

    /**
     * Qwen (DashScope) — Alibaba Cloud OpenAI-compatible endpoint.
     *
     * @param apiKey the DashScope API key
     */
    public static OpenAIProvider qwen(String apiKey) {
        return new OpenAIProvider(
                apiKey, "https://dashscope.aliyuncs.com/compatible-mode", "/v1/chat/completions");
    }

    /**
     * GLM (Zhipu AI) — OpenAI-compatible endpoint.
     *
     * @param apiKey the Zhipu AI API key
     */
    public static OpenAIProvider glm(String apiKey) {
        return new OpenAIProvider(
                apiKey, "https://open.bigmodel.cn/api/paas", "/v4/chat/completions");
    }

    /**
     * DeepSeek — OpenAI-compatible endpoint.
     *
     * @param apiKey the DeepSeek API key
     */
    public static OpenAIProvider deepseek(String apiKey) {
        return new OpenAIProvider(apiKey, "https://api.deepseek.com", "/v1/chat/completions");
    }

    /**
     * MiniMax — OpenAI-compatible endpoint at {@code api.minimaxi.com/v1}. Supports the M2.7
     * family.
     *
     * @param apiKey the MiniMax API key
     */
    public static OpenAIProvider minimax(String apiKey) {
        return new OpenAIProvider(apiKey, "https://api.minimaxi.com", "/v1/chat/completions");
    }

    /**
     * Moonshot / Kimi — OpenAI-compatible endpoint.
     *
     * @param apiKey the Moonshot API key
     */
    public static OpenAIProvider kimi(String apiKey) {
        return new OpenAIProvider(apiKey, "https://api.moonshot.cn", "/v1/chat/completions");
    }

    // ---- OpenAI-compatible backends (Global) ----------------------------------------------

    /**
     * Groq — OpenAI-compatible endpoint, very low latency Llama / Mistral / Gemma hosting.
     *
     * @param apiKey the Groq API key
     */
    public static OpenAIProvider groq(String apiKey) {
        return new OpenAIProvider(apiKey, "https://api.groq.com/openai", "/v1/chat/completions");
    }

    /**
     * xAI Grok — OpenAI-compatible endpoint.
     *
     * @param apiKey the xAI API key
     */
    public static OpenAIProvider xai(String apiKey) {
        return new OpenAIProvider(apiKey, "https://api.x.ai", "/v1/chat/completions");
    }

    /**
     * OpenRouter — multi-model aggregator with one OpenAI-compatible endpoint.
     *
     * @param apiKey the OpenRouter API key
     */
    public static OpenAIProvider openrouter(String apiKey) {
        return new OpenAIProvider(apiKey, "https://openrouter.ai/api", "/v1/chat/completions");
    }

    // ---- Local --------------------------------------------------------------------------

    /**
     * Local Ollama — no API key. Defaults to {@code http://localhost:11434}; override the URL with
     * {@link #ollama(String)} if your Ollama listens elsewhere.
     */
    public static OpenAIProvider ollama() {
        return ollama("http://localhost:11434");
    }

    /**
     * Local Ollama at a custom base URL. Ollama accepts any non-empty string as the API key (Bearer
     * token is ignored).
     */
    public static OpenAIProvider ollama(String baseUrl) {
        return new OpenAIProvider("ollama", baseUrl, "/v1/chat/completions");
    }

    /**
     * Local LM Studio — defaults to {@code http://localhost:1234}. Like Ollama, ignores the API
     * key; we pass a placeholder.
     */
    public static OpenAIProvider lmStudio() {
        return lmStudio("http://localhost:1234");
    }

    public static OpenAIProvider lmStudio(String baseUrl) {
        return new OpenAIProvider("lm-studio", baseUrl, "/v1/chat/completions");
    }

    /**
     * Together AI — OpenAI-compatible endpoint.
     *
     * @param apiKey the Together API key
     * @return a configured provider
     */
    public static OpenAIProvider together(String apiKey) {
        return new OpenAIProvider(apiKey, "https://api.together.xyz", "/v1/chat/completions");
    }

    /**
     * Fireworks AI — OpenAI-compatible endpoint.
     *
     * @param apiKey the Fireworks API key
     * @return a configured provider
     */
    public static OpenAIProvider fireworks(String apiKey) {
        return new OpenAIProvider(
                apiKey, "https://api.fireworks.ai/inference", "/v1/chat/completions");
    }

    /**
     * Novita AI — OpenAI-compatible endpoint.
     *
     * @param apiKey the Novita API key
     * @return a configured provider
     */
    public static OpenAIProvider novita(String apiKey) {
        return new OpenAIProvider(apiKey, "https://api.novita.ai/v3/openai", "/chat/completions");
    }

    /**
     * NVIDIA NIM (integrate.api.nvidia.com) — OpenAI-compatible endpoint for Nemotron and hosted
     * models.
     *
     * @param apiKey the NVIDIA API key
     * @return a configured provider
     */
    public static OpenAIProvider nvidia(String apiKey) {
        return new OpenAIProvider(
                apiKey, "https://integrate.api.nvidia.com", "/v1/chat/completions");
    }

    /**
     * StepFun — OpenAI-compatible endpoint.
     *
     * @param apiKey the StepFun API key
     * @return a configured provider
     */
    public static OpenAIProvider stepfun(String apiKey) {
        return new OpenAIProvider(apiKey, "https://api.stepfun.com", "/v1/chat/completions");
    }

    /**
     * Perplexity (Sonar) — OpenAI-compatible endpoint.
     *
     * @param apiKey the Perplexity API key
     * @return a configured provider
     */
    public static OpenAIProvider perplexity(String apiKey) {
        return new OpenAIProvider(apiKey, "https://api.perplexity.ai", "/chat/completions");
    }

    /**
     * Cerebras — OpenAI-compatible endpoint.
     *
     * @param apiKey the Cerebras API key
     * @return a configured provider
     */
    public static OpenAIProvider cerebras(String apiKey) {
        return new OpenAIProvider(apiKey, "https://api.cerebras.ai", "/v1/chat/completions");
    }

    /**
     * Mistral AI — OpenAI-compatible endpoint.
     *
     * @param apiKey the Mistral API key
     * @return a configured provider
     */
    public static OpenAIProvider mistral(String apiKey) {
        return new OpenAIProvider(apiKey, "https://api.mistral.ai", "/v1/chat/completions");
    }
}
