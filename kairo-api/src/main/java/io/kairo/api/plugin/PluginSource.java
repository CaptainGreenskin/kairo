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
import java.util.Map;

/**
 * Where a plugin's bytes come from. Mirrors the {@code source} field of marketplace entries in
 * Claude Code's marketplace.json schema.
 *
 * @since 1.2
 */
@Experimental("Plugin SPI — contract may change in v1.x")
public sealed interface PluginSource {

    /** Stable identifier for telemetry / settings persistence. */
    String type();

    /** Local filesystem path. Used when {@code source} is a relative or absolute path string. */
    record LocalPath(Path path) implements PluginSource {
        @Override
        public String type() {
            return "path";
        }
    }

    /**
     * GitHub repository, optionally pinned by {@code ref} (branch / tag) or {@code sha} (commit).
     * Resolved as {@code https://github.com/owner/repo/archive/<ref|sha>.tar.gz} (no API token
     * required).
     */
    record GitHub(String ownerRepo, String ref, String sha) implements PluginSource {
        @Override
        public String type() {
            return "github";
        }
    }

    /** Arbitrary git URL (https/ssh). Resolved via JGit clone. */
    record GitUrl(String url, String ref) implements PluginSource {
        @Override
        public String type() {
            return "git";
        }
    }

    /**
     * Subdirectory inside a git repository, fetched via sparse checkout. Used for monorepos that
     * publish multiple plugins under one repo.
     */
    record GitSubdir(String url, String ref, String subdir) implements PluginSource {
        @Override
        public String type() {
            return "git-subdir";
        }
    }

    /**
     * NPM package source. Resolved by downloading the registry tarball and verifying the SHA-512
     * integrity field if present.
     */
    record Npm(String packageName, String version, Map<String, String> tags)
            implements PluginSource {
        public Npm {
            tags = tags == null ? Map.of() : Map.copyOf(tags);
        }

        @Override
        public String type() {
            return "npm";
        }
    }
}
