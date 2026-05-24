/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin;

import io.kairo.api.agent.SubagentDefinition;
import io.kairo.api.agent.SubagentRegistry;
import io.kairo.api.plugin.PluginComponent;
import io.kairo.api.skill.SkillRegistry;
import io.kairo.plugin.component.SubagentMarkdownParser;
import io.kairo.plugin.mcp.PluginMcpRegistrar;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Default {@link ComponentRegistrar} that wires plugin components onto the host's existing Kairo
 * registries:
 *
 * <ul>
 *   <li>{@link PluginComponent.SkillComponent} and {@link PluginComponent.CommandComponent} →
 *       {@link SkillRegistry#loadFromFile(java.nio.file.Path)} (commands are flat skills)
 *   <li>{@link PluginComponent.McpComponent} → {@link PluginMcpRegistrar}
 *   <li>{@link PluginComponent.BinComponent} → {@link PluginEnvironment#addBinDir}
 *   <li>{@link PluginComponent.AgentComponent} / {@link PluginComponent.HookComponent} / {@link
 *       PluginComponent.OutputStyleComponent} / {@link PluginComponent.ThemeComponent} — captured
 *       but not yet bound; tracked for rollback parity. Subagent + Hook bindings will be wired once
 *       kairo-api exposes per-plugin unregister hooks (Phase B.7 follow-up).
 * </ul>
 *
 * <p>Atomicity: registrations are pushed onto a per-plugin LIFO deque as undo callbacks. On any
 * failure, the deque is drained in reverse order before the error propagates.
 */
public final class KairoComponentRegistrar implements ComponentRegistrar {

    private static final Logger log = LoggerFactory.getLogger(KairoComponentRegistrar.class);

    private final SkillRegistry skillRegistry;
    private final PluginMcpRegistrar mcpRegistrar;
    private final PluginEnvironment environment;
    private final SubagentRegistry subagentRegistry;
    private final SubagentMarkdownParser subagentParser = new SubagentMarkdownParser();

    private final ConcurrentHashMap<String, Deque<Runnable>> undoByPlugin =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> countByPlugin = new ConcurrentHashMap<>();

    /**
     * 3-arg convenience constructor: no SubagentRegistry binding (agent components are captured but
     * not registered). Provided for backwards compatibility with existing wiring.
     */
    public KairoComponentRegistrar(
            SkillRegistry skillRegistry,
            PluginMcpRegistrar mcpRegistrar,
            PluginEnvironment environment) {
        this(skillRegistry, mcpRegistrar, environment, null);
    }

    /**
     * @param skillRegistry kairo skill registry; may be null to skip skill/command binding
     * @param mcpRegistrar MCP bridge; may be null to skip MCP binding
     * @param environment plugin PATH aggregator; may be null to skip bin/ binding
     * @param subagentRegistry subagent catalog; may be null to skip agent binding
     */
    public KairoComponentRegistrar(
            SkillRegistry skillRegistry,
            PluginMcpRegistrar mcpRegistrar,
            PluginEnvironment environment,
            SubagentRegistry subagentRegistry) {
        this.skillRegistry = skillRegistry;
        this.mcpRegistrar = mcpRegistrar;
        this.environment = environment;
        this.subagentRegistry = subagentRegistry;
    }

    @Override
    public Mono<Void> registerAll(String pluginId, List<PluginComponent> components) {
        if (components == null || components.isEmpty()) return Mono.empty();
        Deque<Runnable> undos = undoByPlugin.computeIfAbsent(pluginId, k -> new LinkedList<>());

        return Flux.fromIterable(components)
                .concatMap(c -> registerOne(pluginId, c, undos))
                .then()
                .doOnSuccess(v -> countByPlugin.put(pluginId, components.size()))
                .onErrorResume(
                        err -> {
                            log.warn(
                                    "Plugin '{}' enable failed; rolling back {} component(s): {}",
                                    pluginId,
                                    undos.size(),
                                    err.getMessage());
                            drainUndo(undos);
                            teardownAggregateState(pluginId);
                            undoByPlugin.remove(pluginId);
                            countByPlugin.remove(pluginId);
                            return Mono.error(err);
                        });
    }

    @Override
    public Mono<Void> unregisterAll(String pluginId) {
        return Mono.fromRunnable(
                () -> {
                    Deque<Runnable> undos = undoByPlugin.remove(pluginId);
                    if (undos != null) drainUndo(undos);
                    teardownAggregateState(pluginId);
                    countByPlugin.remove(pluginId);
                });
    }

    @Override
    public int registeredCount(String pluginId) {
        return countByPlugin.getOrDefault(pluginId, 0);
    }

    /** Snapshot of per-plugin component counts; for diagnostics. */
    public Map<String, Integer> snapshot() {
        return Map.copyOf(countByPlugin);
    }

    // ── per-component dispatch ──────────────────────────────────────────────

    private Mono<Void> registerOne(String pluginId, PluginComponent c, Deque<Runnable> undos) {
        if (c instanceof PluginComponent.SkillComponent skill) {
            return registerSkill(pluginId, skill.skillFile(), skill.name(), undos);
        }
        if (c instanceof PluginComponent.CommandComponent cmd) {
            // Commands are flat skills — same loader path.
            return registerSkill(pluginId, cmd.commandFile(), cmd.name(), undos);
        }
        if (c instanceof PluginComponent.AgentComponent agent) {
            return registerAgent(pluginId, agent, undos);
        }
        if (c instanceof PluginComponent.McpComponent mcp) {
            return registerMcp(pluginId, mcp, undos);
        }
        if (c instanceof PluginComponent.BinComponent bin) {
            return registerBin(pluginId, bin, undos);
        }
        // Hook/OutputStyle/Theme: captured but not yet bound. Push a no-op undo so
        // unregisterAll's count remains accurate.
        undos.push(() -> {});
        log.debug(
                "Component {} from plugin '{}' captured (binding deferred to follow-up phase)",
                c.getClass().getSimpleName(),
                pluginId);
        return Mono.empty();
    }

    private Mono<Void> registerAgent(
            String pluginId, PluginComponent.AgentComponent agent, Deque<Runnable> undos) {
        if (subagentRegistry == null) {
            undos.push(() -> {});
            return Mono.empty();
        }
        return Mono.fromRunnable(
                () -> {
                    try {
                        SubagentDefinition def =
                                subagentParser.parse(agent.agentFile(), agent.namespace());
                        subagentRegistry.register(def);
                        String qualifiedName = def.qualifiedName();
                        undos.push(
                                () -> {
                                    try {
                                        subagentRegistry.unregister(qualifiedName);
                                    } catch (Exception e) {
                                        log.debug(
                                                "Subagent unregister of '{}' failed: {}",
                                                qualifiedName,
                                                e.getMessage());
                                    }
                                });
                    } catch (Exception e) {
                        throw new RuntimeException(
                                "Failed to register subagent from "
                                        + agent.agentFile()
                                        + ": "
                                        + e.getMessage(),
                                e);
                    }
                });
    }

    private Mono<Void> registerSkill(
            String pluginId, java.nio.file.Path file, String name, Deque<Runnable> undos) {
        if (skillRegistry == null) {
            undos.push(() -> {});
            return Mono.empty();
        }
        return skillRegistry
                .loadFromFile(file)
                .doOnSuccess(
                        def -> {
                            String registeredName = def == null ? name : def.name();
                            undos.push(
                                    () -> {
                                        try {
                                            skillRegistry.unregister(registeredName);
                                        } catch (UnsupportedOperationException uoe) {
                                            log.debug(
                                                    "SkillRegistry impl does not support unregister"
                                                            + " — leaving '{}' in place",
                                                    registeredName);
                                        }
                                    });
                        })
                .then();
    }

    private Mono<Void> registerMcp(
            String pluginId, PluginComponent.McpComponent mcp, Deque<Runnable> undos) {
        if (mcpRegistrar == null) {
            undos.push(() -> {});
            return Mono.empty();
        }
        return mcpRegistrar
                .registerOne(pluginId, mcp)
                .doOnSuccess(
                        reg ->
                                undos.push(
                                        () -> {
                                            // PluginMcpRegistrar's per-plugin disable releases all
                                            // MCP servers belonging to this plugin in one call;
                                            // here we just record the intent — actual cleanup is
                                            // when unregisterAll fires.
                                        }))
                .then();
    }

    private Mono<Void> registerBin(
            String pluginId, PluginComponent.BinComponent bin, Deque<Runnable> undos) {
        if (environment == null) {
            undos.push(() -> {});
            return Mono.empty();
        }
        java.nio.file.Path binDir = bin.executable().getParent();
        if (binDir == null) {
            undos.push(() -> {});
            return Mono.empty();
        }
        environment.addBinDir(pluginId, binDir);
        // Bin contributions are aggregated per-plugin in PluginEnvironment, so the per-component
        // undo is a no-op; unregisterAll calls environment.removeForPlugin(pluginId) once.
        undos.push(() -> {});
        return Mono.empty();
    }

    private void drainUndo(Deque<Runnable> undos) {
        // LIFO order
        List<Runnable> snapshot = new ArrayList<>(undos);
        Collections.reverse(snapshot);
        for (Runnable r : snapshot) {
            try {
                r.run();
            } catch (Exception e) {
                log.warn("Undo step failed (continuing): {}", e.getMessage());
            }
        }
        undos.clear();
    }

    /**
     * Per-plugin teardown of MCP and PATH state — invoked after the LIFO undo finishes during
     * {@link #unregisterAll}.
     */
    public void teardownAggregateState(String pluginId) {
        if (mcpRegistrar != null) mcpRegistrar.disablePlugin(pluginId);
        if (environment != null) environment.removeForPlugin(pluginId);
    }
}
