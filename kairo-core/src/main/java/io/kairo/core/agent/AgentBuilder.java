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
package io.kairo.core.agent;

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentBuilderCustomizer;
import io.kairo.api.agent.AgentConfig;
import io.kairo.api.agent.AgentSnapshot;
import io.kairo.api.agent.SystemPromptContributor;
import io.kairo.api.context.ContextManager;
import io.kairo.api.evolution.EvolutionConfig;
import io.kairo.api.execution.DurableExecutionStore;
import io.kairo.api.execution.ResourceConstraint;
import io.kairo.api.guardrail.GuardrailChain;
import io.kairo.api.memory.MemoryStore;
import io.kairo.api.middleware.Middleware;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.api.tool.ToolRegistry;
import io.kairo.api.tool.UserApprovalHandler;
import io.kairo.api.tracing.Tracer;
import io.kairo.core.context.CompactionThresholds;
import io.kairo.core.context.DefaultContextManager;
import io.kairo.core.context.TokenBudgetManager;
import io.kairo.core.execution.RecoveryHandler;
import io.kairo.core.hook.DefaultHookChain;
import io.kairo.core.middleware.DefaultMiddlewarePipeline;
import io.kairo.core.session.SessionManager;
import io.kairo.core.shutdown.GracefulShutdownManager;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fluent builder for creating {@link DefaultReActAgent} instances.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * Agent agent = AgentBuilder.create()
 *     .name("code-assistant")
 *     .model(anthropicProvider)
 *     .tools(toolRegistry)
 *     .toolExecutor(toolExecutor)
 *     .systemPrompt("You are a helpful coding assistant.")
 *     .maxIterations(50)
 *     .tokenBudget(200_000)
 *     .hook(new LoggingHook())
 *     .build();
 * }</pre>
 */
public class AgentBuilder {

    private String name;
    private String systemPrompt;
    private ModelProvider modelProvider;
    private ToolRegistry toolRegistry;
    private ToolExecutor toolExecutor;
    private int maxIterations = 50;
    private Duration timeout = Duration.ofMinutes(30);
    private int tokenBudget = 200_000;
    private String modelName;
    private final List<Object> hooks = new ArrayList<>();
    private final List<Middleware> middlewares = new ArrayList<>();
    private ContextManager contextManager;
    private MemoryStore memoryStore;
    private String sessionId;
    private SessionManager sessionManager;
    private UserApprovalHandler approvalHandler;
    private Tracer tracer;
    private boolean streamingEnabled = false;
    private GracefulShutdownManager shutdownManager;
    private AgentSnapshot restoreFrom;
    private final List<Object> mcpServerConfigs = new ArrayList<>();
    private int loopHashWarn = 3;
    private int loopHashStop = 5;
    private int loopFreqWarn = 50;
    private int loopFreqStop = 100;
    private Duration loopFreqWindow = Duration.ofMinutes(10);
    private Map<String, Object> toolDependencies = Map.of();
    private CompactionThresholds compactionThresholds;
    private GuardrailChain guardrailChain;
    @javax.annotation.Nullable private DurableExecutionStore durableExecutionStore;
    @javax.annotation.Nullable private RecoveryHandler recoveryHandler;
    private boolean recoveryOnStartup = false;
    @javax.annotation.Nullable private java.util.List<ResourceConstraint> resourceConstraints;
    @javax.annotation.Nullable private EvolutionConfig evolutionConfig;
    private final List<AgentBuilderCustomizer> customizers = new ArrayList<>();
    private final List<SystemPromptContributor> systemPromptContributors = new ArrayList<>();

    private AgentBuilder() {}

    /** Create a new builder. */
    public static AgentBuilder create() {
        return new AgentBuilder();
    }

    /** Set the agent name. */
    public AgentBuilder name(String name) {
        this.name = name;
        return this;
    }

    /** Set the model provider for LLM calls. */
    public AgentBuilder model(ModelProvider provider) {
        this.modelProvider = provider;
        return this;
    }

    /** Set the tool registry for available tools. */
    public AgentBuilder tools(ToolRegistry registry) {
        this.toolRegistry = registry;
        return this;
    }

    /** Set the tool executor for running tools. */
    public AgentBuilder toolExecutor(ToolExecutor executor) {
        this.toolExecutor = executor;
        return this;
    }

    /**
     * Set runtime dependencies to inject into tools via {@link io.kairo.api.tool.ToolContext}.
     *
     * <p>These dependencies (e.g., database connections, API clients) are made available to
     * context-aware tools at execution time.
     *
     * @param dependencies the dependencies map, or null for empty
     * @return this builder
     */
    public AgentBuilder toolDependencies(Map<String, Object> dependencies) {
        this.toolDependencies = dependencies != null ? Map.copyOf(dependencies) : Map.of();
        return this;
    }

    /** Set the system prompt. */
    public AgentBuilder systemPrompt(String prompt) {
        this.systemPrompt = prompt;
        return this;
    }

    /** Set the maximum number of ReAct loop iterations. */
    public AgentBuilder maxIterations(int max) {
        this.maxIterations = max;
        return this;
    }

    /** Set the overall timeout for agent execution. */
    public AgentBuilder timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    /** Set the token budget for the agent. */
    public AgentBuilder tokenBudget(int budget) {
        this.tokenBudget = budget;
        return this;
    }

    /**
     * Set the model name (e.g. "glm-4-plus", "gpt-4o").
     *
     * <p>This is a <strong>required</strong> parameter. The builder will throw {@link
     * IllegalStateException} at {@link #build()} time if not set.
     */
    public AgentBuilder modelName(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("modelName cannot be null or blank");
        }
        this.modelName = modelName;
        return this;
    }

    /** Register a hook handler. */
    public AgentBuilder hook(Object hookHandler) {
        if (hookHandler != null) {
            this.hooks.add(hookHandler);
        }
        return this;
    }

    /** Register a middleware that runs before the agent loop. */
    public AgentBuilder middleware(Middleware mw) {
        if (mw != null) {
            this.middlewares.add(mw);
        }
        return this;
    }

    /** Set the context manager for auto-compaction (optional). */
    public AgentBuilder contextManager(ContextManager contextManager) {
        this.contextManager = contextManager;
        return this;
    }

    /** Set the memory store for session memory (optional). */
    public AgentBuilder memoryStore(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;
        return this;
    }

    /** Set the session ID for session memory recovery (optional). */
    public AgentBuilder sessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    /**
     * Enable file-based session persistence with the given storage directory.
     *
     * <p>Creates a {@link SessionManager} backed by a {@link io.kairo.core.memory.FileMemoryStore}
     * rooted at the specified directory.
     *
     * @param storageDir the directory for session file storage
     * @return this builder
     */
    public AgentBuilder sessionPersistence(Path storageDir) {
        this.sessionManager = new SessionManager(storageDir);
        return this;
    }

    /**
     * Set a custom {@link SessionManager} for session persistence.
     *
     * @param sessionManager the session manager
     * @return this builder
     */
    public AgentBuilder sessionManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
        return this;
    }

    /**
     * Set the approval handler for human-in-the-loop tool confirmation.
     *
     * @param handler the approval handler
     * @return this builder
     */
    public AgentBuilder approvalHandler(UserApprovalHandler handler) {
        this.approvalHandler = handler;
        return this;
    }

    /**
     * Set the tracer for observability and tracing.
     *
     * @param tracer the tracer implementation
     * @return this builder
     */
    public AgentBuilder tracer(Tracer tracer) {
        this.tracer = tracer;
        return this;
    }

    /**
     * Set the graceful shutdown manager.
     *
     * <p>If not set, a new instance will be created automatically.
     *
     * @param shutdownManager the shutdown manager
     * @return this builder
     */
    public AgentBuilder shutdownManager(GracefulShutdownManager shutdownManager) {
        this.shutdownManager = shutdownManager;
        return this;
    }

    /**
     * Restore an agent from a previously captured snapshot.
     *
     * <p>The restored agent will continue from the snapshot's conversation history, iteration
     * count, and token usage. Runtime dependencies (model provider, tool executor, etc.) must still
     * be set on this builder — they are not stored in the snapshot.
     *
     * @param snapshot the snapshot to restore from
     * @return this builder
     */
    public AgentBuilder restoreFrom(AgentSnapshot snapshot) {
        this.restoreFrom = snapshot;
        return this;
    }

    /**
     * Enable or disable streaming responses from the model provider.
     *
     * <p>When enabled, the agent receives tokens in real-time as they are generated. Streaming
     * automatically falls back to non-streaming if the provider doesn't support it.
     *
     * @param enabled true to enable streaming, false to disable (default: false)
     * @return this builder
     */
    public AgentBuilder streaming(boolean enabled) {
        this.streamingEnabled = enabled;
        return this;
    }

    /**
     * Add a stdio-based MCP server configuration.
     *
     * <p>Requires {@code kairo-mcp} on the classpath at runtime.
     *
     * @param name the server name (used as tool name prefix)
     * @param command the command to launch the server
     * @param args additional command arguments
     * @return this builder
     */
    public AgentBuilder mcpServer(String name, String command, String... args) {
        try {
            Class<?> configClass = Class.forName("io.kairo.mcp.McpServerConfig");
            var cmdList = new ArrayList<String>();
            cmdList.add(command);
            Collections.addAll(cmdList, args);
            Object config =
                    configClass
                            .getMethod("stdio", String.class, String[].class)
                            .invoke(null, name, cmdList.stream().toArray(String[]::new));
            mcpServerConfigs.add(config);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "kairo-mcp is not on the classpath. Add kairo-mcp dependency to use MCP servers.",
                    e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create MCP server config", e);
        }
        return this;
    }

    /**
     * Add a pre-built MCP server configuration object.
     *
     * <p>The object must be an instance of {@code io.kairo.mcp.McpServerConfig}. Requires {@code
     * kairo-mcp} on the classpath at runtime.
     *
     * @param mcpServerConfig the MCP server config
     * @return this builder
     */
    public AgentBuilder mcpServer(Object mcpServerConfig) {
        Objects.requireNonNull(mcpServerConfig, "mcpServerConfig must not be null");
        mcpServerConfigs.add(mcpServerConfig);
        return this;
    }

    /**
     * Configure loop detection thresholds for the ReAct loop.
     *
     * <p>If not called, sensible defaults apply (hash warn=3, hash stop=5, freq warn=50, freq
     * stop=100, window=10min).
     *
     * @param hashWarn consecutive identical tool-call hashes to trigger a warning
     * @param hashStop consecutive identical tool-call hashes to hard-stop
     * @param freqWarn per-tool call count within window to trigger a warning
     * @param freqStop per-tool call count within window to hard-stop
     * @param freqWindow sliding time window for frequency detection
     * @return this builder
     */
    public AgentBuilder loopDetection(
            int hashWarn, int hashStop, int freqWarn, int freqStop, Duration freqWindow) {
        this.loopHashWarn = hashWarn;
        this.loopHashStop = hashStop;
        this.loopFreqWarn = freqWarn;
        this.loopFreqStop = freqStop;
        this.loopFreqWindow = freqWindow;
        return this;
    }

    /**
     * Configure loop detection via the capability record introduced in v0.10.
     *
     * @param config the loop detection configuration, or {@code null} to restore defaults
     * @return this builder
     */
    public AgentBuilder loopDetection(io.kairo.api.agent.LoopDetectionConfig config) {
        io.kairo.api.agent.LoopDetectionConfig effective =
                config != null ? config : io.kairo.api.agent.LoopDetectionConfig.DEFAULTS;
        this.loopHashWarn = effective.hashWarnThreshold();
        this.loopHashStop = effective.hashHardLimit();
        this.loopFreqWarn = effective.freqWarnThreshold();
        this.loopFreqStop = effective.freqHardLimit();
        this.loopFreqWindow = effective.freqWindow();
        return this;
    }

    /**
     * Configure durable execution in one capability call. Wires both the store and
     * recovery-on-startup behaviour.
     *
     * @param capability the durable capability, or {@code null} for disabled
     * @return this builder
     */
    public AgentBuilder durableCapability(io.kairo.api.agent.DurableCapabilityConfig capability) {
        io.kairo.api.agent.DurableCapabilityConfig effective =
                capability != null
                        ? capability
                        : io.kairo.api.agent.DurableCapabilityConfig.DISABLED;
        this.durableExecutionStore = effective.store();
        this.recoveryOnStartup = effective.recoveryOnStartup();
        return this;
    }

    /**
     * Set compaction thresholds for the agent's context compaction pipeline.
     *
     * <p>These thresholds control when each compaction stage triggers, the circuit breaker limit,
     * and the token buffer. If not set, sensible defaults are used (Principle #6).
     *
     * @param thresholds the compaction thresholds
     * @return this builder
     */
    public AgentBuilder compactionThresholds(CompactionThresholds thresholds) {
        this.compactionThresholds = thresholds;
        return this;
    }

    /**
     * Set the guardrail chain for policy evaluation at model and tool boundaries.
     *
     * <p>If not set (null), guardrail evaluation is skipped — backward compatible.
     *
     * @param guardrailChain the guardrail chain, or null to disable
     * @return this builder
     */
    public AgentBuilder guardrailChain(GuardrailChain guardrailChain) {
        this.guardrailChain = guardrailChain;
        return this;
    }

    /**
     * Set the durable execution store for crash recovery persistence.
     *
     * @param store the durable execution store, or null to disable
     * @return this builder
     */
    public AgentBuilder durableExecutionStore(DurableExecutionStore store) {
        this.durableExecutionStore = store;
        return this;
    }

    /**
     * Set the recovery handler for crash recovery.
     *
     * @param handler the recovery handler, or null to disable
     * @return this builder
     */
    public AgentBuilder recoveryHandler(RecoveryHandler handler) {
        this.recoveryHandler = handler;
        return this;
    }

    /**
     * Enable or disable recovery of pending executions on startup.
     *
     * @param enabled true to attempt recovery on startup (default: false)
     * @return this builder
     */
    public AgentBuilder recoveryOnStartup(boolean enabled) {
        this.recoveryOnStartup = enabled;
        return this;
    }

    /**
     * Set custom {@link ResourceConstraint}s for the agent.
     *
     * <p>When set, these constraints replace the default iteration/token/timeout checks. Pass an
     * empty list to explicitly opt out of all resource constraints.
     *
     * @param constraints the resource constraints
     * @return this builder
     */
    public AgentBuilder resourceConstraints(java.util.List<ResourceConstraint> constraints) {
        this.resourceConstraints = constraints != null ? List.copyOf(constraints) : null;
        return this;
    }

    /**
     * Configures the evolution settings for the agent.
     *
     * <p>Example (non-Spring usage):
     *
     * <pre>{@code
     * AgentBuilder builder = AgentBuilder.create()
     *     .name("my-agent")
     *     .evolutionConfig(EvolutionConfig.builder()
     *         .enabled(true)
     *         .evolutionPolicy(myPolicy)
     *         .evolvedSkillStore(myStore)
     *         .build());
     * }</pre>
     *
     * @param config the evolution configuration
     * @return this builder
     */
    public AgentBuilder evolutionConfig(EvolutionConfig config) {
        this.evolutionConfig = config;
        return this;
    }

    /**
     * Registers a customizer that is applied during {@link #build()}. This is the primary extension
     * point for auto-configuration frameworks to wire additional capabilities (e.g., evolution
     * hooks) into the agent.
     *
     * <p>Example:
     *
     * <pre>{@code
     * builder.customizer(b -> ((AgentBuilder) b).hook(evolutionHook));
     * }</pre>
     *
     * @param customizer the customizer to register
     * @return this builder
     */
    public AgentBuilder customizer(AgentBuilderCustomizer customizer) {
        this.customizers.add(customizer);
        return this;
    }

    /**
     * Registers a system prompt contributor that provides dynamic content sections injected into
     * the agent's system prompt.
     *
     * <p>Example:
     *
     * <pre>{@code
     * builder.systemPromptContributor(skillContentInjector);
     * }</pre>
     *
     * @param contributor the system prompt contributor
     * @return this builder
     */
    public AgentBuilder systemPromptContributor(SystemPromptContributor contributor) {
        this.systemPromptContributors.add(contributor);
        return this;
    }

    /**
     * Build the agent, validating required parameters.
     *
     * @return a new {@link Agent} instance
     * @throws NullPointerException if required parameters are missing
     * @throws IllegalArgumentException if parameters are invalid
     */
    public Agent build() {
        // Apply all registered customizers before building config
        customizers.forEach(c -> c.customize(this));

        // Wire compactionThresholds into a DefaultContextManager if user didn't provide one
        if (contextManager == null && compactionThresholds != null) {
            TokenBudgetManager budgetMgr = TokenBudgetManager.forModel(modelName);
            contextManager =
                    new DefaultContextManager(budgetMgr, modelProvider, compactionThresholds);
        }

        AgentConfig config = buildConfig();

        // Wire approval handler into tool executor if configured
        if (approvalHandler != null) {
            toolExecutor.setApprovalHandler(approvalHandler);
        }

        // Create hook chain — hooks will be registered by DefaultReActAgent constructor
        DefaultHookChain hookChain = new DefaultHookChain();

        // Build middleware pipeline
        DefaultMiddlewarePipeline middlewarePipeline = new DefaultMiddlewarePipeline(middlewares);

        // Default shutdown manager if none provided
        GracefulShutdownManager sm =
                shutdownManager != null ? shutdownManager : new GracefulShutdownManager();

        DefaultReActAgent agent =
                new DefaultReActAgent(
                        config,
                        toolExecutor,
                        hookChain,
                        middlewarePipeline,
                        sm,
                        guardrailChain,
                        durableExecutionStore,
                        recoveryHandler,
                        recoveryOnStartup);

        // Restore from snapshot if provided
        if (restoreFrom != null) {
            if (restoreFrom.conversationHistory() != null
                    && !restoreFrom.conversationHistory().isEmpty()) {
                agent.injectMessages(restoreFrom.conversationHistory());
            }
        }

        if (streamingEnabled) {
            agent.setStreamingEnabled(true);
        }
        return agent;
    }

    /**
     * Build a {@link CoordinatorAgent} from this builder's configuration.
     *
     * <p>The coordinator will only retain AGENT_AND_TASK category tools, stripping all file, exec,
     * and search tools.
     *
     * @return a new CoordinatorAgent
     */
    public CoordinatorAgent buildCoordinator() {
        AgentConfig baseConfig = buildConfig();
        CoordinatorConfig coordConfig = CoordinatorConfig.of(baseConfig);
        return new CoordinatorAgent(coordConfig);
    }

    /**
     * Build a {@link CoordinatorAgent} with custom coordinator settings.
     *
     * @param maxWorkers maximum concurrent workers
     * @param requirePlan whether to require plan before dispatch
     * @return a new CoordinatorAgent
     */
    public CoordinatorAgent buildCoordinator(int maxWorkers, boolean requirePlan) {
        AgentConfig baseConfig = buildConfig();
        CoordinatorConfig coordConfig =
                CoordinatorConfig.builder(baseConfig)
                        .maxConcurrentWorkers(maxWorkers)
                        .requirePlanBeforeDispatch(requirePlan)
                        .build();
        return new CoordinatorAgent(coordConfig);
    }

    /**
     * Build the {@link AgentConfig} from the current builder state, validating required parameters.
     *
     * @return a new AgentConfig
     * @throws NullPointerException if required parameters are missing
     * @throws IllegalArgumentException if parameters are invalid
     */
    private AgentConfig buildConfig() {
        Objects.requireNonNull(name, "Agent name must not be null");
        Objects.requireNonNull(modelProvider, "ModelProvider must not be null");

        if (modelName == null || modelName.isBlank()) {
            throw new IllegalStateException(
                    "modelName is required. Call .modelName(\"your-model\") before .build(). "
                            + "Example: AgentBuilder.create().name(\"my-agent\").model(provider).modelName(\"gpt-4o\").build()");
        }

        if (maxIterations <= 0) {
            throw new IllegalArgumentException(
                    "maxIterations must be positive, got: " + maxIterations);
        }
        if (tokenBudget <= 0) {
            throw new IllegalArgumentException("tokenBudget must be positive, got: " + tokenBudget);
        }

        AgentConfig.Builder configBuilder =
                AgentConfig.builder()
                        .name(name)
                        .systemPrompt(systemPrompt)
                        .modelProvider(modelProvider)
                        .toolRegistry(toolRegistry)
                        .maxIterations(maxIterations)
                        .timeout(timeout)
                        .tokenBudget(tokenBudget)
                        .modelName(modelName)
                        .contextManager(contextManager)
                        .memoryStore(memoryStore)
                        .sessionId(sessionId)
                        .tracer(tracer);

        configBuilder.loopDetection(
                loopHashWarn, loopHashStop, loopFreqWarn, loopFreqStop, loopFreqWindow);

        if (resourceConstraints != null) {
            configBuilder.resourceConstraints(resourceConstraints);
        }

        if (evolutionConfig != null) {
            configBuilder.evolutionConfig(evolutionConfig);
        }

        if (!systemPromptContributors.isEmpty()) {
            configBuilder.systemPromptContributors(List.copyOf(systemPromptContributors));
        }

        for (Object hook : hooks) {
            configBuilder.addHook(hook);
        }

        for (Middleware mw : middlewares) {
            configBuilder.addMiddleware(mw);
        }

        if (!mcpServerConfigs.isEmpty()) {
            configBuilder.mcpCapability(
                    new io.kairo.api.agent.McpCapabilityConfig(
                            mcpServerConfigs,
                            io.kairo.api.agent.McpCapabilityConfig.EMPTY.maxToolsPerServer(),
                            io.kairo.api.agent.McpCapabilityConfig.EMPTY.strictSchemaAlignment(),
                            io.kairo.api.agent.McpCapabilityConfig.EMPTY.toolSearchQuery()));
        }

        return configBuilder.build();
    }
}
