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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.plugin.PluginSource;
import io.kairo.plugin.installer.PluginCacheManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitUrlSourceFetcherTest {

    @Test
    void identityKeyIncludesUrlAndRef() {
        var gu = new PluginSource.GitUrl("https://example.com/foo.git", "main");
        assertThat(GitUrlSourceFetcher.identityKey(gu))
                .isEqualTo("https://example.com/foo.git@main");

        var noRef = new PluginSource.GitUrl("git://x", null);
        assertThat(GitUrlSourceFetcher.identityKey(noRef)).isEqualTo("git://x@HEAD");
    }

    @Test
    void clonesPluginFromLocalGitRepo(@TempDir Path tmp) throws Exception {
        Path origin = createBareLikeRepo(tmp.resolve("origin"));
        var cache = new PluginCacheManager(tmp.resolve("cache"));
        var fetcher = new GitUrlSourceFetcher(cache);

        Path cloned =
                fetcher.fetch(new PluginSource.GitUrl(origin.toUri().toString(), "main"))
                        .block(Duration.ofSeconds(30));

        assertThat(cloned).isNotNull();
        assertThat(cloned.resolve(".kairo-plugin/plugin.json")).exists();
        assertThat(cloned.resolve("SKILL.md")).hasContent("hello");
    }

    @Test
    void cacheHitDoesNotReclone(@TempDir Path tmp) throws Exception {
        Path origin = createBareLikeRepo(tmp.resolve("origin"));
        var cache = new PluginCacheManager(tmp.resolve("cache"));
        var fetcher = new GitUrlSourceFetcher(cache);
        var src = new PluginSource.GitUrl(origin.toUri().toString(), "main");

        Path first = fetcher.fetch(src).block(Duration.ofSeconds(30));
        long firstMtime = Files.getLastModifiedTime(first.resolve("SKILL.md")).toMillis();

        // Add a new commit to origin — second fetch should NOT pick it up because cache wins.
        try (Git git = Git.open(origin.toFile())) {
            Files.writeString(origin.resolve("SKILL.md"), "modified");
            git.add().addFilepattern("SKILL.md").call();
            git.commit().setSign(false).setMessage("update").call();
        }

        Path second = fetcher.fetch(src).block(Duration.ofSeconds(30));
        assertThat(second).isEqualTo(first);
        assertThat(Files.readString(second.resolve("SKILL.md"))).isEqualTo("hello");
        assertThat(Files.getLastModifiedTime(second.resolve("SKILL.md")).toMillis())
                .isEqualTo(firstMtime);
    }

    @Test
    void evictForcesReclone(@TempDir Path tmp) throws Exception {
        Path origin = createBareLikeRepo(tmp.resolve("origin"));
        var cache = new PluginCacheManager(tmp.resolve("cache"));
        var fetcher = new GitUrlSourceFetcher(cache);
        var src = new PluginSource.GitUrl(origin.toUri().toString(), "main");

        fetcher.fetch(src).block(Duration.ofSeconds(30));

        // Update origin then evict cache.
        try (Git git = Git.open(origin.toFile())) {
            Files.writeString(origin.resolve("SKILL.md"), "after-evict");
            git.add().addFilepattern("SKILL.md").call();
            git.commit().setSign(false).setMessage("update").call();
        }
        cache.evict("git", GitUrlSourceFetcher.identityKey(src));

        Path second = fetcher.fetch(src).block(Duration.ofSeconds(30));
        assertThat(Files.readString(second.resolve("SKILL.md"))).isEqualTo("after-evict");
    }

    @Test
    void rejectsWrongSourceType(@TempDir Path tmp) {
        var fetcher = new GitUrlSourceFetcher(new PluginCacheManager(tmp));
        assertThatThrownBy(
                        () ->
                                fetcher.fetch(new PluginSource.LocalPath(tmp))
                                        .block(Duration.ofSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void supportsAndKind() {
        var fetcher = new GitUrlSourceFetcher(null);
        assertThat(fetcher.kind()).isEqualTo("git");
        assertThat(fetcher.supports(new PluginSource.GitUrl("git://x", null))).isTrue();
        assertThat(fetcher.supports(new PluginSource.GitHub("a/b", null, null))).isFalse();
    }

    @Test
    void invalidUrlPropagatesError(@TempDir Path tmp) {
        var cache = new PluginCacheManager(tmp.resolve("cache"));
        var fetcher = new GitUrlSourceFetcher(cache);
        assertThatThrownBy(
                        () ->
                                fetcher.fetch(
                                                new PluginSource.GitUrl(
                                                        "file:///definitely/does/not/exist.git",
                                                        "main"))
                                        .block(Duration.ofSeconds(15)))
                .isNotNull(); // either JGit's TransportException or IOException — both acceptable
    }

    /**
     * Creates a non-bare git repo with one commit containing a Kairo-style plugin tree, suitable
     * for cloning via file:// URI.
     */
    private static Path createBareLikeRepo(Path path) throws IOException {
        try {
            Files.createDirectories(path);
            try (Git git = Git.init().setDirectory(path.toFile()).setInitialBranch("main").call()) {
                Files.createDirectories(path.resolve(".kairo-plugin"));
                Files.writeString(
                        path.resolve(".kairo-plugin/plugin.json"),
                        "{\"name\":\"git-fixture\",\"version\":\"1.0.0\"}");
                Files.writeString(path.resolve("SKILL.md"), "hello");
                git.add().addFilepattern(".").call();
                git.commit()
                        .setMessage("initial")
                        .setSign(false)
                        .setAuthor("test", "test@test")
                        .setCommitter("test", "test@test")
                        .call();
            }
            return path;
        } catch (Exception e) {
            throw new IOException("failed to set up test repo", e);
        }
    }
}
