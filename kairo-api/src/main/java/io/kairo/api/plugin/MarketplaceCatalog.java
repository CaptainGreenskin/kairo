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

/**
 * Parsed view of a marketplace, mirrored from {@code .claude-plugin/marketplace.json}.
 *
 * <p>A marketplace is just a manifest of {@link MarketplaceEntry} listings — it does not host
 * plugin bytes. Each entry's {@link PluginSource} points at where the bytes actually live (path,
 * GitHub, npm, etc.). This keeps Kairo out of the business of running plugin servers.
 *
 * @param name catalog name
 * @param ownerName catalog owner, used for trust labelling
 * @param description optional description
 * @param plugins entries
 * @param trustLevel coarse classification: {@code official} / {@code community} / {@code unknown}
 * @since 1.2
 */
@Experimental("Plugin SPI — contract may change in v1.x")
public record MarketplaceCatalog(
        String name,
        String ownerName,
        String description,
        List<MarketplaceEntry> plugins,
        String trustLevel) {

    public MarketplaceCatalog {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Marketplace name must not be blank");
        }
        if (ownerName == null || ownerName.isBlank()) {
            throw new IllegalArgumentException("Marketplace owner name must not be blank");
        }
        plugins = plugins == null ? List.of() : List.copyOf(plugins);
        trustLevel = trustLevel == null ? "unknown" : trustLevel;
    }
}
