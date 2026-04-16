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
        List<SystemPromptSegment> systemPromptSegments) {

    /** Default model name used when no model is explicitly configured. */
    public static final String DEFAULT_MODEL = "claude-sonnet-4-20250514";

    /** Default maximum output tokens. */
    public static final int DEFAULT_MAX_TOKENS = 8096;

    /** Default temperature for model calls. */
    public static final double DEFAULT_TEMPERATURE = 1.0;

    /**
     * Backward-compatible constructor without fallbackModels, thinkingBudget, toolVerbosity,
     * systemPromptSegments.
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
                null);
    }

    /**
     * Backward-compatible constructor without fallbackModels, thinkingBudget, toolVerbosity,
     * systemPromptSegments.
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
                null);
    }

    /**
     * Configuration for extended thinking / chain-of-thought.
     *
     * @param enabled whether thinking is enabled
     * @param budgetTokens the token budget for thinking
     */
    public record ThinkingConfig(boolean enabled, int budgetTokens) {}

    /** Create a new builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link ModelConfig}. */
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

        private Builder() {}

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder addTool(ToolDefinition tool) {
            this.tools.add(tool);
            return this;
        }

        public Builder tools(List<ToolDefinition> tools) {
            this.tools.clear();
            this.tools.addAll(tools);
            return this;
        }

        public Builder thinking(ThinkingConfig thinking) {
            this.thinking = thinking;
            return this;
        }

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

        public Builder fallbackModels(List<String> fallbackModels) {
            this.fallbackModels = fallbackModels;
            return this;
        }

        public Builder thinkingBudget(Integer thinkingBudget) {
            this.thinkingBudget = thinkingBudget;
            return this;
        }

        public Builder toolVerbosity(ToolVerbosity toolVerbosity) {
            this.toolVerbosity = toolVerbosity;
            return this;
        }

        public Builder addSegment(SystemPromptSegment segment) {
            if (this.systemPromptSegments == null) {
                this.systemPromptSegments = new ArrayList<>();
            }
            this.systemPromptSegments.add(segment);
            return this;
        }

        public Builder segments(List<SystemPromptSegment> segments) {
            this.systemPromptSegments = segments != null ? new ArrayList<>(segments) : null;
            return this;
        }

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
                    systemPromptSegments != null ? List.copyOf(systemPromptSegments) : null);
        }
    }
}
