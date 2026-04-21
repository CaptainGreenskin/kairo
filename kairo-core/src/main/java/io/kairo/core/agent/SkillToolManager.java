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

import io.kairo.api.agent.AgentConfig;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.tool.ToolHandler;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Manages MCP (Model Context Protocol) server lifecycle: lazy initialization, tool registration,
 * and cleanup. Also handles skill_load tool restrictions on the {@link ToolExecutor}.
 *
 * <p>Package-private: not part of the public API.
 */
class SkillToolManager {

    private static final Logger log = LoggerFactory.getLogger(SkillToolManager.class);

    private final AgentConfig config;
    private final ToolExecutor toolExecutor;
    private volatile boolean mcpInitialized = false;
    private AutoCloseable
            mcpRegistry; // McpClientRegistry, held as AutoCloseable to avoid compile dep

    SkillToolManager(AgentConfig config, ToolExecutor toolExecutor) {
        this.config = config;
        this.toolExecutor = toolExecutor;
    }

    /**
     * Lazily initialize MCP servers if configured. Connects to each MCP server, discovers tools,
     * and registers them into the agent's ToolRegistry and ToolExecutor.
     */
    Mono<Void> initMcpIfConfigured() {
        if (mcpInitialized
                || config.mcpServerConfigs() == null
                || config.mcpServerConfigs().isEmpty()) {
            return Mono.empty();
        }
        return Mono.fromCallable(
                        () -> {
                            mcpInitialized = true;
                            // Runtime check for kairo-mcp on classpath
                            Class<?> registryClass =
                                    Class.forName("io.kairo.mcp.McpClientRegistry");
                            Object registry = registryClass.getDeclaredConstructor().newInstance();
                            this.mcpRegistry = (AutoCloseable) registry;

                            // Get the register(McpServerConfig) method
                            Class<?> configClass = Class.forName("io.kairo.mcp.McpServerConfig");
                            var registerMethod = registryClass.getMethod("register", configClass);

                            for (Object serverConfig : config.mcpServerConfigs()) {
                                // register() returns Mono<McpToolGroup> — block to get group
                                @SuppressWarnings("unchecked")
                                Mono<Object> groupMono =
                                        (Mono<Object>)
                                                registerMethod.invoke(registry, serverConfig);
                                Object toolGroup = groupMono.block();

                                // Get tool definitions and executors from the group
                                var getAllDefs =
                                        toolGroup.getClass().getMethod("getAllToolDefinitions");
                                @SuppressWarnings("unchecked")
                                List<io.kairo.api.tool.ToolDefinition> defs =
                                        (List<io.kairo.api.tool.ToolDefinition>)
                                                getAllDefs.invoke(toolGroup);

                                var getExecutor =
                                        toolGroup.getClass().getMethod("getExecutor", String.class);

                                for (var def : defs) {
                                    // Register definition into ToolRegistry
                                    if (config.toolRegistry() != null) {
                                        config.toolRegistry().register(def);
                                    }
                                    // Register executor instance into ToolExecutor's registry.
                                    // McpToolExecutor lives in kairo-mcp and does not implement
                                    // ToolHandler (kairo-core type), so wrap it in a reflective
                                    // adapter to satisfy DefaultToolExecutor's handler contract.
                                    Object executor = getExecutor.invoke(toolGroup, def.name());
                                    ToolHandler adapter = adaptMcpExecutor(executor);
                                    toolExecutor.registerToolInstance(def.name(), adapter);
                                }
                                log.info(
                                        "MCP server registered {} tool(s) into agent", defs.size());
                            }
                            return true;
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(
                        ClassNotFoundException.class,
                        e -> {
                            log.warn(
                                    "kairo-mcp not on classpath, skipping MCP initialization: {}",
                                    e.getMessage());
                            return Mono.empty();
                        })
                .onErrorResume(
                        e -> {
                            log.error("Failed to initialize MCP servers: {}", e.getMessage(), e);
                            return Mono.empty();
                        })
                .then();
    }

    /** Close the MCP registry if it was initialized. */
    void closeMcpRegistry() {
        if (mcpRegistry != null) {
            try {
                mcpRegistry.close();
                log.debug("MCP registry closed");
            } catch (Exception e) {
                log.warn("Error closing MCP registry: {}", e.getMessage());
            }
        }
    }

    /** Clear skill tool constraints on the executor (called on agent completion). */
    void clearSkillRestrictions() {
        toolExecutor.clearAllowedTools();
    }

    /**
     * Wrap an {@code McpToolExecutor} instance (loaded reflectively to avoid a compile-time
     * dependency on kairo-mcp) into a {@link ToolHandler} so that {@code DefaultToolExecutor} can
     * dispatch tool calls through its standard handler contract.
     *
     * <p>Resolves the target's {@code executeSync(Map)} method once per registration so
     * per-invocation cost stays at a single reflective call.
     */
    private static ToolHandler adaptMcpExecutor(Object mcpExecutor) {
        final Method executeSync;
        try {
            executeSync = mcpExecutor.getClass().getMethod("executeSync", Map.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    "MCP executor "
                            + mcpExecutor.getClass().getName()
                            + " is missing executeSync(Map); cannot adapt to ToolHandler",
                    e);
        }
        return input -> {
            try {
                Object result = executeSync.invoke(mcpExecutor, input);
                if (result instanceof ToolResult tr) {
                    return tr;
                }
                throw new IllegalStateException(
                        "MCP executor.executeSync returned unexpected type: "
                                + (result == null ? "null" : result.getClass().getName()));
            } catch (ReflectiveOperationException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                if (cause instanceof Exception ex) {
                    throw ex;
                }
                throw new RuntimeException(cause);
            }
        };
    }
}
