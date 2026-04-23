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
import io.kairo.api.evolution.EvolutionConfig;
import io.kairo.api.execution.ResourceConstraint;
import io.kairo.api.memory.MemoryStore;
import io.kairo.api.middleware.Middleware;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.tool.ToolRegistry;
import io.kairo.api.tracing.Tracer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

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
        List<Middleware> middlewares,
        ContextManager contextManager,
        MemoryStore memoryStore,
        String sessionId,
        Tracer tracer,
        List<Object> mcpServerConfigs,
        int mcpMaxToolsPerServer,
        boolean mcpStrictSchemaAlignment,
        String mcpToolSearchQuery,
        int loopHashWarnThreshold,
        int loopHashHardLimit,
        int loopFreqWarnThreshold,
        int loopFreqHardLimit,
        Duration loopFreqWindow,
        @Nullable List<ResourceConstraint> resourceConstraints,
        @Nullable EvolutionConfig evolutionConfig,
        @Nullable List<SystemPromptContributor> systemPromptContributors) {

    /**
     * Derived view of MCP-related configuration as a {@link McpCapabilityConfig}. Useful for
     * starters that prefer the capability-config API introduced in v0.10 instead of the legacy
     * per-field accessors.
     */
    public McpCapabilityConfig mcpCapability() {
        return new McpCapabilityConfig(
                mcpServerConfigs,
                mcpMaxToolsPerServer,
                mcpStrictSchemaAlignment,
                mcpToolSearchQuery);
    }

    /**
     * Derived view of loop-detection thresholds as a {@link LoopDetectionConfig}. Prefer this
     * accessor in v0.10+ consumers so the individual loop-detection fields can eventually be
     * removed.
     */
    public LoopDetectionConfig loopDetection() {
        return new LoopDetectionConfig(
                loopHashWarnThreshold,
                loopHashHardLimit,
                loopFreqWarnThreshold,
                loopFreqHardLimit,
                loopFreqWindow);
    }

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
        private final List<Middleware> middlewares = new ArrayList<>();
        private ContextManager contextManager;
        private MemoryStore memoryStore;
        private String sessionId;
        private Tracer tracer;
        private final List<Object> mcpServerConfigs = new ArrayList<>();
        private int mcpMaxToolsPerServer = 128;
        private boolean mcpStrictSchemaAlignment = true;
        private String mcpToolSearchQuery;
        private int loopHashWarnThreshold = 3;
        private int loopHashHardLimit = 5;
        private int loopFreqWarnThreshold = 50;
        private int loopFreqHardLimit = 100;
        private Duration loopFreqWindow = Duration.ofMinutes(10);
        @Nullable private List<ResourceConstraint> resourceConstraints;
        @Nullable private EvolutionConfig evolutionConfig;
        @Nullable private List<SystemPromptContributor> systemPromptContributors;

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
         * Add a {@link Middleware} to the pipeline that runs before the agent loop.
         *
         * @param middleware the middleware to register
         * @return this builder
         */
        public Builder addMiddleware(Middleware middleware) {
            this.middlewares.add(middleware);
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
         * @deprecated since v0.10 — configure MCP via {@link #mcpCapability(McpCapabilityConfig)}.
         */
        @Deprecated(since = "0.10", forRemoval = true)
        public Builder addMcpServerConfig(Object config) {
            this.mcpServerConfigs.add(config);
            return this;
        }

        /**
         * Set max MCP tools to register per server.
         *
         * <p>Provides a hard upper bound so one remote server cannot flood the runtime with an
         * unbounded tool list.
         *
         * @deprecated since v0.10 — configure MCP via {@link #mcpCapability(McpCapabilityConfig)}.
         */
        @Deprecated(since = "0.10", forRemoval = true)
        public Builder mcpMaxToolsPerServer(int maxTools) {
            if (maxTools < 1) {
                throw new IllegalArgumentException("mcpMaxToolsPerServer must be >= 1");
            }
            this.mcpMaxToolsPerServer = maxTools;
            return this;
        }

        /**
         * Enable/disable strict schema alignment when importing MCP tool schemas.
         *
         * <p>When enabled, schema mismatches are normalized conservatively by the runtime.
         *
         * @deprecated since v0.10 — configure MCP via {@link #mcpCapability(McpCapabilityConfig)}.
         */
        @Deprecated(since = "0.10", forRemoval = true)
        public Builder mcpStrictSchemaAlignment(boolean strict) {
            this.mcpStrictSchemaAlignment = strict;
            return this;
        }

        /**
         * Restrict MCP tool registration using a case-insensitive name/description search query.
         *
         * <p>When set, only MCP tools that match this query are registered into the runtime.
         *
         * @deprecated since v0.10 — configure MCP via {@link #mcpCapability(McpCapabilityConfig)}.
         *     Expected to be removed once the MCP fields fully migrate to the {@code kairo-mcp}
         *     starter in a subsequent wave.
         */
        @Deprecated(since = "0.10", forRemoval = true)
        public Builder mcpToolSearchQuery(String query) {
            this.mcpToolSearchQuery = query;
            return this;
        }

        /**
         * Configure loop detection thresholds.
         *
         * @param hashWarn consecutive identical hash count to trigger WARN (default 3)
         * @param hashStop consecutive identical hash count to trigger HARD_STOP (default 5)
         * @param freqWarn per-tool call count within window to trigger WARN (default 50)
         * @param freqStop per-tool call count within window to trigger HARD_STOP (default 100)
         * @param freqWindow the sliding time window for frequency detection (default 10 min)
         * @return this builder
         */
        public Builder loopDetection(
                int hashWarn, int hashStop, int freqWarn, int freqStop, Duration freqWindow) {
            this.loopHashWarnThreshold = hashWarn;
            this.loopHashHardLimit = hashStop;
            this.loopFreqWarnThreshold = freqWarn;
            this.loopFreqHardLimit = freqStop;
            this.loopFreqWindow = freqWindow;
            return this;
        }

        /**
         * Configure loop detection thresholds via the new {@link LoopDetectionConfig} capability
         * record. Null clears any prior configuration and restores defaults.
         *
         * @param config the loop detection configuration
         * @return this builder
         * @since v0.10
         */
        public Builder loopDetectionConfig(LoopDetectionConfig config) {
            LoopDetectionConfig effective = config != null ? config : LoopDetectionConfig.DEFAULTS;
            this.loopHashWarnThreshold = effective.hashWarnThreshold();
            this.loopHashHardLimit = effective.hashHardLimit();
            this.loopFreqWarnThreshold = effective.freqWarnThreshold();
            this.loopFreqHardLimit = effective.freqHardLimit();
            this.loopFreqWindow = effective.freqWindow();
            return this;
        }

        /**
         * Configure MCP integration via the {@link McpCapabilityConfig} capability record. Null
         * clears any prior configuration.
         *
         * @param capability the MCP capability config
         * @return this builder
         * @since v0.10
         */
        public Builder mcpCapability(McpCapabilityConfig capability) {
            McpCapabilityConfig effective =
                    capability != null ? capability : McpCapabilityConfig.EMPTY;
            this.mcpServerConfigs.clear();
            this.mcpServerConfigs.addAll(effective.serverConfigs());
            this.mcpMaxToolsPerServer = effective.maxToolsPerServer();
            this.mcpStrictSchemaAlignment = effective.strictSchemaAlignment();
            this.mcpToolSearchQuery = effective.toolSearchQuery();
            return this;
        }

        /**
         * Set custom {@link ResourceConstraint}s for the agent.
         *
         * <p>When set, these constraints replace the default iteration/token/timeout checks. Pass
         * an empty list to explicitly opt out of all resource constraints.
         *
         * @param constraints the resource constraints
         * @return this builder
         */
        public Builder resourceConstraints(List<ResourceConstraint> constraints) {
            this.resourceConstraints = constraints != null ? List.copyOf(constraints) : null;
            return this;
        }

        /**
         * Set the {@link EvolutionConfig} for the self-evolution subsystem.
         *
         * @param evolutionConfig the evolution configuration
         * @return this builder
         */
        public Builder evolutionConfig(EvolutionConfig evolutionConfig) {
            this.evolutionConfig = evolutionConfig;
            return this;
        }

        /**
         * Set the {@link SystemPromptContributor}s for dynamic prompt injection.
         *
         * @param contributors the system prompt contributors
         * @return this builder
         */
        public Builder systemPromptContributors(List<SystemPromptContributor> contributors) {
            this.systemPromptContributors = contributors != null ? List.copyOf(contributors) : null;
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
                    List.copyOf(middlewares),
                    contextManager,
                    memoryStore,
                    sessionId,
                    tracer,
                    List.copyOf(mcpServerConfigs),
                    mcpMaxToolsPerServer,
                    mcpStrictSchemaAlignment,
                    mcpToolSearchQuery,
                    loopHashWarnThreshold,
                    loopHashHardLimit,
                    loopFreqWarnThreshold,
                    loopFreqHardLimit,
                    loopFreqWindow,
                    resourceConstraints,
                    evolutionConfig,
                    systemPromptContributors);
        }
    }
}
