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
import java.nio.file.Path;
import reactor.core.publisher.Mono;

/**
 * Resolves a {@link PluginSource} into a local on-disk plugin directory.
 *
 * <p>Each {@link PluginSource} variant (LocalPath / GitHub / GitUrl / GitSubdir / Npm) has a
 * matching fetcher implementation. Fetchers are responsible for downloading bytes (if remote),
 * decompressing archives, and returning the directory that contains the plugin's {@code
 * .kairo-plugin/plugin.json}.
 *
 * @apiNote Implementations must be safe to call concurrently for distinct sources; calls for the
 *     same source may serialize on the cache layer.
 */
public interface PluginSourceFetcher {

    /** Whether this fetcher can resolve the given source variant. */
    boolean supports(PluginSource source);

    /**
     * Fetches the source, returning the absolute path to the unpacked plugin root.
     *
     * <p>The returned path is the directory that should be passed to {@code PluginLoader.load()};
     * it must contain a {@code .kairo-plugin/} folder unless the plugin layout is implicit (single
     * SKILL.md / no manifest).
     *
     * @param source the source to fetch
     * @return Mono emitting the local plugin root
     */
    Mono<Path> fetch(PluginSource source);

    /**
     * Stable identifier for this fetcher kind, matching {@link PluginSource#type()}. Used by {@code
     * SourceFetcherRegistry} for dispatch.
     */
    String kind();
}
