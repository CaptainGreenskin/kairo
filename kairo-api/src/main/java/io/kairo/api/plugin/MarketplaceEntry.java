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

/**
 * One plugin listing inside a marketplace catalog.
 *
 * @param name plugin name (matches {@link PluginMetadata#name()})
 * @param source how to fetch the plugin's bytes
 * @param description optional human-readable summary; may differ from manifest description if
 *     marketplace curates its own copy
 * @param version pinned version string; null means "latest at install time"
 * @since 1.2
 */
@Experimental("Plugin SPI — contract may change in v1.x")
public record MarketplaceEntry(
        String name, PluginSource source, String description, String version) {

    public MarketplaceEntry {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Marketplace entry name must not be blank");
        }
        if (source == null) throw new IllegalArgumentException("source required");
    }
}
