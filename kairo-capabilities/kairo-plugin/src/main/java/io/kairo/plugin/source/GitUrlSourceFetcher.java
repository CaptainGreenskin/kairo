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
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Fetches a plugin from any git URL via JGit clone.
 *
 * <p>Supports https://, ssh://, git://, file:// — anything JGit's transport layer understands.
 * Authentication uses the JGit defaults (system ssh agent, ~/.netrc, environment credentials); the
 * fetcher does not currently surface explicit credential handling — Phase D will add a pluggable
 * auth provider when CLI is wired up.
 *
 * <p>The cache slot identity is {@code &lt;url&gt;@&lt;ref&gt;}. The first fetch performs a shallow
 * clone (depth=1) on the requested ref; subsequent fetches reuse the cached working tree without
 * pulling so that pinned refs stay reproducible. Updating to a new ref requires evicting the cache
 * slot.
 */
public final class GitUrlSourceFetcher implements PluginSourceFetcher {

    private static final Logger log = LoggerFactory.getLogger(GitUrlSourceFetcher.class);

    private final PluginCacheManager cache;

    public GitUrlSourceFetcher(PluginCacheManager cache) {
        this.cache = cache;
    }

    @Override
    public boolean supports(PluginSource source) {
        return source instanceof PluginSource.GitUrl;
    }

    @Override
    public String kind() {
        return "git";
    }

    @Override
    public Mono<Path> fetch(PluginSource source) {
        if (!(source instanceof PluginSource.GitUrl gu)) {
            return Mono.error(new IllegalArgumentException("Not a GitUrl source: " + source));
        }
        return Mono.fromCallable(() -> doFetch(gu)).subscribeOn(Schedulers.boundedElastic());
    }

    private Path doFetch(PluginSource.GitUrl gu) throws IOException, GitAPIException {
        String identity = identityKey(gu);
        Path slot = cache.slotFor(kind(), identity);

        if (cache.isPopulated(kind(), identity)) {
            log.debug("Git source cache hit for {}", identity);
            return slot;
        }

        java.nio.file.Files.createDirectories(slot);
        String ref = gu.ref() == null || gu.ref().isBlank() ? "HEAD" : gu.ref();
        log.info("Cloning git plugin: {} (ref={})", gu.url(), ref);

        var clone = Git.cloneRepository().setURI(gu.url()).setDirectory(slot.toFile()).setDepth(1);
        if (!ref.equals("HEAD")) {
            clone.setBranch(ref);
        }
        try (Git git = clone.call()) {
            log.debug("Cloned git source into {}", slot);
        }
        return slot;
    }

    static String identityKey(PluginSource.GitUrl gu) {
        String ref = gu.ref() == null || gu.ref().isBlank() ? "HEAD" : gu.ref();
        return gu.url() + "@" + ref;
    }
}
