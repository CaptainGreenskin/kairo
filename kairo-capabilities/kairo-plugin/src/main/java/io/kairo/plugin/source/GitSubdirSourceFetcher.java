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
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Fetches a plugin contained inside a subdirectory of a git repository — the typical layout for
 * monorepos that publish many plugins under one repo (e.g. {@code anthropics/claude-code/plugins/
 * commit-commands}).
 *
 * <p>Strategy: shallow-clone the repo at the requested ref into the cache slot, then return the
 * resolved {@code &lt;slot&gt;/&lt;subdir&gt;} path. JGit does not currently expose first-class
 * sparse-checkout via its high-level API, so we accept the slight extra storage cost in exchange
 * for simpler, well-tested clone logic.
 *
 * <p>The cache identity is {@code &lt;url&gt;@&lt;ref&gt;:&lt;subdir&gt;} — different subdirs in
 * the same repo each get their own clone slot for now. A future optimisation can share a single
 * clone across subdirs by stripping subdir from the identity key.
 */
public final class GitSubdirSourceFetcher implements PluginSourceFetcher {

    private static final Logger log = LoggerFactory.getLogger(GitSubdirSourceFetcher.class);

    private final PluginCacheManager cache;

    public GitSubdirSourceFetcher(PluginCacheManager cache) {
        this.cache = cache;
    }

    @Override
    public boolean supports(PluginSource source) {
        return source instanceof PluginSource.GitSubdir;
    }

    @Override
    public String kind() {
        return "git-subdir";
    }

    @Override
    public Mono<Path> fetch(PluginSource source) {
        if (!(source instanceof PluginSource.GitSubdir gs)) {
            return Mono.error(new IllegalArgumentException("Not a GitSubdir source: " + source));
        }
        return Mono.fromCallable(() -> doFetch(gs)).subscribeOn(Schedulers.boundedElastic());
    }

    private Path doFetch(PluginSource.GitSubdir gs) throws IOException, GitAPIException {
        if (gs.subdir() == null || gs.subdir().isBlank()) {
            throw new IllegalArgumentException("GitSubdir source requires a non-blank subdir");
        }
        // Disallow path traversal in the subdir field.
        if (gs.subdir().contains("..")) {
            throw new IllegalArgumentException(
                    "GitSubdir subdir must not contain '..': " + gs.subdir());
        }

        String identity = identityKey(gs);
        Path slot = cache.slotFor(kind(), identity);

        if (cache.isPopulated(kind(), identity)) {
            log.debug("GitSubdir source cache hit for {}", identity);
            return resolveSubdir(slot, gs.subdir());
        }

        Files.createDirectories(slot);
        String ref = gs.ref() == null || gs.ref().isBlank() ? "HEAD" : gs.ref();
        log.info(
                "Cloning git monorepo for subdir plugin: {} (ref={}, subdir={})",
                gs.url(),
                ref,
                gs.subdir());

        var clone = Git.cloneRepository().setURI(gs.url()).setDirectory(slot.toFile()).setDepth(1);
        if (!ref.equals("HEAD")) clone.setBranch(ref);
        try (Git git = clone.call()) {
            log.debug("Cloned monorepo into {}", slot);
        }

        return resolveSubdir(slot, gs.subdir());
    }

    private Path resolveSubdir(Path slot, String subdir) throws IOException {
        Path resolved = slot.resolve(subdir).normalize();
        if (!resolved.startsWith(slot)) {
            throw new IOException("Subdir resolution escaped slot: " + subdir);
        }
        if (!Files.isDirectory(resolved)) {
            throw new IOException("Subdir does not exist in cloned repo: " + subdir);
        }
        return resolved;
    }

    static String identityKey(PluginSource.GitSubdir gs) {
        String ref = gs.ref() == null || gs.ref().isBlank() ? "HEAD" : gs.ref();
        return gs.url() + "@" + ref + ":" + gs.subdir();
    }
}
