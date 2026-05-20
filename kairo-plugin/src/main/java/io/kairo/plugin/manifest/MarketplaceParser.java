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
import io.kairo.api.plugin.MarketplaceCatalog;
import io.kairo.api.plugin.MarketplaceEntry;
import io.kairo.api.plugin.PluginSource;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Parses {@code marketplace.json} (or {@code .kairo-plugin/marketplace.json}) into a {@link
 * MarketplaceCatalog} with one {@link MarketplaceEntry} per listed plugin.
 *
 * <p>Schema (Claude-Code-compatible):
 *
 * <pre>{@code
 * {
 *   "name": "official",
 *   "owner": { "name": "anthropic" },
 *   "description": "...",
 *   "trustLevel": "official",
 *   "plugins": [
 *     { "name": "commit-commands", "source": "./plugins/commit-commands" },
 *     { "name": "frontend-design", "source": { "github": "anthropics/claude-code", "ref": "main" } },
 *     { "name": "skill-x", "source": { "git-subdir": "https://x.git", "ref": "v1", "subdir": "p/x" } },
 *     { "name": "y", "source": { "url": "https://my.git" } },
 *     { "name": "z", "source": { "npm": "@scope/z", "version": "1.2.3" } },
 *     { "name": "v", "version": "1.2.3", "description": "curated", ... }
 *   ]
 * }
 * }</pre>
 *
 * <p>The {@code source} field is polymorphic: a string is treated as a relative {@link
 * PluginSource.LocalPath}; an object is dispatched on its first known key ({@code github} / {@code
 * url} / {@code git-subdir} / {@code npm}). Unrecognised entries are skipped with no error.
 */
public final class MarketplaceParser {

    private final ObjectMapper json = new ObjectMapper();

    /**
     * Parses a {@code marketplace.json} file. Resolves any relative {@link PluginSource.LocalPath}
     * sources against the marketplace file's parent directory.
     */
    public MarketplaceCatalog parse(Path file) throws IOException {
        try (Reader r = Files.newBufferedReader(file)) {
            JsonNode root = json.readTree(r);
            return parseTree(root, file.toAbsolutePath().getParent());
        }
    }

    /** Lower-level entry point: parse a pre-loaded JSON tree. */
    public MarketplaceCatalog parseTree(JsonNode root, Path basePathForRelative) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Marketplace must be a JSON object");
        }
        String name = textOrThrow(root, "name", "marketplace name");
        String owner = ownerName(root.get("owner"));
        String description = textOrNull(root, "description");
        String trustLevel = textOrNull(root, "trustLevel");

        List<MarketplaceEntry> entries = new ArrayList<>();
        JsonNode pluginsNode = root.get("plugins");
        if (pluginsNode != null && pluginsNode.isArray()) {
            for (JsonNode entryNode : pluginsNode) {
                MarketplaceEntry parsed = parseEntry(entryNode, basePathForRelative);
                if (parsed != null) entries.add(parsed);
            }
        }
        return new MarketplaceCatalog(name, owner, description, entries, trustLevel);
    }

    private MarketplaceEntry parseEntry(JsonNode entry, Path basePathForRelative) {
        if (entry == null || !entry.isObject()) return null;
        String name = textOrNull(entry, "name");
        if (name == null || name.isBlank()) return null;
        PluginSource source = parseSource(entry.get("source"), basePathForRelative);
        if (source == null) return null;
        return new MarketplaceEntry(
                name, source, textOrNull(entry, "description"), textOrNull(entry, "version"));
    }

    private PluginSource parseSource(JsonNode src, Path basePathForRelative) {
        if (src == null) return null;
        if (src.isTextual()) {
            // String shorthand → LocalPath, resolved against the marketplace file's directory.
            String txt = src.asText();
            Path p = Path.of(txt);
            if (!p.isAbsolute() && basePathForRelative != null) {
                p = basePathForRelative.resolve(txt).normalize();
            }
            return new PluginSource.LocalPath(p);
        }
        if (!src.isObject()) return null;

        // Polymorphic object — dispatch on first known key.
        if (src.has("github")) {
            String repo = src.get("github").asText();
            String ref = textOrNull(src, "ref");
            String sha = textOrNull(src, "sha");
            return new PluginSource.GitHub(repo, ref, sha);
        }
        if (src.has("git-subdir")) {
            String url = src.get("git-subdir").asText();
            String ref = textOrNull(src, "ref");
            String subdir = textOrNull(src, "subdir");
            if (subdir == null || subdir.isBlank()) return null;
            return new PluginSource.GitSubdir(url, ref, subdir);
        }
        if (src.has("npm")) {
            String pkg = src.get("npm").asText();
            String version = textOrNull(src, "version");
            return new PluginSource.Npm(pkg, version, parseTags(src.get("tags")));
        }
        if (src.has("url")) {
            String url = src.get("url").asText();
            String ref = textOrNull(src, "ref");
            return new PluginSource.GitUrl(url, ref);
        }
        if (src.has("path")) {
            // Explicit path object: { "path": "./..." }
            String txt = src.get("path").asText();
            Path p = Path.of(txt);
            if (!p.isAbsolute() && basePathForRelative != null) {
                p = basePathForRelative.resolve(txt).normalize();
            }
            return new PluginSource.LocalPath(p);
        }
        return null;
    }

    private Map<String, String> parseTags(JsonNode tags) {
        if (tags == null || !tags.isObject()) return Map.of();
        var out = new java.util.HashMap<String, String>();
        Iterator<Map.Entry<String, JsonNode>> it = tags.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            JsonNode v = e.getValue();
            if (v != null && v.isTextual()) out.put(e.getKey(), v.asText());
        }
        return out;
    }

    private static String ownerName(JsonNode owner) {
        if (owner == null) {
            throw new IllegalArgumentException("marketplace owner is required");
        }
        if (owner.isTextual()) return owner.asText();
        if (owner.isObject()) {
            String n = textOrNull(owner, "name");
            if (n == null || n.isBlank()) {
                throw new IllegalArgumentException("marketplace owner.name is required");
            }
            return n;
        }
        throw new IllegalArgumentException("marketplace owner must be string or {name: string}");
    }

    private static String textOrNull(JsonNode parent, String field) {
        if (parent == null) return null;
        JsonNode n = parent.get(field);
        return (n != null && n.isTextual()) ? n.asText() : null;
    }

    private static String textOrThrow(JsonNode parent, String field, String label) {
        String v = textOrNull(parent, field);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return v;
    }
}
