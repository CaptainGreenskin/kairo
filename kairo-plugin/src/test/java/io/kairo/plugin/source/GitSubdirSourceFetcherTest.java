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

class GitSubdirSourceFetcherTest {

    @Test
    void identityKeyIncludesSubdir() {
        var gs = new PluginSource.GitSubdir("https://example.com/repo.git", "main", "plugins/foo");
        assertThat(GitSubdirSourceFetcher.identityKey(gs))
                .isEqualTo("https://example.com/repo.git@main:plugins/foo");
    }

    @Test
    void clonesAndReturnsSubdir(@TempDir Path tmp) throws Exception {
        Path origin = createMonorepo(tmp.resolve("origin"));
        var cache = new PluginCacheManager(tmp.resolve("cache"));
        var fetcher = new GitSubdirSourceFetcher(cache);

        Path subdir =
                fetcher.fetch(
                                new PluginSource.GitSubdir(
                                        origin.toUri().toString(), "main", "plugins/alpha"))
                        .block(Duration.ofSeconds(30));

        assertThat(subdir).isNotNull();
        assertThat(subdir.getFileName().toString()).isEqualTo("alpha");
        assertThat(subdir.resolve(".kairo-plugin/plugin.json")).exists();
        assertThat(subdir.resolve("SKILL.md")).hasContent("alpha-skill");
    }

    @Test
    void differentSubdirsResolveToDifferentSlotsByDefault(@TempDir Path tmp) throws Exception {
        Path origin = createMonorepo(tmp.resolve("origin"));
        var cache = new PluginCacheManager(tmp.resolve("cache"));
        var fetcher = new GitSubdirSourceFetcher(cache);

        Path alpha =
                fetcher.fetch(
                                new PluginSource.GitSubdir(
                                        origin.toUri().toString(), "main", "plugins/alpha"))
                        .block(Duration.ofSeconds(30));
        Path beta =
                fetcher.fetch(
                                new PluginSource.GitSubdir(
                                        origin.toUri().toString(), "main", "plugins/beta"))
                        .block(Duration.ofSeconds(30));

        assertThat(alpha).isNotEqualTo(beta);
        assertThat(beta.resolve("SKILL.md")).hasContent("beta-skill");
    }

    @Test
    void rejectsBlankSubdir(@TempDir Path tmp) {
        var cache = new PluginCacheManager(tmp.resolve("cache"));
        var fetcher = new GitSubdirSourceFetcher(cache);
        assertThatThrownBy(
                        () ->
                                fetcher.fetch(new PluginSource.GitSubdir("git://x", null, ""))
                                        .block(Duration.ofSeconds(2)))
                .hasMessageContaining("non-blank subdir");
    }

    @Test
    void rejectsTraversalInSubdir(@TempDir Path tmp) {
        var cache = new PluginCacheManager(tmp.resolve("cache"));
        var fetcher = new GitSubdirSourceFetcher(cache);
        assertThatThrownBy(
                        () ->
                                fetcher.fetch(
                                                new PluginSource.GitSubdir(
                                                        "git://x", "main", "../escape"))
                                        .block(Duration.ofSeconds(2)))
                .hasMessageContaining("must not contain '..'");
    }

    @Test
    void missingSubdirInCloneFailsClearly(@TempDir Path tmp) throws Exception {
        Path origin = createMonorepo(tmp.resolve("origin"));
        var cache = new PluginCacheManager(tmp.resolve("cache"));
        var fetcher = new GitSubdirSourceFetcher(cache);
        assertThatThrownBy(
                        () ->
                                fetcher.fetch(
                                                new PluginSource.GitSubdir(
                                                        origin.toUri().toString(),
                                                        "main",
                                                        "plugins/does-not-exist"))
                                        .block(Duration.ofSeconds(30)))
                .hasMessageContaining("does not exist");
    }

    @Test
    void supportsAndKind() {
        var fetcher = new GitSubdirSourceFetcher(null);
        assertThat(fetcher.kind()).isEqualTo("git-subdir");
        assertThat(fetcher.supports(new PluginSource.GitSubdir("git://x", null, "p"))).isTrue();
        assertThat(fetcher.supports(new PluginSource.GitUrl("git://x", null))).isFalse();
    }

    @Test
    void cacheHitDoesNotReclone(@TempDir Path tmp) throws Exception {
        Path origin = createMonorepo(tmp.resolve("origin"));
        var cache = new PluginCacheManager(tmp.resolve("cache"));
        var fetcher = new GitSubdirSourceFetcher(cache);
        var src = new PluginSource.GitSubdir(origin.toUri().toString(), "main", "plugins/alpha");

        Path first = fetcher.fetch(src).block(Duration.ofSeconds(30));
        Path second = fetcher.fetch(src).block(Duration.ofSeconds(30));
        assertThat(second).isEqualTo(first);
    }

    /**
     * Creates a "monorepo": a git repo with two plugin trees under {@code plugins/alpha} and {@code
     * plugins/beta}.
     */
    private static Path createMonorepo(Path path) throws IOException {
        try {
            Files.createDirectories(path);
            try (Git git = Git.init().setDirectory(path.toFile()).setInitialBranch("main").call()) {
                Path alpha = path.resolve("plugins/alpha");
                Path beta = path.resolve("plugins/beta");
                Files.createDirectories(alpha.resolve(".kairo-plugin"));
                Files.createDirectories(beta.resolve(".kairo-plugin"));
                Files.writeString(
                        alpha.resolve(".kairo-plugin/plugin.json"),
                        "{\"name\":\"alpha\",\"version\":\"1.0.0\"}");
                Files.writeString(alpha.resolve("SKILL.md"), "alpha-skill");
                Files.writeString(
                        beta.resolve(".kairo-plugin/plugin.json"),
                        "{\"name\":\"beta\",\"version\":\"1.0.0\"}");
                Files.writeString(beta.resolve("SKILL.md"), "beta-skill");
                git.add().addFilepattern(".").call();
                git.commit()
                        .setMessage("init monorepo")
                        .setSign(false)
                        .setAuthor("test", "test@test")
                        .setCommitter("test", "test@test")
                        .call();
            }
            return path;
        } catch (Exception e) {
            throw new IOException("failed to set up monorepo", e);
        }
    }
}
