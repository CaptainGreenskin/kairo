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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitHubSourceFetcherTest {

    @Test
    void buildsCorrectArchiveUrlForRefAndSha() {
        var withRef = new PluginSource.GitHub("anthropics/claude-plugins-official", "main", null);
        assertThat(GitHubSourceFetcher.archiveUrl(withRef))
                .isEqualTo(
                        "https://github.com/anthropics/claude-plugins-official/archive/main.tar.gz");

        var withSha = new PluginSource.GitHub("a/b", "main", "deadbeef");
        // sha takes precedence over ref
        assertThat(GitHubSourceFetcher.archiveUrl(withSha))
                .isEqualTo("https://github.com/a/b/archive/deadbeef.tar.gz");

        var noRef = new PluginSource.GitHub("a/b", null, null);
        assertThat(GitHubSourceFetcher.archiveUrl(noRef))
                .isEqualTo("https://github.com/a/b/archive/main.tar.gz");
    }

    @Test
    void identityKeyIncludesPin() {
        assertThat(GitHubSourceFetcher.identityKey(new PluginSource.GitHub("a/b", "v1.0", null)))
                .isEqualTo("a/b@v1.0");
        assertThat(GitHubSourceFetcher.identityKey(new PluginSource.GitHub("a/b", null, "abc123")))
                .isEqualTo("a/b@abc123");
    }

    @Test
    void fetchExtractsTarballIntoCacheSlot(@TempDir Path tmp) throws Exception {
        var cache = new PluginCacheManager(tmp.resolve("cache"));
        var fakeHttp =
                new FakeHttpDownloader(
                        Map.of(
                                "https://github.com/owner/repo/archive/main.tar.gz",
                                githubStyleTarball()));
        var fetcher = new GitHubSourceFetcher(cache, fakeHttp);

        Path root =
                fetcher.fetch(new PluginSource.GitHub("owner/repo", "main", null))
                        .block(Duration.ofSeconds(5));

        assertThat(root).isNotNull();
        assertThat(root.resolve(".kairo-plugin/plugin.json")).exists();
        assertThat(root.resolve("SKILL.md")).hasContent("body");
        // Wrapper dir not present after stripComponents=1.
        assertThat(root.resolve("repo-main")).doesNotExist();
    }

    @Test
    void cacheHitSkipsHttpCallOnSecondFetch(@TempDir Path tmp) throws Exception {
        var cache = new PluginCacheManager(tmp.resolve("cache"));
        var fakeHttp =
                new FakeHttpDownloader(
                        Map.of("https://github.com/o/r/archive/main.tar.gz", githubStyleTarball()));
        var fetcher = new GitHubSourceFetcher(cache, fakeHttp);
        var src = new PluginSource.GitHub("o/r", "main", null);

        fetcher.fetch(src).block(Duration.ofSeconds(5));
        fetcher.fetch(src).block(Duration.ofSeconds(5));

        assertThat(fakeHttp.calls.get()).as("second call must not re-download").isEqualTo(1);
    }

    @Test
    void httpErrorPropagatesAsFailureMono(@TempDir Path tmp) throws Exception {
        var cache = new PluginCacheManager(tmp.resolve("cache"));
        var fakeHttp = new FakeHttpDownloader(Map.of()); // no entries → IOException
        var fetcher = new GitHubSourceFetcher(cache, fakeHttp);
        assertThatThrownBy(
                        () ->
                                fetcher.fetch(new PluginSource.GitHub("nope/nope", null, null))
                                        .block(Duration.ofSeconds(5)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("404");
    }

    @Test
    void rejectsWrongSourceType(@TempDir Path tmp) {
        var fetcher =
                new GitHubSourceFetcher(
                        new PluginCacheManager(tmp.resolve("cache")),
                        new FakeHttpDownloader(Map.of()));
        assertThatThrownBy(
                        () ->
                                fetcher.fetch(new PluginSource.LocalPath(Path.of("/tmp")))
                                        .block(Duration.ofSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void supportsAndKindReportCorrectly(@TempDir Path tmp) {
        var fetcher =
                new GitHubSourceFetcher(
                        new PluginCacheManager(tmp.resolve("cache")),
                        new FakeHttpDownloader(Map.of()));
        assertThat(fetcher.kind()).isEqualTo("github");
        assertThat(fetcher.supports(new PluginSource.GitHub("a/b", null, null))).isTrue();
        assertThat(fetcher.supports(new PluginSource.LocalPath(Path.of("/tmp")))).isFalse();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** GitHub-style tarball: a single {repo}-{ref}/ wrapper containing plugin tree. */
    private static byte[] githubStyleTarball() throws IOException {
        var baos = new ByteArrayOutputStream();
        try (var gz = new GZIPOutputStream(baos);
                var tar = new TarArchiveOutputStream(gz)) {
            putDir(tar, "repo-main/");
            putDir(tar, "repo-main/.kairo-plugin/");
            putFile(
                    tar,
                    "repo-main/.kairo-plugin/plugin.json",
                    "{\"name\":\"gh-fixture\",\"version\":\"1.0.0\"}");
            putFile(tar, "repo-main/SKILL.md", "body");
        }
        return baos.toByteArray();
    }

    private static void putDir(TarArchiveOutputStream tar, String path) throws IOException {
        TarArchiveEntry e = new TarArchiveEntry(path);
        e.setMode(0755);
        tar.putArchiveEntry(e);
        tar.closeArchiveEntry();
    }

    private static void putFile(TarArchiveOutputStream tar, String path, String content)
            throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        TarArchiveEntry e = new TarArchiveEntry(path);
        e.setMode(0644);
        e.setSize(bytes.length);
        tar.putArchiveEntry(e);
        tar.write(bytes);
        tar.closeArchiveEntry();
    }

    private static final class FakeHttpDownloader implements HttpDownloader {
        final Map<String, byte[]> bodies = new HashMap<>();
        final AtomicInteger calls = new AtomicInteger(0);

        FakeHttpDownloader(Map<String, byte[]> bodies) {
            this.bodies.putAll(bodies);
        }

        @Override
        public InputStream get(String url) throws IOException {
            calls.incrementAndGet();
            byte[] bytes = bodies.get(url);
            if (bytes == null) throw new IOException("HTTP 404 fetching " + url);
            return new ByteArrayInputStream(bytes);
        }
    }
}
