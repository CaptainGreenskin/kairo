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
import java.util.ArrayList;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Dispatches a {@link PluginSource} to the registered {@link PluginSourceFetcher} that supports it.
 * Registration order matters: the first supporting fetcher wins.
 */
public final class SourceFetcherRegistry {

    private final List<PluginSourceFetcher> fetchers = new ArrayList<>();

    /** Adds a fetcher to the end of the dispatch chain. */
    public SourceFetcherRegistry register(PluginSourceFetcher fetcher) {
        fetchers.add(fetcher);
        return this;
    }

    /** Looks up the first fetcher that supports {@code source}. */
    public PluginSourceFetcher find(PluginSource source) {
        for (PluginSourceFetcher f : fetchers) {
            if (f.supports(source)) return f;
        }
        return null;
    }

    /** Convenience: dispatches and fetches in one call. */
    public Mono<java.nio.file.Path> fetch(PluginSource source) {
        PluginSourceFetcher f = find(source);
        if (f == null) {
            return Mono.error(
                    new UnsupportedOperationException(
                            "No fetcher registered for source type: " + source.type()));
        }
        return f.fetch(source);
    }

    /** Snapshot of registered fetchers (mostly for diagnostics). */
    public List<PluginSourceFetcher> list() {
        return List.copyOf(fetchers);
    }
}
