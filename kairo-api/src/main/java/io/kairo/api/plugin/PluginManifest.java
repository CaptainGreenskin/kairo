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
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Full parsed view of a plugin on disk: resolved metadata + all component contributions.
 *
 * <p>This is the result of running {@code PluginLoader.load(rootPath)}. Once a manifest is built,
 * {@link PluginManager#enable} can register all components atomically.
 *
 * @param metadata parsed {@code plugin.json}
 * @param rootPath plugin root directory (where {@code .claude-plugin/} lives)
 * @param components all discovered component contributions, deduped and sorted by {@link
 *     PluginComponent#order()}
 * @param mcpServers raw mcpServers map from {@code plugin.json} or {@code .mcp.json}; preserved for
 *     diagnostic / audit purposes
 * @since 1.2
 */
@Experimental("Plugin SPI — contract may change in v1.x")
public record PluginManifest(
        PluginMetadata metadata,
        Path rootPath,
        List<PluginComponent> components,
        Map<String, Object> mcpServers) {

    public PluginManifest {
        if (metadata == null) throw new IllegalArgumentException("metadata required");
        if (rootPath == null) throw new IllegalArgumentException("rootPath required");
        components = components == null ? List.of() : List.copyOf(components);
        mcpServers = mcpServers == null ? Map.of() : Map.copyOf(mcpServers);
    }
}
