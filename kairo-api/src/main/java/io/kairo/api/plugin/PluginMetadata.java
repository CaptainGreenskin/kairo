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
 * Plugin metadata, parsed from {@code .claude-plugin/plugin.json} (or {@code
 * .kairo-plugin/plugin.json}).
 *
 * <p>Fields mirror the Claude Code plugin manifest schema for cross-ecosystem compatibility.
 *
 * @param name plugin name (kebab-case recommended); when manifest is absent, defaults to plugin
 *     directory name
 * @param version exact version string; v1.2 enforces exact form ({@code "1.2.3"}), wildcard or
 *     range ({@code "^1.2.0"}) is rejected
 * @param description short human-readable description; nullable
 * @param author author info; nullable
 * @param license SPDX license identifier; nullable
 * @param homepage project homepage URL; nullable
 * @param keywords search/discovery keywords; never null, possibly empty
 * @param dependencies dependency declarations; v1.2 records but does not resolve them
 * @since 1.2
 */
@Experimental("Plugin SPI — contract may change in v1.x")
public record PluginMetadata(
        String name,
        String version,
        String description,
        Author author,
        String license,
        String homepage,
        List<String> keywords,
        List<Dependency> dependencies) {

    public PluginMetadata {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Plugin name must not be blank");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Plugin version must not be blank");
        }
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }

    /** Plugin author info. */
    public record Author(String name, String email, String url) {}

    /** Plugin dependency declaration. */
    public record Dependency(String name, String version) {}
}
