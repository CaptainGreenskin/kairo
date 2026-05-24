/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.plugin.PluginInstallation;
import io.kairo.api.plugin.PluginScope;
import io.kairo.api.plugin.PluginSource;
import io.kairo.plugin.installer.PluginCacheManager;
import io.kairo.plugin.source.GitHubSourceFetcher;
import io.kairo.plugin.source.HttpDownloader;
import io.kairo.plugin.source.LocalPathSourceFetcher;
import io.kairo.plugin.source.SourceFetcherRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that DefaultPluginManager correctly dispatches PluginSource variants through the
 * SourceFetcherRegistry — the wiring point that Phase C.6 introduces. End-to-end install of a
 * GitHub-backed plugin via fake HTTP downloader (no network access).
 */
class PluginManagerSourceWiringTest {

    @Test
    void installFromGitHubSourceUsesGithubFetcher(@TempDir Path tmp) throws Exception {
        // Build the wiring chain with both LocalPath and GitHub fetchers.
        var cache = new PluginCacheManager(tmp.resolve("cache"));
        var fakeHttp =
                new FakeHttpDownloader(
                        Map.of(
                                "https://github.com/owner/repo/archive/main.tar.gz",
                                githubStyleTarball()));
        var fetchers =
                new SourceFetcherRegistry()
                        .register(new LocalPathSourceFetcher())
                        .register(new GitHubSourceFetcher(cache, fakeHttp));

        var pluginRegistry = new DefaultPluginRegistry();
        var loader = new PluginLoader();
        var manager =
                new DefaultPluginManager(
                        pluginRegistry,
                        loader,
                        tmp.resolve("data"),
                        ComponentRegistrar.noOp(),
                        fetchers);

        PluginInstallation installed =
                manager.install(
                                new PluginSource.GitHub("owner/repo", "main", null),
                                PluginScope.USER)
                        .block(Duration.ofSeconds(10));

        assertThat(installed).isNotNull();
        assertThat(installed.id()).startsWith("github:gh-fixture:");
        assertThat(installed.metadata().name()).isEqualTo("gh-fixture");
        assertThat(installed.source()).isInstanceOf(PluginSource.GitHub.class);
        assertThat(installed.rootPath()).exists();
        assertThat(installed.rootPath().resolve(".kairo-plugin/plugin.json")).exists();
    }

    @Test
    void installFromLocalPathStillWorks(@TempDir Path tmp) throws Exception {
        Path plugin = tmp.resolve("plug");
        java.nio.file.Files.createDirectories(plugin.resolve(".kairo-plugin"));
        java.nio.file.Files.writeString(
                plugin.resolve(".kairo-plugin/plugin.json"),
                "{\"name\":\"local\",\"version\":\"1.0.0\"}");

        var manager =
                new DefaultPluginManager(
                        new DefaultPluginRegistry(), new PluginLoader(), tmp.resolve("data"));

        PluginInstallation installed =
                manager.install(new PluginSource.LocalPath(plugin), PluginScope.LOCAL)
                        .block(Duration.ofSeconds(5));
        assertThat(installed.id()).startsWith("path:local:");
        assertThat(installed.scope()).isEqualTo(PluginScope.LOCAL);
    }

    @Test
    void idEncodesSourceTypeForDiagnostics(@TempDir Path tmp) throws Exception {
        var cache = new PluginCacheManager(tmp.resolve("cache"));
        var fakeHttp =
                new FakeHttpDownloader(
                        Map.of("https://github.com/o/r/archive/main.tar.gz", githubStyleTarball()));
        var fetchers =
                new SourceFetcherRegistry()
                        .register(new LocalPathSourceFetcher())
                        .register(new GitHubSourceFetcher(cache, fakeHttp));

        var manager =
                new DefaultPluginManager(
                        new DefaultPluginRegistry(),
                        new PluginLoader(),
                        tmp.resolve("data"),
                        ComponentRegistrar.noOp(),
                        fetchers);

        PluginInstallation gh =
                manager.install(new PluginSource.GitHub("o/r", "main", null), PluginScope.USER)
                        .block(Duration.ofSeconds(10));
        // id format: <source-type>:<plugin-name>:<uuid>
        assertThat(gh.id().split(":")[0]).isEqualTo("github");
    }

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

        FakeHttpDownloader(Map<String, byte[]> bodies) {
            this.bodies.putAll(bodies);
        }

        @Override
        public InputStream get(String url) throws IOException {
            byte[] bytes = bodies.get(url);
            if (bytes == null) throw new IOException("HTTP 404: " + url);
            return new ByteArrayInputStream(bytes);
        }
    }
}
