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
import io.kairo.api.cost.CostTracker;
import io.kairo.api.event.KairoEventBus;
import io.kairo.api.evolution.EvolutionConfig;
import io.kairo.api.execution.DurableExecutionStore;
import io.kairo.api.execution.ResourceConstraint;
import io.kairo.api.guardrail.GuardrailChain;
import io.kairo.api.memory.MemoryStore;
import io.kairo.api.model.ModelCatalog;
import io.kairo.api.model.ModelInfo;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ProviderRegistry;
import io.kairo.api.model.ProviderSpec;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.api.tool.ToolRegistry;
import io.kairo.api.tool.UserApprovalHandler;
import io.kairo.api.tracing.Tracer;
import io.kairo.core.agent.continuation.AgentContinuationStrategy;
import io.kairo.core.agent.continuation.CompositeContinuationStrategy;
import io.kairo.core.agent.continuation.FinishReasonRecoveryStrategy;
import io.kairo.core.agent.continuation.PendingTodoNudgeStrategy;
import io.kairo.core.agent.continuation.RecentToolActivityStrategy;
import io.kairo.core.context.CompactionThresholds;
import io.kairo.core.context.DefaultContextManager;
import io.kairo.core.context.TokenBudgetManager;
import io.kairo.core.execution.RecoveryHandler;
import io.kairo.core.hook.DefaultHookChain;
import io.kairo.core.model.DefaultModelCatalog;
import io.kairo.core.model.DefaultProviderRegistry;
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
    private ContextManager contextManager;
    private MemoryStore memoryStore;
    private String sessionId;
    private io.kairo.api.session.SessionStorageProvider sessionStorageProvider;
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
    @javax.annotation.Nullable private java.util.function.Consumer<String> textDeltaConsumer;
    @javax.annotation.Nullable private KairoEventBus eventBus;
    @javax.annotation.Nullable private AgentContinuationStrategy continuationStrategy;
    @javax.annotation.Nullable private CostTracker costTracker;
    @javax.annotation.Nullable private String apiKey;
    @javax.annotation.Nullable private String baseUrl;
    @javax.annotation.Nullable private ModelCatalog modelCatalog;
    @javax.annotation.Nullable private ProviderRegistry providerRegistry;
    @javax.annotation.Nullable private io.kairo.api.workspace.Workspace workspace;

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

    /**
     * Set the workspace for tool execution context.
     *
     * <p>When set, tools receive this workspace via {@link
     * io.kairo.api.tool.ToolContext#workspace()} instead of defaulting to the JVM's current working
     * directory.
     *
     * @param workspace the workspace, or null to use JVM cwd
     * @return this builder
     */
    public AgentBuilder workspace(io.kairo.api.workspace.Workspace workspace) {
        this.workspace = workspace;
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
     * Set a session storage provider for per-session checkpoint isolation. When set, the agent
     * automatically creates a session-scoped {@link io.kairo.api.agent.IterationCheckpointStore}
     * from this provider using the configured {@link #sessionId(String)}.
     */
    public AgentBuilder sessionStorage(io.kairo.api.session.SessionStorageProvider provider) {
        this.sessionStorageProvider = provider;
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
     * Set the cost tracker for cumulative token usage and cost estimation.
     *
     * <p>When not set, defaults to {@link io.kairo.api.cost.NoopCostTracker#INSTANCE}.
     *
     * @param costTracker the cost tracker implementation
     * @return this builder
     */
    public AgentBuilder costTracker(CostTracker costTracker) {
        this.costTracker = costTracker;
        return this;
    }

    /**
     * Set the API key for automatic provider resolution.
     *
     * <p>When {@link #model(ModelProvider)} is not called, the builder uses this key together with
     * {@link #modelName(String)} to auto-construct a {@link ModelProvider} via {@link ModelCatalog}
     * and {@link ProviderRegistry}.
     *
     * @param apiKey the API key
     * @return this builder
     */
    public AgentBuilder apiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    /**
     * Override the base URL for automatic provider resolution.
     *
     * @param baseUrl the base URL (e.g., "https://api.openai.com")
     * @return this builder
     */
    public AgentBuilder baseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    /**
     * Set a custom {@link ModelCatalog} for model name resolution.
     *
     * <p>When not set, {@link DefaultModelCatalog#withBuiltIns()} is used during auto-resolution.
     *
     * @param catalog the model catalog
     * @return this builder
     */
    public AgentBuilder modelCatalog(ModelCatalog catalog) {
        this.modelCatalog = catalog;
        return this;
    }

    /**
     * Set a custom {@link ProviderRegistry} for provider construction.
     *
     * <p>When not set, {@link DefaultProviderRegistry#withBuiltIns()} is used during
     * auto-resolution.
     *
     * @param registry the provider registry
     * @return this builder
     */
    public AgentBuilder providerRegistry(ProviderRegistry registry) {
        this.providerRegistry = registry;
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
     * Register a consumer that receives each text token as it arrives from the model during
     * streaming. Only fires when {@link #streaming(boolean)} is enabled and the provider supports
     * raw streaming. No-op when streaming is disabled or the provider falls back.
     *
     * @param consumer receives each text delta string; called on a Reactor scheduler thread
     * @return this builder
     */
    public AgentBuilder textDeltaConsumer(java.util.function.Consumer<String> consumer) {
        this.textDeltaConsumer = consumer;
        return this;
    }

    /**
     * Set the event bus for publishing structured agent progress events.
     *
     * <p>When configured, the agent publishes {@link io.kairo.api.event.AgentProgressEvent} at key
     * ReAct loop milestones (ITERATION_START, ITERATION_END, AGENT_DONE).
     *
     * @param eventBus the event bus, or null to disable progress events
     * @return this builder
     */
    public AgentBuilder eventBus(KairoEventBus eventBus) {
        this.eventBus = eventBus;
        return this;
    }

    /**
     * Set the continuation strategy that decides whether the ReAct loop should continue when the
     * model emits a response with zero tool calls.
     *
     * <p>When not set (null), the loop terminates immediately on a text-only response — preserving
     * pre-0.5.0 behavior.
     *
     * @param strategy the continuation strategy, or null to disable
     * @return this builder
     */
    public AgentBuilder continuationStrategy(AgentContinuationStrategy strategy) {
        this.continuationStrategy = Objects.requireNonNull(strategy, "continuationStrategy");
        return this;
    }

    /**
     * Convenience: registers the recommended Composite continuation strategy with sensible
     * defaults. Equivalent to {@code
     * continuationStrategy(CompositeContinuationStrategy.withDefaults())}.
     *
     * @return this builder
     */
    public AgentBuilder withSmartContinuation() {
        return continuationStrategy(CompositeContinuationStrategy.withDefaults());
    }

    /**
     * Enable smart continuation with custom parameters.
     *
     * <p>Nudge strategies are signal-based and have no per-session budget; only the length-recovery
     * strategy retains a retry cap since MAX_TOKENS is a real provider failure mode.
     *
     * @param maxLengthRetries maximum length-recovery retries for {@link
     *     FinishReasonRecoveryStrategy}
     * @param lookbackWindow number of recent iterations to check for tool activity
     * @return this builder
     */
    public AgentBuilder withSmartContinuation(int maxLengthRetries, int lookbackWindow) {
        return continuationStrategy(
                new CompositeContinuationStrategy(
                        List.of(
                                new FinishReasonRecoveryStrategy(maxLengthRetries),
                                new PendingTodoNudgeStrategy(),
                                new RecentToolActivityStrategy(lookbackWindow))));
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
        // Default shutdown manager if none provided
        GracefulShutdownManager sm =
                shutdownManager != null ? shutdownManager : new GracefulShutdownManager();

        DefaultReActAgent agent =
                new DefaultReActAgent(
                        config,
                        toolExecutor,
                        hookChain,
                        sm,
                        guardrailChain,
                        durableExecutionStore,
                        recoveryHandler,
                        recoveryOnStartup,
                        continuationStrategy,
                        costTracker);

        // Wire ToolContext with workspace and dependencies so tools resolve paths correctly
        if (workspace != null || !toolDependencies.isEmpty()) {
            agent.setToolContext(
                    new io.kairo.api.tool.ToolContext(
                            agent.id(),
                            null,
                            io.kairo.api.tool.OutputBudgetConfig.DEFAULT,
                            workspace,
                            io.kairo.api.tenant.TenantContext.SINGLE,
                            java.util.Optional.empty(),
                            toolDependencies));
        }

        // Wire session-scoped checkpoint manager from SessionStorageProvider
        if (sessionStorageProvider != null && sessionId != null) {
            sessionStorageProvider.ensureSession(sessionId);
            var store = sessionStorageProvider.checkpointStore(sessionId);
            agent.setCheckpointManager(
                    new io.kairo.core.agent.checkpoint.IterationCheckpointManager(store));
        }

        // Wire event bus for progress events
        if (eventBus != null) {
            agent.withEventBus(eventBus);
        }

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
        if (textDeltaConsumer != null) {
            agent.setTextDeltaConsumer(textDeltaConsumer);
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

        // Auto-resolve: if modelProvider is not set but modelName + apiKey are, construct one
        if (modelProvider == null && modelName != null && !modelName.isBlank() && apiKey != null) {
            ModelCatalog catalog =
                    this.modelCatalog != null
                            ? this.modelCatalog
                            : DefaultModelCatalog.withBuiltIns();
            java.util.Optional<ModelInfo> info = catalog.resolve(modelName);
            if (info.isPresent()) {
                ProviderRegistry pr =
                        this.providerRegistry != null
                                ? this.providerRegistry
                                : DefaultProviderRegistry.withBuiltIns();
                ProviderSpec spec = ProviderSpec.of(apiKey, baseUrl).withModel(modelName);
                modelProvider = pr.create(info.get().providerName(), spec);
            }
        }

        if (modelProvider == null) {
            throw new IllegalStateException(
                    "ModelProvider is required. Either call .model(provider) or set "
                            + ".modelName(\"model\").apiKey(\"key\") for auto-resolution.");
        }

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

        // Wrap the user-supplied provider so every model call emits a reasoning span with
        // the full langfuse.observation.* + gen_ai.usage.* attribute set. Noop tracer is a no-op
        // wrap (returns the delegate unchanged).
        ModelProvider tracedModelProvider =
                io.kairo.core.tracing.TracingModelProvider.wrap(modelProvider, tracer);

        AgentConfig.Builder configBuilder =
                AgentConfig.builder()
                        .name(name)
                        .systemPrompt(systemPrompt)
                        .modelProvider(tracedModelProvider)
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
