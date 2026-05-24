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
package io.kairo.spring.config;

import io.kairo.api.model.ModelConfig;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Model provider configuration ({@code kairo.model.*}).
 *
 * <p>Configures which LLM provider to use, authentication credentials, and generation parameters.
 * Supports native Anthropic API and OpenAI-compatible providers (GLM, Qwen, GPT).
 */
@ConfigurationProperties(prefix = "kairo.model")
public class ModelProperties {

    /**
     * Model provider type. Determines which API client is instantiated.
     *
     * <p>Valid values: {@code "anthropic"}, {@code "openai"} (also covers GLM, Qwen, GPT via
     * OpenAI-compatible endpoint).
     *
     * <p>Default: {@code "anthropic"}
     */
    private String provider = "anthropic";

    /**
     * API key for the model provider. If not set, the auto-configuration will attempt to read from
     * environment variables: {@code ANTHROPIC_API_KEY} (for Anthropic), {@code OPENAI_API_KEY} /
     * {@code GLM_API_KEY} / {@code QWEN_API_KEY} (for OpenAI-compatible providers).
     *
     * <p>Default: {@code null} (falls back to environment variable)
     */
    private String apiKey;

    /**
     * Custom base URL for the provider API endpoint. Use this when connecting through a proxy or
     * when using an OpenAI-compatible provider (e.g. {@code "https://open.bigmodel.cn/api/paas/v4"}
     * for GLM).
     *
     * <p>Default: {@code null} (uses the provider's default endpoint)
     */
    private String baseUrl;

    /**
     * Model name to use for inference. Must be a model identifier recognized by the configured
     * provider.
     *
     * <p>Examples: {@code "claude-sonnet-4-20250514"}, {@code "glm-4-plus"}, {@code "qwen-plus"},
     * {@code "gpt-4o"}
     *
     * <p>Default: {@value io.kairo.api.model.ModelConfig#DEFAULT_MODEL}
     */
    private String modelName = ModelConfig.DEFAULT_MODEL;

    /**
     * Maximum number of tokens the model may generate in a single response. Higher values allow
     * longer responses but increase latency and cost.
     *
     * <p>Valid range: 1–200,000 (provider-dependent upper bound)
     *
     * <p>Default: {@value io.kairo.api.model.ModelConfig#DEFAULT_MAX_TOKENS}
     */
    private int maxTokens = ModelConfig.DEFAULT_MAX_TOKENS;

    /**
     * Sampling temperature for generation. Lower values produce more deterministic output; higher
     * values produce more creative/random output.
     *
     * <p>Valid range: 0.0–2.0
     *
     * <p>Default: {@code 0.7}
     */
    private double temperature = 0.7;

    /**
     * Whether to enable extended thinking (Anthropic only). When enabled, the model performs deeper
     * reasoning before responding, which can improve quality on complex tasks at the cost of
     * additional tokens and latency.
     *
     * <p>Default: {@code false}
     */
    private boolean thinkingEnabled = false;

    /**
     * Budget tokens allocated for extended thinking (Anthropic only). Only used when {@link
     * #thinkingEnabled} is {@code true}. The model will use up to this many tokens for internal
     * reasoning before producing the visible response.
     *
     * <p>Valid range: 1,000–100,000
     *
     * <p>Default: {@code 10000}
     */
    private int thinkingBudget = 10000;

    /**
     * Circuit breaker configuration for model API calls ({@code kairo.model.circuit-breaker.*}).
     */
    @NestedConfigurationProperty private CircuitBreaker circuitBreaker = new CircuitBreaker();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public boolean isThinkingEnabled() {
        return thinkingEnabled;
    }

    public void setThinkingEnabled(boolean thinkingEnabled) {
        this.thinkingEnabled = thinkingEnabled;
    }

    public int getThinkingBudget() {
        return thinkingBudget;
    }

    public void setThinkingBudget(int thinkingBudget) {
        this.thinkingBudget = thinkingBudget;
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public void setCircuitBreaker(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * Circuit breaker configuration for model API calls ({@code kairo.model.circuit-breaker.*}).
     *
     * <p>Implements a three-state circuit breaker (CLOSED → OPEN → HALF_OPEN) that prevents
     * cascading failures when the model API is unavailable. When open, calls fail fast without
     * contacting the provider.
     */
    public static class CircuitBreaker {

        /**
         * Whether the circuit breaker is enabled. When disabled, all model API calls proceed
         * without circuit-breaker protection.
         *
         * <p>Default: {@code true}
         */
        private boolean enabled = true;

        /**
         * Number of consecutive failures before the circuit transitions from CLOSED to OPEN. Once
         * open, subsequent calls fail immediately until {@link #resetTimeout} elapses.
         *
         * <p>Valid range: 1–100
         *
         * <p>Default: {@code 5}
         */
        private int failureThreshold = 5;

        /**
         * Duration to wait before transitioning from OPEN to HALF_OPEN. In the HALF_OPEN state, a
         * single probe request is allowed through; if it succeeds the circuit closes, otherwise it
         * re-opens.
         *
         * <p>Default: {@code 60s}
         */
        private Duration resetTimeout = Duration.ofSeconds(60);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getFailureThreshold() {
            return failureThreshold;
        }

        public void setFailureThreshold(int failureThreshold) {
            this.failureThreshold = failureThreshold;
        }

        public Duration getResetTimeout() {
            return resetTimeout;
        }

        public void setResetTimeout(Duration resetTimeout) {
            this.resetTimeout = resetTimeout;
        }
    }
}
