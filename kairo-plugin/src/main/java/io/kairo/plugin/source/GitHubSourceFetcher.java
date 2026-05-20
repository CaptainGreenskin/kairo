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
import io.kairo.plugin.installer.PluginCacheManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Fetches a plugin from a GitHub repository via the public archive endpoint:
 *
 * <pre>https://github.com/&lt;owner/repo&gt;/archive/&lt;ref|sha&gt;.tar.gz</pre>
 *
 * <p>No GitHub API token is required for public repos. The tarball wraps content in a single {@code
 * &lt;repo&gt;-&lt;ref&gt;/} directory; that prefix is stripped during extraction so the returned
 * path points at the plugin root directly.
 *
 * <p>Idempotent: if the cache slot is already populated for the same source identity, the cached
 * directory is returned without re-fetching.
 */
public final class GitHubSourceFetcher implements PluginSourceFetcher {

    private static final Logger log = LoggerFactory.getLogger(GitHubSourceFetcher.class);

    private final PluginCacheManager cache;
    private final HttpDownloader http;

    public GitHubSourceFetcher(PluginCacheManager cache, HttpDownloader http) {
        this.cache = cache;
        this.http = http;
    }

    @Override
    public boolean supports(PluginSource source) {
        return source instanceof PluginSource.GitHub;
    }

    @Override
    public String kind() {
        return "github";
    }

    @Override
    public Mono<Path> fetch(PluginSource source) {
        if (!(source instanceof PluginSource.GitHub gh)) {
            return Mono.error(new IllegalArgumentException("Not a GitHub source: " + source));
        }
        return Mono.fromCallable(() -> doFetch(gh)).subscribeOn(Schedulers.boundedElastic());
    }

    private Path doFetch(PluginSource.GitHub gh) throws IOException, InterruptedException {
        String identity = identityKey(gh);
        Path slot = cache.slotFor(kind(), identity);

        if (cache.isPopulated(kind(), identity)) {
            log.debug("GitHub source cache hit for {}", identity);
            return slot;
        }

        String archiveUrl = archiveUrl(gh);
        log.info("Fetching GitHub plugin tarball: {}", archiveUrl);
        try (InputStream body = http.get(archiveUrl)) {
            // GitHub's archive tarball wraps everything in <repo>-<ref>/, hence stripComponents=1.
            TarGzExtractor.extract(body, slot, 1);
        }
        return slot;
    }

    static String identityKey(PluginSource.GitHub gh) {
        String pin = gh.sha() != null ? gh.sha() : gh.ref() != null ? gh.ref() : "main";
        return gh.ownerRepo() + "@" + pin;
    }

    static String archiveUrl(PluginSource.GitHub gh) {
        String pin = gh.sha() != null ? gh.sha() : gh.ref() != null ? gh.ref() : "main";
        return "https://github.com/" + gh.ownerRepo() + "/archive/" + pin + ".tar.gz";
    }
}
