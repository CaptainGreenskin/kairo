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

    /**
     * Create a new {@link Builder} for constructing an {@link AgentConfig}.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link AgentConfig}. Provides a fluent API for constructing immutable config
     * instances.
     */
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

        /**
         * Set the agent name. This is required and used in logging, tracing, and multi-agent
         * routing.
         *
         * @param name the agent name; must not be {@code null}
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Set the system prompt that defines the agent's persona and behavioral instructions.
         *
         * @param systemPrompt the system prompt text
         * @return this builder
         */
        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        /**
         * Set the {@link ModelProvider} used for LLM calls. This is required.
         *
         * @param modelProvider the model provider; must not be {@code null}
         * @return this builder
         */
        public Builder modelProvider(ModelProvider modelProvider) {
            this.modelProvider = modelProvider;
            return this;
        }

        /**
         * Set the {@link ToolRegistry} that provides tool definitions and executors.
         *
         * @param toolRegistry the tool registry
         * @return this builder
         */
        public Builder toolRegistry(ToolRegistry toolRegistry) {
            this.toolRegistry = toolRegistry;
            return this;
        }

        /**
         * Set the maximum number of reasoning/acting iterations before the agent stops. Defaults to
         * {@code 100}.
         *
         * @param maxIterations the iteration limit; must be positive
         * @return this builder
         */
        public Builder maxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
            return this;
        }

        /**
         * Set the overall timeout for a single {@code agent.call()} invocation. Defaults to 10
         * minutes.
         *
         * @param timeout the timeout duration
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Set the token budget for the agent's context window. Defaults to {@code 200_000}.
         *
         * @param tokenBudget the maximum token count
         * @return this builder
         */
        public Builder tokenBudget(int tokenBudget) {
            this.tokenBudget = tokenBudget;
            return this;
        }

        /**
         * Override the model name used for LLM calls, independently of the provider.
         *
         * @param modelName the model identifier
         * @return this builder
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Add a hook handler POJO whose annotated methods participate in the agent lifecycle.
         *
         * @param hook the hook handler to register
         * @return this builder
         * @see io.kairo.api.hook.HookChain
         */
        public Builder addHook(Object hook) {
            this.hooks.add(hook);
            return this;
        }

        /**
         * Set the {@link ContextManager} for conversation history and compaction.
         *
         * @param contextManager the context manager
         * @return this builder
         */
        public Builder contextManager(ContextManager contextManager) {
            this.contextManager = contextManager;
            return this;
        }

        /**
         * Set the {@link MemoryStore} for persistent agent memory.
         *
         * @param memoryStore the memory store
         * @return this builder
         */
        public Builder memoryStore(MemoryStore memoryStore) {
            this.memoryStore = memoryStore;
            return this;
        }

        /**
         * Set the session ID for conversation continuity across agent restarts.
         *
         * @param sessionId the session identifier
         * @return this builder
         */
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        /**
         * Set the {@link Tracer} for observability and span recording.
         *
         * @param tracer the tracer instance
         * @return this builder
         */
        public Builder tracer(Tracer tracer) {
            this.tracer = tracer;
            return this;
        }

        /**
         * Add an MCP (Model Context Protocol) server configuration for dynamic tool discovery.
         *
         * @param config the MCP server configuration object
         * @return this builder
         */
        public Builder addMcpServerConfig(Object config) {
            this.mcpServerConfigs.add(config);
            return this;
        }

        /**
         * Build an immutable {@link AgentConfig} from the current builder state.
         *
         * @return the constructed config
         * @throws NullPointerException if {@code name} or {@code modelProvider} has not been set
         */
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
