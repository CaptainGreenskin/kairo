/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin.manifest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.plugin.PluginMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parses {@code .kairo-plugin/plugin.json} manifests.
 *
 * <p>The {@code plugin.json} schema is deliberately compatible with Claude Code's manifest format
 * (same fields: name, version, description, author, license, mcpServers, etc.), so the contents of
 * a Claude Code plugin can be migrated into a Kairo plugin by copying its files into a directory
 * with a {@code .kairo-plugin/} folder. Kairo does not load {@code .claude-plugin/} directly —
 * Kairo plugins live under their own namespace.
 *
 * <p>v1.2 enforces exact-version policy: {@code version} must look like {@code
 * "MAJOR.MINOR.PATCH"}; range expressions ({@code "^1.2.0"}, {@code "~1.2"}, {@code "1.x"}, {@code
 * "latest"}) are rejected with an actionable error.
 */
public final class PluginManifestParser {

    /** Strict semver: MAJOR.MINOR.PATCH plus optional prerelease/build metadata (RFC 2119). */
    private static final Pattern EXACT_VERSION =
            Pattern.compile("^\\d+\\.\\d+\\.\\d+(?:-[A-Za-z0-9.-]+)?(?:\\+[A-Za-z0-9.-]+)?$");

    private final ObjectMapper mapper = new ObjectMapper();

    /** Parses the plugin.json at the conventional location inside {@code pluginRoot}. */
    public PluginMetadata parse(Path pluginRoot) throws IOException {
        Path manifestPath = locateManifest(pluginRoot);
        return parseManifestFile(manifestPath, pluginRoot.getFileName().toString());
    }

    /** Returns the {@code .kairo-plugin/plugin.json} path if present, else null. */
    public Path locateManifest(Path pluginRoot) {
        Path manifest = pluginRoot.resolve(".kairo-plugin").resolve("plugin.json");
        return Files.isRegularFile(manifest) ? manifest : null;
    }

    /**
     * Parses a manifest file. If {@code manifestPath} is null, synthesises a minimal manifest from
     * {@code defaultName} so directories without an explicit manifest still load (the plugin will
     * have no metadata-driven components but its skills/commands/agents folders are still scanned).
     */
    public PluginMetadata parseManifestFile(Path manifestPath, String defaultName)
            throws IOException {
        if (manifestPath == null) {
            return new PluginMetadata(
                    defaultName, "0.0.0", null, null, null, null, List.of(), List.of());
        }
        JsonNode root = mapper.readTree(Files.newBufferedReader(manifestPath));
        String name = textOrDefault(root, "name", defaultName);
        String version = requireExactVersion(textOrDefault(root, "version", "0.0.0"), manifestPath);
        String description = textOrNull(root, "description");
        String license = textOrNull(root, "license");
        String homepage = textOrNull(root, "homepage");

        PluginMetadata.Author author = parseAuthor(root.get("author"));
        List<String> keywords = readStringArray(root.get("keywords"));
        List<PluginMetadata.Dependency> deps = parseDependencies(root.get("dependencies"));

        return new PluginMetadata(
                name, version, description, author, license, homepage, keywords, deps);
    }

    /** Reads {@code mcpServers} as a raw object map for downstream consumers (.mcp.json loader). */
    public Map<String, Object> readMcpServers(Path manifestPath) throws IOException {
        if (manifestPath == null) return Map.of();
        JsonNode root = mapper.readTree(Files.newBufferedReader(manifestPath));
        JsonNode node = root.get("mcpServers");
        if (node == null || !node.isObject()) return Map.of();
        return mapper.convertValue(node, Map.class);
    }

    private String requireExactVersion(String version, Path manifestPath) {
        if (version == null || version.isBlank()) return "0.0.0";
        if (!EXACT_VERSION.matcher(version).matches()) {
            throw new IllegalArgumentException(
                    "Plugin manifest at "
                            + manifestPath
                            + " has non-exact version '"
                            + version
                            + "'. v1.2 requires exact MAJOR.MINOR.PATCH (range/wildcard not yet"
                            + " supported; will arrive in v1.3).");
        }
        return version;
    }

    private PluginMetadata.Author parseAuthor(JsonNode node) {
        if (node == null) return null;
        if (node.isTextual()) {
            return new PluginMetadata.Author(node.asText(), null, null);
        }
        if (node.isObject()) {
            return new PluginMetadata.Author(
                    textOrNull(node, "name"), textOrNull(node, "email"), textOrNull(node, "url"));
        }
        return null;
    }

    private List<PluginMetadata.Dependency> parseDependencies(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<PluginMetadata.Dependency> out = new ArrayList<>();
        for (JsonNode entry : node) {
            String n = textOrNull(entry, "name");
            String v = textOrNull(entry, "version");
            if (n != null && !n.isBlank()) {
                out.add(new PluginMetadata.Dependency(n, v));
            }
        }
        return out;
    }

    private List<String> readStringArray(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> out = new ArrayList<>();
        for (JsonNode entry : node) {
            if (entry.isTextual()) out.add(entry.asText());
        }
        return out;
    }

    private String textOrNull(JsonNode parent, String field) {
        if (parent == null) return null;
        JsonNode n = parent.get(field);
        return (n != null && n.isTextual()) ? n.asText() : null;
    }

    private String textOrDefault(JsonNode parent, String field, String fallback) {
        String v = textOrNull(parent, field);
        return v == null ? fallback : v;
    }
}
