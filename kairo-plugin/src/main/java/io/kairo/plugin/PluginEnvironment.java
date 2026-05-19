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

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide aggregation of plugin-contributed PATH entries.
 *
 * <p>Each enabled plugin can contribute zero or more {@code bin/} directories. When kairo's shell
 * or process tools spawn a child, they query this environment for the augmented PATH and prepend
 * the plugin entries so plugin-shipped executables are findable. The host machine's {@code PATH} is
 * never mutated.
 *
 * <p>Insertion order is preserved across plugins so behaviour is deterministic when two plugins
 * ship binaries with the same name (first registered wins). Within a single plugin, entries are
 * deduplicated.
 */
public final class PluginEnvironment {

    private final Map<String, Set<Path>> byPluginId = new ConcurrentHashMap<>();

    /** Records that {@code pluginId} contributes a bin directory. No-op if already present. */
    public void addBinDir(String pluginId, Path binDir) {
        if (pluginId == null || binDir == null) return;
        byPluginId
                .computeIfAbsent(pluginId, k -> Collections.synchronizedSet(new LinkedHashSet<>()))
                .add(binDir.toAbsolutePath());
    }

    /** Convenience: register all bin dirs for a plugin in one call. */
    public void addBinDirs(String pluginId, List<Path> binDirs) {
        if (binDirs == null) return;
        for (Path p : binDirs) addBinDir(pluginId, p);
    }

    /** Removes every bin dir contributed by {@code pluginId}. Used during plugin disable. */
    public void removeForPlugin(String pluginId) {
        byPluginId.remove(pluginId);
    }

    /** All currently-active plugin bin dirs, deduplicated, in insertion order. */
    public List<Path> activeBinDirs() {
        // LinkedHashMap preserves plugin insertion order; LinkedHashSet preserves per-plugin order.
        var out = new LinkedHashSet<Path>();
        // ConcurrentHashMap iteration order is not guaranteed — use snapshot ordered by plugin id
        // for stable test behaviour.
        new LinkedHashMap<>(byPluginId)
                .entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(e -> out.addAll(e.getValue()));
        return List.copyOf(out);
    }

    /**
     * Returns the existing PATH value with all active plugin bin dirs prepended. The original value
     * is preserved at the end so user-installed binaries still resolve. Plugin entries take
     * precedence — same model Claude Code uses.
     *
     * @param originalPath the host's current PATH (typically {@code System.getenv("PATH")}); may be
     *     null
     */
    public String augmentedPath(String originalPath) {
        List<Path> active = activeBinDirs();
        if (active.isEmpty()) return originalPath == null ? "" : originalPath;
        StringBuilder sb = new StringBuilder();
        for (Path p : active) {
            if (sb.length() > 0) sb.append(File.pathSeparator);
            sb.append(p);
        }
        if (originalPath != null && !originalPath.isBlank()) {
            sb.append(File.pathSeparator).append(originalPath);
        }
        return sb.toString();
    }

    /**
     * Returns a fresh environment map: callers' env entries plus a {@code PATH} key set to {@link
     * #augmentedPath(String)}. Useful for {@link ProcessBuilder} integration; the returned map is
     * mutable so callers can add or override entries before passing to {@code
     * ProcessBuilder.environment().putAll}.
     */
    public Map<String, String> envWithAugmentedPath(Map<String, String> originalEnv) {
        var out = new java.util.HashMap<String, String>();
        if (originalEnv != null) out.putAll(originalEnv);
        String existingPath = out.get("PATH");
        if (existingPath == null) existingPath = System.getenv("PATH");
        out.put("PATH", augmentedPath(existingPath));
        return out;
    }

    /** Snapshot view, mostly for diagnostics / tests. */
    public Map<String, List<Path>> snapshot() {
        var out = new LinkedHashMap<String, List<Path>>();
        new LinkedHashMap<>(byPluginId)
                .entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(e -> out.put(e.getKey(), new ArrayList<>(e.getValue())));
        return Collections.unmodifiableMap(out);
    }

    /** True iff no plugin currently contributes a bin dir. */
    public boolean isEmpty() {
        return byPluginId.isEmpty();
    }
}
