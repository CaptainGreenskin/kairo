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

import io.kairo.api.plugin.PluginComponent;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Per-plugin atomic registrar for {@link PluginComponent} contributions.
 *
 * <p>{@code DefaultPluginManager} delegates the actual binding of components to the host's Kairo
 * registries (skills/hooks/MCP/bin/...) through this interface so the manager itself stays free of
 * those concrete dependencies and can run with a no-op registrar in tests.
 *
 * <p>Implementations MUST guarantee atomicity: if any single component within {@link #registerAll}
 * fails to register, every previously-registered component for the same plugin id is undone before
 * the error is propagated to the caller.
 *
 * @apiNote Implementations must be thread-safe; {@link #registerAll} and {@link #unregisterAll} for
 *     different plugin ids may run concurrently.
 */
public interface ComponentRegistrar {

    /**
     * Registers all components belonging to one plugin in the canonical order
     * (tools→skills→agents→hooks→mcp→bin→outputStyles→themes). On the first failure, all
     * already-registered siblings of the same plugin are rolled back and the error is propagated.
     */
    Mono<Void> registerAll(String pluginId, List<PluginComponent> components);

    /** Removes every registration that was previously contributed by this plugin. */
    Mono<Void> unregisterAll(String pluginId);

    /** Number of components currently registered against {@code pluginId}. */
    int registeredCount(String pluginId);

    /** No-op registrar — useful for tests / phases that do not care about real wiring. */
    static ComponentRegistrar noOp() {
        return new NoOpComponentRegistrar();
    }
}
