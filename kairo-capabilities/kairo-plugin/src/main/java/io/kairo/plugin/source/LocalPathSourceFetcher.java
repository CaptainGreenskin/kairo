/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin.source;

import io.kairo.api.plugin.PluginSource;
import java.nio.file.Files;
import java.nio.file.Path;
import reactor.core.publisher.Mono;

/**
 * Trivial fetcher for {@link PluginSource.LocalPath} — returns the source path as-is.
 *
 * <p>No copy is made: the plugin lives where the user pointed at. Useful for live development
 * (edit-reload-edit) and for testing.
 */
public final class LocalPathSourceFetcher implements PluginSourceFetcher {

    @Override
    public boolean supports(PluginSource source) {
        return source instanceof PluginSource.LocalPath;
    }

    @Override
    public Mono<Path> fetch(PluginSource source) {
        if (!(source instanceof PluginSource.LocalPath local)) {
            return Mono.error(new IllegalArgumentException("Not a LocalPath source: " + source));
        }
        Path p = local.path();
        if (!Files.isDirectory(p)) {
            return Mono.error(
                    new IllegalArgumentException(
                            "LocalPath source does not point at a directory: " + p));
        }
        return Mono.just(p.toAbsolutePath());
    }

    @Override
    public String kind() {
        return "path";
    }
}
