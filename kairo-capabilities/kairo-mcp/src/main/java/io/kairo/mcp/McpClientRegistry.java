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
package io.kairo.mcp;

import io.kairo.api.tool.ToolDefinition;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.Closeable;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

/**
 * Central registry managing multiple MCP server connections and their tools.
 *
 * <p>Handles client lifecycle (connect, initialize, list tools, close) and maps MCP tools to Kairo
 * {@link ToolDefinition} and {@link McpToolExecutor} instances.
 *
 * <p>Example:
 *
 * <pre>{@code
 * McpClientRegistry registry = new McpClientRegistry();
 * McpToolGroup group = registry.register(McpServerConfig.stdio("fs", "npx", "-y",
 *     "@modelcontextprotocol/server-filesystem", "/tmp")).block();
 * // group.getRegisteredToolNames() -> ["fs_read_file", "fs_write_file", ...]
 * }</pre>
 */
public class McpClientRegistry implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(McpClientRegistry.class);
    private static final int REGISTER_RETRY_ATTEMPTS = 3;
    private static final Duration REGISTER_RETRY_MIN_BACKOFF = Duration.ofMillis(250);
    private static final Duration REGISTER_RETRY_MAX_BACKOFF = Duration.ofSeconds(2);

    private final ConcurrentHashMap<String, McpAsyncClient> clients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, McpToolGroup> toolGroups = new ConcurrentHashMap<>();

    /**
     * Registers an MCP server from a config, initializes the client, lists tools, and creates a
     * {@link McpToolGroup}.
     *
     * @param config the server configuration
     * @return a Mono emitting the tool group for this server
     */
    public Mono<McpToolGroup> register(McpServerConfig config) {
        if (config.name() == null || config.name().isBlank()) {
            return Mono.error(
                    new IllegalArgumentException(
                            "MCP server config must have a non-blank name; "
                                    + "this name is required for the 'mcp.server' guardrail metadata key"));
        }
        if (clients.containsKey(config.name())) {
            return Mono.error(
                    new IllegalStateException("MCP server already registered: " + config.name()));
        }

        logger.info("Registering MCP server: {}", config.name());
        McpAsyncClient client = McpClientBuilder.fromConfig(config).build();

        return initializeAndListTools(client, config.name())
                .map(
                        listToolsResult -> {
                            List<McpSchema.Tool> mcpTools = listToolsResult.tools();
                            int maxTools = config.maxToolsPerServer();

                            // Enforce tool limit per server
                            if (mcpTools.size() > maxTools) {
                                logger.warn(
                                        "MCP server '{}' exposes {} tools, exceeding the"
                                                + " limit of {}. Only the first {} tools will be"
                                                + " registered. Consider using enableTools/"
                                                + "disableTools to select specific tools.",
                                        config.name(),
                                        mcpTools.size(),
                                        maxTools,
                                        maxTools);
                                mcpTools = mcpTools.subList(0, maxTools);
                            }

                            // TODO: Future enhancement — support "tool search" lazy-load mode
                            //  where tools are not eagerly registered but discovered on-demand
                            //  via a search/query mechanism. This would allow MCP servers with
                            //  very large tool catalogs to be used without hitting the limit.

                            McpToolGroup group = new McpToolGroup(config.name());

                            for (McpSchema.Tool mcpTool : mcpTools) {
                                if (!shouldRegisterTool(
                                        mcpTool.name(),
                                        config.enableTools(),
                                        config.disableTools())) {
                                    logger.debug("Skipping disabled tool: {}", mcpTool.name());
                                    continue;
                                }

                                // Determine preset args for this tool
                                Map<String, Object> toolPresetArgs = null;
                                if (config.presetArgs() != null) {
                                    toolPresetArgs = config.presetArgs().get(mcpTool.name());
                                }

                                Set<String> excludeParams =
                                        toolPresetArgs != null
                                                ? toolPresetArgs.keySet()
                                                : Collections.emptySet();

                                ToolDefinition definition =
                                        McpToolAdapter.toToolDefinition(
                                                mcpTool,
                                                config.name(),
                                                config.requestTimeout(),
                                                excludeParams);

                                McpToolExecutor executor =
                                        new McpToolExecutor(
                                                client,
                                                mcpTool.name(),
                                                definition.name(),
                                                toolPresetArgs,
                                                config.requestTimeout());

                                group.addTool(definition, executor);
                                logger.debug(
                                        "Registered MCP tool: {} -> {}",
                                        mcpTool.name(),
                                        definition.name());
                            }

                            clients.put(config.name(), client);
                            toolGroups.put(config.name(), group);
                            logger.info(
                                    "MCP server '{}' registered with {} tools",
                                    config.name(),
                                    group.size());
                            return group;
                        })
                .doOnError(
                        e -> {
                            logger.error(
                                    "Failed to register MCP server '{}': {}",
                                    config.name(),
                                    e.getMessage());
                            // Best-effort close on failure
                            try {
                                client.closeGracefully().block();
                            } catch (Exception ignored) {
                                // ignore close errors during failed registration
                            }
                        });
    }

    private Mono<McpSchema.ListToolsResult> initializeAndListTools(
            McpAsyncClient client, String serverName) {
        return client.initialize()
                .then(client.listTools())
                .retryWhen(registerRetrySpec(serverName));
    }

    private RetryBackoffSpec registerRetrySpec(String serverName) {
        return Retry.backoff(REGISTER_RETRY_ATTEMPTS, REGISTER_RETRY_MIN_BACKOFF)
                .maxBackoff(REGISTER_RETRY_MAX_BACKOFF)
                .filter(this::isTransientConnectionError)
                .doBeforeRetry(
                        signal ->
                                logger.warn(
                                        "Retrying MCP server '{}' registration (attempt {}): {}",
                                        serverName,
                                        signal.totalRetries() + 1,
                                        signal.failure() == null
                                                ? "unknown"
                                                : signal.failure().getMessage()));
    }

    private boolean isTransientConnectionError(Throwable t) {
        if (t == null) {
            return false;
        }
        if (t instanceof TimeoutException
                || t instanceof ConnectException
                || t instanceof SocketException
                || t instanceof IOException) {
            return true;
        }
        String simpleName = t.getClass().getSimpleName().toLowerCase();
        return simpleName.contains("timeout")
                || simpleName.contains("connect")
                || simpleName.contains("temporar");
    }

    /**
     * Unregisters an MCP server and closes its client.
     *
     * @param name the server name
     * @return a Mono that completes when the client is closed
     */
    public Mono<Void> unregister(String name) {
        McpAsyncClient client = clients.remove(name);
        McpToolGroup group = toolGroups.remove(name);

        if (client == null) {
            logger.warn("MCP server not found: {}", name);
            return Mono.empty();
        }

        logger.info(
                "Unregistering MCP server '{}' ({} tools)", name, group != null ? group.size() : 0);

        return client.closeGracefully()
                .doOnSuccess(v -> logger.info("MCP server '{}' closed", name))
                .onErrorResume(
                        e -> {
                            logger.warn("Error closing MCP server '{}': {}", name, e.getMessage());
                            return Mono.empty();
                        });
    }

    /** Returns the tool group for the given server name, or null if not registered. */
    public McpToolGroup getToolGroup(String serverName) {
        return toolGroups.get(serverName);
    }

    /** Returns the executor for a given Kairo tool name, searching all groups. */
    public McpToolExecutor getExecutor(String kairoToolName) {
        for (McpToolGroup group : toolGroups.values()) {
            McpToolExecutor executor = group.getExecutor(kairoToolName);
            if (executor != null) {
                return executor;
            }
        }
        return null;
    }

    /** Returns all registered server names. */
    public Set<String> getServerNames() {
        return Collections.unmodifiableSet(clients.keySet());
    }

    /** Returns all tool definitions across all registered servers. */
    public Flux<ToolDefinition> getAllToolDefinitions() {
        return Flux.fromIterable(toolGroups.values())
                .flatMapIterable(McpToolGroup::getAllToolDefinitions);
    }

    /** Closes all registered MCP clients. */
    @Override
    public void close() {
        logger.info("Closing McpClientRegistry ({} servers)", clients.size());
        for (Map.Entry<String, McpAsyncClient> entry : clients.entrySet()) {
            try {
                entry.getValue().closeGracefully().block();
                logger.debug("Closed MCP client: {}", entry.getKey());
            } catch (Exception e) {
                logger.warn("Error closing MCP client '{}': {}", entry.getKey(), e.getMessage());
            }
        }
        clients.clear();
        toolGroups.clear();
    }

    private boolean shouldRegisterTool(
            String toolName, List<String> enableTools, List<String> disableTools) {
        boolean result = true;
        if (disableTools != null && !disableTools.isEmpty()) {
            result = !disableTools.contains(toolName);
        }
        if (enableTools != null && !enableTools.isEmpty()) {
            result = enableTools.contains(toolName);
        }
        return result;
    }
}
