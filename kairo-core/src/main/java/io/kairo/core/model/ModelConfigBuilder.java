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

import io.kairo.api.model.ModelConfig;
import io.kairo.api.tool.ToolDefinition;
import java.util.List;

/**
 * Enhanced builder for {@link ModelConfig} with convenience methods.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * ModelConfig config = ModelConfigBuilder.create()
 *     .model(ModelConfig.DEFAULT_MODEL)
 *     .maxTokens(ModelConfig.DEFAULT_MAX_TOKENS)
 *     .temperature(0.7)
 *     .thinking(true, 10000)
 *     .systemPrompt("You are a helpful assistant")
 *     .build();
 * }</pre>
 */
public final class ModelConfigBuilder {

    private final ModelConfig.Builder delegate;

    private ModelConfigBuilder() {
        this.delegate = ModelConfig.builder();
    }

    /** Create a new builder. */
    public static ModelConfigBuilder create() {
        return new ModelConfigBuilder();
    }

    /** Set the model name. */
    public ModelConfigBuilder model(String model) {
        delegate.model(model);
        return this;
    }

    /** Set the maximum output tokens. */
    public ModelConfigBuilder maxTokens(int maxTokens) {
        delegate.maxTokens(maxTokens);
        return this;
    }

    /** Set the temperature. */
    public ModelConfigBuilder temperature(double temperature) {
        delegate.temperature(temperature);
        return this;
    }

    /** Add a tool definition. */
    public ModelConfigBuilder addTool(ToolDefinition tool) {
        delegate.addTool(tool);
        return this;
    }

    /** Set tool definitions. */
    public ModelConfigBuilder tools(List<ToolDefinition> tools) {
        delegate.tools(tools);
        return this;
    }

    /**
     * Configure extended thinking with shortcut parameters.
     *
     * @param enabled whether thinking is enabled
     * @param budgetTokens the token budget for thinking
     * @return this builder
     */
    public ModelConfigBuilder thinking(boolean enabled, int budgetTokens) {
        delegate.thinking(new ModelConfig.ThinkingConfig(enabled, budgetTokens));
        return this;
    }

    /** Configure thinking with a {@link ModelConfig.ThinkingConfig} object. */
    public ModelConfigBuilder thinking(ModelConfig.ThinkingConfig thinking) {
        delegate.thinking(thinking);
        return this;
    }

    /** Set the system prompt. */
    public ModelConfigBuilder systemPrompt(String systemPrompt) {
        delegate.systemPrompt(systemPrompt);
        return this;
    }

    /** Build the configuration. */
    public ModelConfig build() {
        return delegate.build();
    }

    // ---- Preset configurations ----

    /**
     * Create a preset config for Claude Sonnet with sensible defaults.
     *
     * @return a pre-configured builder for Claude Sonnet
     */
    public static ModelConfigBuilder claudeSonnet() {
        return create()
                .model(ModelConfig.DEFAULT_MODEL)
                .maxTokens(ModelConfig.DEFAULT_MAX_TOKENS)
                .temperature(ModelConfig.DEFAULT_TEMPERATURE);
    }

    /**
     * Create a preset config for Claude Sonnet with extended thinking enabled.
     *
     * @param budgetTokens the thinking token budget
     * @return a pre-configured builder for Claude Sonnet with thinking
     */
    public static ModelConfigBuilder claudeSonnetWithThinking(int budgetTokens) {
        return claudeSonnet().thinking(true, budgetTokens);
    }
}
