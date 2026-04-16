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
package io.kairo.api.agent;

import io.kairo.api.context.ContextManager;
import io.kairo.api.memory.MemoryStore;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.tool.ToolRegistry;
import io.kairo.api.tracing.Tracer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Configuration for creating an agent.
 *
 * <p>Use the {@link Builder} to construct instances:
 *
 * <pre>{@code
 * AgentConfig config = AgentConfig.builder()
 *     .name("code-assistant")
 *     .systemPrompt("You are a helpful coding assistant.")
 *     .modelProvider(provider)
 *     .toolRegistry(registry)
 *     .build();
 * }</pre>
 */
public record AgentConfig(
        String name,
        String systemPrompt,
        ModelProvider modelProvider,
        ToolRegistry toolRegistry,
        int maxIterations,
        Duration timeout,
        int tokenBudget,
        String modelName,
        List<Object> hooks,
        ContextManager contextManager,
        MemoryStore memoryStore,
        String sessionId,
        Tracer tracer,
        List<Object> mcpServerConfigs) {

    /** Create a new builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link AgentConfig}. */
    public static class Builder {
        private String name;
        private String systemPrompt;
        private ModelProvider modelProvider;
        private ToolRegistry toolRegistry;
        private int maxIterations = 100;
        private Duration timeout = Duration.ofMinutes(10);
        private int tokenBudget = 200_000;
        private String modelName;
        private final List<Object> hooks = new ArrayList<>();
        private ContextManager contextManager;
        private MemoryStore memoryStore;
        private String sessionId;
        private Tracer tracer;
        private final List<Object> mcpServerConfigs = new ArrayList<>();

        private Builder() {}

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder modelProvider(ModelProvider modelProvider) {
            this.modelProvider = modelProvider;
            return this;
        }

        public Builder toolRegistry(ToolRegistry toolRegistry) {
            this.toolRegistry = toolRegistry;
            return this;
        }

        public Builder maxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder tokenBudget(int tokenBudget) {
            this.tokenBudget = tokenBudget;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder addHook(Object hook) {
            this.hooks.add(hook);
            return this;
        }

        public Builder contextManager(ContextManager contextManager) {
            this.contextManager = contextManager;
            return this;
        }

        public Builder memoryStore(MemoryStore memoryStore) {
            this.memoryStore = memoryStore;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder tracer(Tracer tracer) {
            this.tracer = tracer;
            return this;
        }

        public Builder addMcpServerConfig(Object config) {
            this.mcpServerConfigs.add(config);
            return this;
        }

        public AgentConfig build() {
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(modelProvider, "modelProvider must not be null");
            return new AgentConfig(
                    name,
                    systemPrompt,
                    modelProvider,
                    toolRegistry,
                    maxIterations,
                    timeout,
                    tokenBudget,
                    modelName,
                    List.copyOf(hooks),
                    contextManager,
                    memoryStore,
                    sessionId,
                    tracer,
                    List.copyOf(mcpServerConfigs));
        }
    }
}
