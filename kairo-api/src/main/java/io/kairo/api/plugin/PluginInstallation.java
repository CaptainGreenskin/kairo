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
import java.time.Instant;

/**
 * State record for an installed plugin: where it came from, where its bytes live on disk, what
 * scope it's installed at, and whether it's currently enabled.
 *
 * @param id stable plugin identifier — by convention {@code <marketplace>:<name>} when from a
 *     marketplace, else {@code local:<name>}
 * @param metadata parsed manifest metadata
 * @param source where the plugin came from
 * @param scope installation scope determining visibility precedence
 * @param enabled whether the plugin currently contributes components to registries
 * @param rootPath resolved on-disk plugin root directory (where {@code .claude-plugin/} lives)
 * @param dataPath persistent per-plugin data directory ({@code ~/.kairo/plugins/data/<id>/})
 * @param installedAt installation timestamp
 * @since 1.2
 */
@Experimental("Plugin SPI — contract may change in v1.x")
public record PluginInstallation(
        String id,
        PluginMetadata metadata,
        PluginSource source,
        PluginScope scope,
        boolean enabled,
        Path rootPath,
        Path dataPath,
        Instant installedAt) {

    public PluginInstallation {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Plugin id must not be blank");
        }
        if (metadata == null) throw new IllegalArgumentException("metadata required");
        if (source == null) throw new IllegalArgumentException("source required");
        if (scope == null) throw new IllegalArgumentException("scope required");
        if (rootPath == null) throw new IllegalArgumentException("rootPath required");
        if (dataPath == null) throw new IllegalArgumentException("dataPath required");
        if (installedAt == null) throw new IllegalArgumentException("installedAt required");
    }

    /** Returns a copy with the {@code enabled} flag flipped. */
    public PluginInstallation withEnabled(boolean newEnabled) {
        return new PluginInstallation(
                id, metadata, source, scope, newEnabled, rootPath, dataPath, installedAt);
    }
}
