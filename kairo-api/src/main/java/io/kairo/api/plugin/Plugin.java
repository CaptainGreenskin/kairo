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
import reactor.core.publisher.Mono;

/**
 * Runtime handle for a loaded plugin. Wraps the parsed manifest plus lifecycle hooks for the
 * implementation-specific parts that {@code PluginManager} cannot generically perform (e.g.
 * stopping a long-lived MCP subprocess).
 *
 * <p>Most plugins are passive bundles of declarative files and do not need to implement {@link
 * #start()} / {@link #stop()} themselves; the default implementations are no-op. Implementations
 * are required only for plugins that contribute Java-native components in a future release (the
 * {@link PluginContribution} annotation pathway).
 *
 * @since 1.2
 */
@Experimental("Plugin SPI — contract may change in v1.x")
public interface Plugin {

    /** Stable plugin identifier (matches {@link PluginInstallation#id()}). */
    String id();

    /** Parsed manifest. */
    PluginManifest manifest();

    /**
     * Optional startup hook, invoked after all components are registered. Default returns {@code
     * Mono.empty()}. Implementations should be non-blocking; long-running work should subscribe
     * onto the IO scheduler.
     */
    default Mono<Void> start() {
        return Mono.empty();
    }

    /**
     * Optional shutdown hook, invoked before any component is unregistered. Default returns {@code
     * Mono.empty()}.
     */
    default Mono<Void> stop() {
        return Mono.empty();
    }
}
