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
import io.kairo.api.mcp.McpPlugin;
import io.kairo.api.mcp.McpPluginRegistration;
import io.kairo.api.mcp.McpPluginTool;
import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.api.tool.ToolHandler;
import io.kairo.api.tool.ToolResult;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
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
    private AutoCloseable mcpRegistryPlugin;

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
                            McpPlugin plugin =
                                    ServiceLoader.load(McpPlugin.class).findFirst().orElse(null);
                            if (plugin == null) {
                                log.warn(
                                        "No McpPlugin found via ServiceLoader; skipping MCP initialization");
                                return false;
                            }
                            this.mcpRegistryPlugin = plugin;

                            for (Object serverConfig : config.mcpServerConfigs()) {
                                if (!plugin.supports(serverConfig)) {
                                    log.warn(
                                            "MCP config type '{}' is not supported by plugin '{}'; skipping",
                                            serverConfig == null
                                                    ? "null"
                                                    : serverConfig.getClass().getName(),
                                            plugin.getClass().getName());
                                    continue;
                                }
                                McpPluginRegistration registration =
                                        plugin.register(serverConfig).block();
                                if (registration == null || registration.tools() == null) {
                                    continue;
                                }

                                List<McpPluginTool> governed =
                                        applyMcpGovernance(
                                                registration.serverName(), registration.tools());
                                for (McpPluginTool tool : governed) {
                                    ToolDefinition def = tool.definition();
                                    Object executor = tool.executor();
                                    if (executor == null) {
                                        log.warn(
                                                "Skipping MCP tool '{}' because executor is null",
                                                def.name());
                                        continue;
                                    }
                                    // Register definition into ToolRegistry
                                    if (config.toolRegistry() != null) {
                                        config.toolRegistry().register(def);
                                    }
                                    // Register executor instance into ToolExecutor's registry.
                                    // McpToolExecutor lives in kairo-mcp and does not implement
                                    // ToolHandler (kairo-core type), so wrap it in a reflective
                                    // adapter to satisfy DefaultToolExecutor's handler contract.
                                    ToolHandler adapter = adaptMcpExecutor(executor);
                                    toolExecutor.registerToolInstance(def.name(), adapter);
                                    // Set MCP origin metadata for guardrail policy evaluation
                                    toolExecutor.setToolMetadata(
                                            def.name(),
                                            Map.of("mcp.server", registration.serverName()));
                                }
                                log.info(
                                        "MCP server '{}' registered {} tool(s) into agent",
                                        registration.serverName(),
                                        governed.size());
                            }
                            return true;
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(
                        e -> {
                            log.error("Failed to initialize MCP servers: {}", e.getMessage(), e);
                            return Mono.empty();
                        })
                .then();
    }

    /** Close the MCP registry if it was initialized. */
    void closeMcpRegistry() {
        if (mcpRegistryPlugin != null) {
            try {
                mcpRegistryPlugin.close();
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
        if (mcpExecutor instanceof ToolHandler toolHandler) {
            return toolHandler;
        }
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

    private List<McpPluginTool> applyMcpGovernance(
            String serverName, List<McpPluginTool> discoveredTools) {
        List<McpPluginTool> filtered = filterToolsBySearchQuery(serverName, discoveredTools);
        int maxTools = Math.max(1, config.mcpMaxToolsPerServer());
        List<McpPluginTool> bounded = filtered;
        if (filtered.size() > maxTools) {
            log.warn(
                    "MCP server '{}' discovered {} tools, capping registration at {}",
                    serverName,
                    filtered.size(),
                    maxTools);
            bounded = filtered.subList(0, maxTools);
        }

        List<McpPluginTool> normalized = new ArrayList<>();
        for (McpPluginTool tool : bounded) {
            ToolDefinition normalizedDef =
                    normalizeToolDefinition(tool.definition(), config.mcpStrictSchemaAlignment());
            normalized.add(new McpPluginTool(normalizedDef, tool.executor()));
        }
        return normalized;
    }

    private List<McpPluginTool> filterToolsBySearchQuery(
            String serverName, List<McpPluginTool> discoveredTools) {
        String query = config.mcpToolSearchQuery();
        if (query == null || query.isBlank()) {
            return discoveredTools;
        }
        String needle = query.toLowerCase();
        List<McpPluginTool> filtered =
                discoveredTools.stream().filter(tool -> matchesToolQuery(tool, needle)).toList();
        log.info(
                "MCP server '{}' tool search query '{}' matched {}/{} tool(s)",
                serverName,
                query,
                filtered.size(),
                discoveredTools.size());
        return filtered;
    }

    private boolean matchesToolQuery(McpPluginTool tool, String needle) {
        ToolDefinition def = tool.definition();
        if (def == null) {
            return false;
        }
        String name = def.name() == null ? "" : def.name().toLowerCase();
        String desc = def.description() == null ? "" : def.description().toLowerCase();
        return name.contains(needle) || desc.contains(needle);
    }

    private ToolDefinition normalizeToolDefinition(ToolDefinition def, boolean strictSchema) {
        JsonSchema schema = def.inputSchema();
        if (schema == null) {
            schema = new JsonSchema("object", Map.of(), List.of(), null);
        }

        String type = schema.type() == null || schema.type().isBlank() ? "object" : schema.type();
        Map<String, JsonSchema> properties =
                schema.properties() == null ? Map.of() : new LinkedHashMap<>(schema.properties());
        List<String> required =
                schema.required() == null ? new ArrayList<>() : new ArrayList<>(schema.required());

        if (strictSchema) {
            required.removeIf(
                    key -> {
                        boolean missing = !properties.containsKey(key);
                        if (missing) {
                            log.warn(
                                    "MCP tool '{}' schema required key '{}' missing in properties; dropping key",
                                    def.name(),
                                    key);
                        }
                        return missing;
                    });
            if (!"object".equals(type)) {
                log.warn(
                        "MCP tool '{}' schema type '{}' coerced to 'object' for runtime alignment",
                        def.name(),
                        type);
                type = "object";
            }
        }

        JsonSchema normalizedSchema =
                new JsonSchema(
                        type, Map.copyOf(properties), List.copyOf(required), schema.description());
        return new ToolDefinition(
                def.name(),
                def.description(),
                def.category(),
                normalizedSchema,
                def.implementationClass(),
                def.timeout(),
                def.sideEffect(),
                def.usageGuidance());
    }
}
