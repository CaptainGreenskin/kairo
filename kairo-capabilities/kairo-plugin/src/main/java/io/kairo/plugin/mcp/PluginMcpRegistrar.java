/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin.mcp;

import io.kairo.api.mcp.McpPlugin;
import io.kairo.api.mcp.McpPluginRegistration;
import io.kairo.api.plugin.PluginComponent;
import io.kairo.mcp.McpServerConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Bridges {@link PluginComponent.McpComponent} declarations onto the kairo-mcp {@link McpPlugin}
 * SPI. Each declaration is converted into a {@link McpServerConfig} (stdio transport) and handed to
 * the supplied {@link McpPlugin} for actual subprocess startup; kairo-mcp's existing transport code
 * (built on the official MCP Java SDK) handles JSON-RPC, lifecycle, and tool discovery.
 *
 * <p>This registrar does the orchestration that is plugin-aware:
 *
 * <ul>
 *   <li>Tracks per-plugin server registrations so a {@code disable()} call can release exactly the
 *       resources belonging to one plugin without affecting others.
 *   <li>Counts consecutive failures per server. After {@link #MAX_CONSECUTIVE_FAILURES} retries
 *       without success, the server is marked broken and further attempts are short-circuited until
 *       the plugin is re-enabled — protecting the agent from crash-loop subprocesses.
 * </ul>
 *
 * <p>Per-server unregister is not yet exposed by kairo-mcp's {@code McpPlugin}; until that lands,
 * {@link #disablePlugin(String)} simply forgets the local references and lets the next process-wide
 * {@link McpPlugin#close()} (or fresh {@code register} for the same name) reclaim resources. This
 * is the same trade-off Claude Code's plugin runtime makes — sufficient for v1.2.
 */
public final class PluginMcpRegistrar {

    /** A server is considered broken after this many consecutive register failures. */
    public static final int MAX_CONSECUTIVE_FAILURES = 3;

    private static final Logger log = LoggerFactory.getLogger(PluginMcpRegistrar.class);

    private final McpPlugin mcpPlugin;
    private final ConcurrentHashMap<String, List<ServerHandle>> byPluginId =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> failureCounts =
            new ConcurrentHashMap<>();

    public PluginMcpRegistrar(McpPlugin mcpPlugin) {
        this.mcpPlugin = mcpPlugin;
    }

    /**
     * Registers all MCP component declarations from one plugin. Each declaration is passed
     * sequentially to the underlying {@link McpPlugin}; a single failure aborts the chain so the
     * caller (typically {@code PluginManager.enable}) can roll back any siblings already
     * registered.
     */
    public Mono<Void> registerAll(String pluginId, List<PluginComponent.McpComponent> components) {
        if (components == null || components.isEmpty()) return Mono.empty();
        return Mono.fromCallable(() -> components)
                .flatMapMany(reactor.core.publisher.Flux::fromIterable)
                .concatMap(c -> registerOne(pluginId, c))
                .then();
    }

    /** Registers a single component. Public so callers can probe / retry one server. */
    public Mono<McpPluginRegistration> registerOne(
            String pluginId, PluginComponent.McpComponent component) {
        AtomicInteger failures =
                failureCounts.computeIfAbsent(component.serverName(), n -> new AtomicInteger(0));
        if (failures.get() >= MAX_CONSECUTIVE_FAILURES) {
            return Mono.error(
                    new IllegalStateException(
                            "MCP server '"
                                    + component.serverName()
                                    + "' marked broken after "
                                    + MAX_CONSECUTIVE_FAILURES
                                    + " consecutive failures"));
        }
        McpServerConfig config = toServerConfig(component);
        return mcpPlugin
                .register(config)
                .doOnSuccess(reg -> recordSuccess(pluginId, component, reg, failures))
                .doOnError(err -> recordFailure(component, failures, err));
    }

    /**
     * Releases the local handle for every server registered against this plugin. Subprocess
     * teardown is the {@link McpPlugin} implementation's responsibility — this method only forgets
     * local bookkeeping.
     */
    public List<ServerHandle> disablePlugin(String pluginId) {
        List<ServerHandle> removed = byPluginId.remove(pluginId);
        if (removed == null) return List.of();
        log.info(
                "Released {} MCP server registration(s) for plugin '{}'", removed.size(), pluginId);
        for (ServerHandle h : removed) {
            failureCounts.remove(h.serverName());
        }
        return removed;
    }

    /** Snapshot view, mostly for diagnostics / tests. */
    public Map<String, List<ServerHandle>> snapshot() {
        return Map.copyOf(byPluginId);
    }

    /** How many times this server has consecutively failed since the last success / disable. */
    public int consecutiveFailures(String serverName) {
        AtomicInteger ai = failureCounts.get(serverName);
        return ai == null ? 0 : ai.get();
    }

    /**
     * Translates a plugin's {@link PluginComponent.McpComponent} into a kairo-mcp config, routing
     * by the component's {@link PluginComponent.McpComponent.Transport}.
     */
    static McpServerConfig toServerConfig(PluginComponent.McpComponent comp) {
        switch (comp.transport()) {
            case STREAMABLE_HTTP:
                return McpServerConfig.builder()
                        .name(comp.serverName())
                        .transportType(McpServerConfig.TransportType.STREAMABLE_HTTP)
                        .url(comp.url())
                        .headers(comp.headers())
                        .build();
            case SSE:
                return McpServerConfig.builder()
                        .name(comp.serverName())
                        .transportType(McpServerConfig.TransportType.SSE)
                        .url(comp.url())
                        .headers(comp.headers())
                        .build();
            case STDIO:
            default:
                List<String> argv = new ArrayList<>(comp.args().size() + 1);
                argv.add(comp.command());
                argv.addAll(comp.args());
                return McpServerConfig.builder()
                        .name(comp.serverName())
                        .transportType(McpServerConfig.TransportType.STDIO)
                        .command(argv)
                        .env(comp.env())
                        .build();
        }
    }

    private void recordSuccess(
            String pluginId,
            PluginComponent.McpComponent component,
            McpPluginRegistration reg,
            AtomicInteger failures) {
        failures.set(0);
        ServerHandle handle = new ServerHandle(component.serverName(), reg);
        byPluginId.compute(
                pluginId,
                (id, existing) -> {
                    List<ServerHandle> list =
                            existing == null ? new ArrayList<>() : new ArrayList<>(existing);
                    list.add(handle);
                    return List.copyOf(list);
                });
        log.info(
                "Registered MCP server '{}' for plugin '{}' ({} tool(s))",
                component.serverName(),
                pluginId,
                reg.tools().size());
    }

    private void recordFailure(
            PluginComponent.McpComponent component, AtomicInteger failures, Throwable err) {
        int n = failures.incrementAndGet();
        if (n >= MAX_CONSECUTIVE_FAILURES) {
            log.warn(
                    "MCP server '{}' failed {} times in a row; marking broken until plugin is"
                            + " disabled",
                    component.serverName(),
                    n,
                    err);
        } else {
            log.warn(
                    "MCP server '{}' register attempt {} failed: {}",
                    component.serverName(),
                    n,
                    err.getMessage());
        }
    }

    /** Lightweight handle remembered per-plugin so disablePlugin can find what to clean up. */
    public record ServerHandle(String serverName, McpPluginRegistration registration) {}
}
