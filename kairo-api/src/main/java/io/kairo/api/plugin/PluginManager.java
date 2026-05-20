/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.api.plugin;

import io.kairo.api.Experimental;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Orchestrates the plugin lifecycle: install → enable → disable → uninstall. Component registration
 * is atomic — if any step in {@link #enable} fails, all already-registered components from this
 * plugin are rolled back before the error propagates.
 *
 * @apiNote Implementations must be thread-safe.
 * @implSpec Component registration order is fixed: tools → skills → agents → hooks → mcp → bin →
 *     outputStyles → themes. See {@link PluginComponent#order()}.
 * @since 1.2
 */
@Experimental("Plugin SPI — contract may change in v1.x")
public interface PluginManager {

    /**
     * Resolves a {@link PluginSource} into bytes on disk, parses its manifest, and adds it to the
     * registry as {@link PluginScope#USER} by default. The returned plugin is initially disabled —
     * call {@link #enable} to start contributing components.
     */
    Mono<PluginInstallation> install(PluginSource source, PluginScope scope);

    /** Removes a plugin entirely (cache cleared, settings entry removed, components disabled). */
    Mono<Void> uninstall(String id);

    /**
     * Atomically registers all components contributed by the plugin. On any failure, the components
     * already registered are rolled back and an {@link PluginEvent.EnableFailed} event is emitted.
     */
    Mono<Void> enable(String id);

    /** Removes all of the plugin's components from the registries but retains the install. */
    Mono<Void> disable(String id);

    /** Updates a plugin to a new version (re-resolves source, re-parses manifest). */
    Mono<PluginInstallation> update(String id);

    /** Lists all installed plugins. */
    List<PluginInstallation> list();

    /** Lifecycle event stream. Hot, multicast — late subscribers do not see past events. */
    Flux<PluginEvent> events();

    /** Hot reload: re-scans plugin roots, rebinds hooks/MCP, no process restart. */
    Mono<Void> reload();
}
