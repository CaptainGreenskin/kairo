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
import io.kairo.api.agent.AgentConfig;
import io.kairo.api.context.ContextManager;
import io.kairo.api.memory.MemoryStore;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.api.tool.ToolRegistry;
import io.kairo.api.tool.UserApprovalHandler;
import io.kairo.api.tracing.Tracer;
import io.kairo.core.hook.DefaultHookChain;
import io.kairo.core.session.SessionManager;
import io.kairo.core.shutdown.GracefulShutdownManager;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private SessionManager sessionManager;
    private UserApprovalHandler approvalHandler;
    private Tracer tracer;
    private GracefulShutdownManager shutdownManager;
    private final List<Object> mcpServerConfigs = new ArrayList<>();

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

    /** Set the model name (e.g. "glm-4-plus", "gpt-4o"). */
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
     * Build the agent, validating required parameters.
     *
     * @return a new {@link Agent} instance
     * @throws NullPointerException if required parameters are missing
     * @throws IllegalArgumentException if parameters are invalid
     */
    public Agent build() {
        AgentConfig config = buildConfig();

        // Wire approval handler into tool executor if configured
        if (approvalHandler != null) {
            toolExecutor.setApprovalHandler(approvalHandler);
        }

        // Create hook chain — hooks will be registered by DefaultReActAgent constructor
        DefaultHookChain hookChain = new DefaultHookChain();

        // Default shutdown manager if none provided
        GracefulShutdownManager sm =
                shutdownManager != null ? shutdownManager : new GracefulShutdownManager();

        return new DefaultReActAgent(config, toolExecutor, hookChain, sm);
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

        for (Object hook : hooks) {
            configBuilder.addHook(hook);
        }

        for (Object mcpConfig : mcpServerConfigs) {
            configBuilder.addMcpServerConfig(mcpConfig);
        }

        return configBuilder.build();
    }
}
