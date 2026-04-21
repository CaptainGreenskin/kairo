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

import io.kairo.api.context.SystemPromptSegment;
import io.kairo.api.tool.ToolDefinition;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for a model call, including model name, token limits, tools, and thinking settings.
 *
 * <p>Use the {@link Builder} to construct instances:
 *
 * <pre>{@code
 * ModelConfig config = ModelConfig.builder()
 *     .model(ModelConfig.DEFAULT_MODEL)
 *     .maxTokens(4096)
 *     .temperature(0.7)
 *     .build();
 * }</pre>
 */
public record ModelConfig(
        String model,
        int maxTokens,
        double temperature,
        List<ToolDefinition> tools,
        ThinkingConfig thinking,
        String systemPrompt,
        Map<String, String> systemPromptParts,
        List<String> fallbackModels,
        Integer thinkingBudget,
        ToolVerbosity toolVerbosity,
        List<SystemPromptSegment> systemPromptSegments,
        Class<?> responseSchema,
        Double effort,
        RetryConfig retryConfig,
        Duration timeout) {

    /**
     * Default model name used when no model is explicitly configured.
     *
     * <p>Set to {@code "claude-sonnet-4-20250514"} as it offers the best balance of capability,
     * speed, and cost for agentic workloads that require tool use and extended reasoning.
     */
    public static final String DEFAULT_MODEL = "claude-sonnet-4-20250514";

    /**
     * Default maximum output tokens per model call.
     *
     * <p>Set to {@code 8096} to allow substantial responses (including tool-call JSON) while
     * staying within provider rate limits for most tiers.
     */
    public static final int DEFAULT_MAX_TOKENS = 8096;

    /**
     * Default sampling temperature for model calls.
     *
     * <p>Set to {@code 1.0} (the Anthropic default) to preserve the model's full creative range.
     * Lower values can be configured per-call via the {@link Builder} when deterministic output is
     * desired.
     */
    public static final double DEFAULT_TEMPERATURE = 1.0;

    /**
     * Backward-compatible constructor without {@code fallbackModels}, {@code thinkingBudget},
     * {@code toolVerbosity}, and {@code systemPromptSegments}.
     *
     * @param model the model identifier
     * @param maxTokens maximum output tokens
     * @param temperature sampling temperature
     * @param tools tool definitions available to the model
     * @param thinking thinking/chain-of-thought configuration
     * @param systemPrompt the system prompt text
     * @param systemPromptParts cache-aware system prompt parts
     * @param fallbackModels ordered list of fallback model names
     * @param thinkingBudget optional token budget override for thinking
     * @param toolVerbosity verbosity level for tool descriptions
     */
    public ModelConfig(
            String model,
            int maxTokens,
            double temperature,
            List<ToolDefinition> tools,
            ThinkingConfig thinking,
            String systemPrompt,
            Map<String, String> systemPromptParts,
            List<String> fallbackModels,
            Integer thinkingBudget,
            ToolVerbosity toolVerbosity) {
        this(
                model,
                maxTokens,
                temperature,
                tools,
                thinking,
                systemPrompt,
                systemPromptParts,
                fallbackModels,
                thinkingBudget,
                toolVerbosity,
                null,
                null,
                null,
                null,
                null);
    }

    /**
     * Backward-compatible constructor without {@code fallbackModels}, {@code thinkingBudget},
     * {@code toolVerbosity}, and {@code systemPromptSegments}.
     *
     * @param model the model identifier
     * @param maxTokens maximum output tokens
     * @param temperature sampling temperature
     * @param tools tool definitions available to the model
     * @param thinking thinking/chain-of-thought configuration
     * @param systemPrompt the system prompt text
     * @param systemPromptParts cache-aware system prompt parts
     */
    public ModelConfig(
            String model,
            int maxTokens,
            double temperature,
            List<ToolDefinition> tools,
            ThinkingConfig thinking,
            String systemPrompt,
            Map<String, String> systemPromptParts) {
        this(
                model,
                maxTokens,
                temperature,
                tools,
                thinking,
                systemPrompt,
                systemPromptParts,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /**
     * Configuration for extended thinking / chain-of-thought.
     *
     * @param enabled whether thinking is enabled
     * @param budgetTokens the token budget for thinking
     */
    public record ThinkingConfig(boolean enabled, int budgetTokens) {}

    /** Backward-compatible constructor without {@code responseSchema}. */
    public ModelConfig(
            String model,
            int maxTokens,
            double temperature,
            List<ToolDefinition> tools,
            ThinkingConfig thinking,
            String systemPrompt,
            Map<String, String> systemPromptParts,
            List<String> fallbackModels,
            Integer thinkingBudget,
            ToolVerbosity toolVerbosity,
            List<SystemPromptSegment> systemPromptSegments) {
        this(
                model,
                maxTokens,
                temperature,
                tools,
                thinking,
                systemPrompt,
                systemPromptParts,
                fallbackModels,
                thinkingBudget,
                toolVerbosity,
                systemPromptSegments,
                null,
                null,
                null,
                null);
    }

    /**
     * Create a new {@link Builder} for constructing a {@link ModelConfig}.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ModelConfig}. Provides a fluent API for constructing immutable config
     * instances.
     */
    public static class Builder {
        private String model;
        private int maxTokens = 4096;
        private double temperature = 1.0;
        private final List<ToolDefinition> tools = new ArrayList<>();
        private ThinkingConfig thinking = new ThinkingConfig(false, 0);
        private String systemPrompt;
        private Map<String, String> systemPromptParts;
        private List<String> fallbackModels;
        private Integer thinkingBudget;
        private ToolVerbosity toolVerbosity;
        private List<SystemPromptSegment> systemPromptSegments;
        private Class<?> responseSchema;
        private Double effort;
        private RetryConfig retryConfig;
        private Duration timeout;

        private Builder() {}

        /**
         * Set the model identifier (e.g. {@code "claude-sonnet-4-20250514"}).
         *
         * @param model the model name; must not be {@code null}
         * @return this builder
         */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * Set the maximum number of output tokens the model may generate.
         *
         * @param maxTokens the token limit; must be positive
         * @return this builder
         */
        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * Set the sampling temperature.
         *
         * @param temperature value between 0.0 (deterministic) and 2.0 (most random)
         * @return this builder
         */
        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * Add a single tool definition that the model may invoke.
         *
         * @param tool the tool definition to add
         * @return this builder
         */
        public Builder addTool(ToolDefinition tool) {
            this.tools.add(tool);
            return this;
        }

        /**
         * Replace all tool definitions with the given list.
         *
         * @param tools the tool definitions; must not be {@code null}
         * @return this builder
         */
        public Builder tools(List<ToolDefinition> tools) {
            this.tools.clear();
            this.tools.addAll(tools);
            return this;
        }

        /**
         * Set the thinking (chain-of-thought) configuration.
         *
         * @param thinking the thinking config
         * @return this builder
         */
        public Builder thinking(ThinkingConfig thinking) {
            this.thinking = thinking;
            return this;
        }

        /**
         * Set the system prompt text.
         *
         * @param systemPrompt the system prompt
         * @return this builder
         */
        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        /**
         * Set the system prompt parts for cache-aware serialization. Keys: "staticPrefix",
         * "dynamicSuffix".
         *
         * @param parts the static/dynamic parts of the system prompt
         * @return this builder
         */
        public Builder systemPromptParts(Map<String, String> parts) {
            this.systemPromptParts = parts;
            return this;
        }

        /**
         * Set the ordered list of fallback model names to try if the primary model fails.
         *
         * @param fallbackModels the fallback model names
         * @return this builder
         */
        public Builder fallbackModels(List<String> fallbackModels) {
            this.fallbackModels = fallbackModels;
            return this;
        }

        /**
         * Set an explicit token budget for the thinking phase, overriding {@link
         * ThinkingConfig#budgetTokens()}.
         *
         * @param thinkingBudget the thinking token budget, or {@code null} to use the default
         * @return this builder
         */
        public Builder thinkingBudget(Integer thinkingBudget) {
            this.thinkingBudget = thinkingBudget;
            return this;
        }

        /**
         * Set the verbosity level for tool descriptions sent to the model.
         *
         * @param toolVerbosity the tool verbosity level
         * @return this builder
         */
        public Builder toolVerbosity(ToolVerbosity toolVerbosity) {
            this.toolVerbosity = toolVerbosity;
            return this;
        }

        /**
         * Add a single system prompt segment for structured prompt composition.
         *
         * @param segment the segment to add
         * @return this builder
         */
        public Builder addSegment(SystemPromptSegment segment) {
            if (this.systemPromptSegments == null) {
                this.systemPromptSegments = new ArrayList<>();
            }
            this.systemPromptSegments.add(segment);
            return this;
        }

        /**
         * Replace all system prompt segments with the given list.
         *
         * @param segments the segments, or {@code null} to clear
         * @return this builder
         */
        public Builder segments(List<SystemPromptSegment> segments) {
            this.systemPromptSegments = segments != null ? new ArrayList<>(segments) : null;
            return this;
        }

        /**
         * Set the response schema class for structured output.
         *
         * <p>When set, the provider will constrain the model output to match the JSON schema
         * derived from this class. Use {@link ModelResponse#contentAs(Class)} to deserialize the
         * response.
         *
         * @param responseSchema the class whose JSON schema constrains the output, or null to
         *     disable
         * @return this builder
         */
        public Builder responseSchema(Class<?> responseSchema) {
            this.responseSchema = responseSchema;
            return this;
        }

        /**
         * Set the effort level for model reasoning.
         *
         * <p>A continuous value between 0.0 and 1.0 that controls reasoning effort. Providers map
         * this to their native parameters (e.g., Anthropic → {@code /effort}, OpenAI → {@code
         * reasoning_effort}). Null means provider default behavior.
         *
         * @param effort the effort level (0.0-1.0), or {@code null} for provider default
         * @return this builder
         */
        public Builder effort(Double effort) {
            this.effort = effort;
            return this;
        }

        /**
         * Set the retry configuration for model API calls.
         *
         * <p>When {@code null}, providers fall back to {@link RetryConfig#MODEL_DEFAULTS}.
         *
         * @param retryConfig the retry config, or {@code null} for defaults
         * @return this builder
         */
        public Builder retryConfig(RetryConfig retryConfig) {
            this.retryConfig = retryConfig;
            return this;
        }

        /**
         * Set the overall timeout for a model call.
         *
         * <p>When {@code null}, providers use their own default timeout (typically 30s for
         * non-streaming, 5min idle timeout for streaming).
         *
         * @param timeout the timeout duration, or {@code null} for provider default
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Build an immutable {@link ModelConfig} from the current builder state.
         *
         * @return the constructed config
         * @throws NullPointerException if {@code model} has not been set
         */
        public ModelConfig build() {
            Objects.requireNonNull(model, "model must not be null");
            return new ModelConfig(
                    model,
                    maxTokens,
                    temperature,
                    List.copyOf(tools),
                    thinking,
                    systemPrompt,
                    systemPromptParts != null ? Map.copyOf(systemPromptParts) : null,
                    fallbackModels != null ? List.copyOf(fallbackModels) : null,
                    thinkingBudget,
                    toolVerbosity,
                    systemPromptSegments != null ? List.copyOf(systemPromptSegments) : null,
                    responseSchema,
                    effort,
                    retryConfig,
                    timeout);
        }
    }
}
