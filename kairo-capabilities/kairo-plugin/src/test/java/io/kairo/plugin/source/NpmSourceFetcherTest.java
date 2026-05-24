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
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NpmSourceFetcherTest {

    @Test
    void identityKeyIsPackageAtVersion() {
        var npm = new PluginSource.Npm("foo", "1.2.3", null);
        assertThat(NpmSourceFetcher.identityKey(npm)).isEqualTo("foo@1.2.3");
    }

    @Test
    void encodesScopedPackageNames() {
        assertThat(NpmSourceFetcher.encodePackageName("foo")).isEqualTo("foo");
        assertThat(NpmSourceFetcher.encodePackageName("@scope/pkg")).isEqualTo("%40scope%2Fpkg");
    }

    @Test
    void fetchExtractsAndVerifiesShasum(@TempDir Path tmp) throws Exception {
        byte[] tarball = npmStyleTarball();
        String shasum = sha1Hex(tarball);

        var cache = new PluginCacheManager(tmp.resolve("cache"));
        var fakeHttp =
                new FakeHttpDownloader(
                        Map.of(
                                "https://registry.npmjs.org/myplugin",
                                metadataJson(
                                                shasum,
                                                "https://registry.npmjs.org/myplugin/-/myplugin-1.0.0.tgz")
                                        .getBytes(StandardCharsets.UTF_8),
                                "https://registry.npmjs.org/myplugin/-/myplugin-1.0.0.tgz",
                                tarball));
        var fetcher = new NpmSourceFetcher(cache, fakeHttp);

        Path root =
                fetcher.fetch(new PluginSource.Npm("myplugin", "1.0.0", null))
                        .block(Duration.ofSeconds(10));

        assertThat(root).isNotNull();
        assertThat(root.resolve(".kairo-plugin/plugin.json")).exists();
        assertThat(root.resolve("SKILL.md")).hasContent("npm-plugin-body");
    }

    @Test
    void shasumMismatchIsRejected(@TempDir Path tmp) throws Exception {
        byte[] tarball = npmStyleTarball();
        var cache = new PluginCacheManager(tmp.resolve("cache"));
        var fakeHttp =
                new FakeHttpDownloader(
                        Map.of(
                                "https://registry.npmjs.org/myplugin",
                                metadataJson(
                                                "0000000000000000000000000000000000000000",
                                                "https://registry.npmjs.org/myplugin/-/myplugin-1.0.0.tgz")
                                        .getBytes(StandardCharsets.UTF_8),
                                "https://registry.npmjs.org/myplugin/-/myplugin-1.0.0.tgz",
                                tarball));
        var fetcher = new NpmSourceFetcher(cache, fakeHttp);

        assertThatThrownBy(
                        () ->
                                fetcher.fetch(new PluginSource.Npm("myplugin", "1.0.0", null))
                                        .block(Duration.ofSeconds(10)))
                .hasMessageContaining("SHA-1 mismatch");
    }

    @Test
    void unknownVersionReportsClearError(@TempDir Path tmp) throws Exception {
        byte[] tarball = npmStyleTarball();
        String shasum = sha1Hex(tarball);
        var cache = new PluginCacheManager(tmp.resolve("cache"));
        var fakeHttp =
                new FakeHttpDownloader(
                        Map.of(
                                "https://registry.npmjs.org/myplugin",
                                metadataJson(shasum, "https://x/foo.tgz")
                                        .getBytes(StandardCharsets.UTF_8)));
        var fetcher = new NpmSourceFetcher(cache, fakeHttp);

        assertThatThrownBy(
                        () ->
                                fetcher.fetch(new PluginSource.Npm("myplugin", "9.9.9", null))
                                        .block(Duration.ofSeconds(5)))
                .hasMessageContaining("not found");
    }

    @Test
    void cacheHitSkipsRegistryAndDownload(@TempDir Path tmp) throws Exception {
        byte[] tarball = npmStyleTarball();
        String shasum = sha1Hex(tarball);
        var cache = new PluginCacheManager(tmp.resolve("cache"));
        var fakeHttp =
                new FakeHttpDownloader(
                        Map.of(
                                "https://registry.npmjs.org/p",
                                metadataJson(shasum, "https://x/p.tgz")
                                        .getBytes(StandardCharsets.UTF_8),
                                "https://x/p.tgz",
                                tarball));
        var fetcher = new NpmSourceFetcher(cache, fakeHttp);
        var src = new PluginSource.Npm("p", "1.0.0", null);

        fetcher.fetch(src).block(Duration.ofSeconds(10));
        fetcher.fetch(src).block(Duration.ofSeconds(10));
        // First fetch: 2 calls (metadata + tarball). Second fetch: 0 calls.
        assertThat(fakeHttp.callCount).isEqualTo(2);
    }

    @Test
    void rejectsBlankPackage(@TempDir Path tmp) {
        var fetcher =
                new NpmSourceFetcher(
                        new PluginCacheManager(tmp.resolve("c")), new FakeHttpDownloader(Map.of()));
        assertThatThrownBy(
                        () ->
                                fetcher.fetch(new PluginSource.Npm("  ", "1.0.0", null))
                                        .block(Duration.ofSeconds(2)))
                .hasMessageContaining("packageName");
    }

    @Test
    void rejectsBlankVersion(@TempDir Path tmp) {
        var fetcher =
                new NpmSourceFetcher(
                        new PluginCacheManager(tmp.resolve("c")), new FakeHttpDownloader(Map.of()));
        assertThatThrownBy(
                        () ->
                                fetcher.fetch(new PluginSource.Npm("p", null, null))
                                        .block(Duration.ofSeconds(2)))
                .hasMessageContaining("version");
    }

    @Test
    void supportsAndKindReportCorrectly(@TempDir Path tmp) {
        var fetcher =
                new NpmSourceFetcher(
                        new PluginCacheManager(tmp.resolve("c")), new FakeHttpDownloader(Map.of()));
        assertThat(fetcher.kind()).isEqualTo("npm");
        assertThat(fetcher.supports(new PluginSource.Npm("p", "1.0.0", null))).isTrue();
        assertThat(fetcher.supports(new PluginSource.GitHub("a/b", null, null))).isFalse();
    }

    @Test
    void customRegistryBaseUrlIsRespected(@TempDir Path tmp) throws Exception {
        byte[] tarball = npmStyleTarball();
        String shasum = sha1Hex(tarball);
        var cache = new PluginCacheManager(tmp.resolve("cache"));
        var fakeHttp =
                new FakeHttpDownloader(
                        Map.of(
                                "https://private.example.com/p",
                                metadataJson(shasum, "https://private.example.com/p.tgz")
                                        .getBytes(StandardCharsets.UTF_8),
                                "https://private.example.com/p.tgz",
                                tarball));
        var fetcher = new NpmSourceFetcher(cache, fakeHttp, "https://private.example.com");
        Path root =
                fetcher.fetch(new PluginSource.Npm("p", "1.0.0", null))
                        .block(Duration.ofSeconds(10));
        assertThat(root.resolve(".kairo-plugin/plugin.json")).exists();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static String metadataJson(String shasum, String tarballUrl) {
        return """
                {
                  "name": "myplugin",
                  "versions": {
                    "1.0.0": {
                      "dist": {
                        "tarball": "%s",
                        "shasum": "%s"
                      }
                    }
                  }
                }
                """
                .formatted(tarballUrl, shasum);
    }

    /** npm-style tarball: content is wrapped in package/. */
    private static byte[] npmStyleTarball() throws IOException {
        var baos = new ByteArrayOutputStream();
        try (var gz = new GZIPOutputStream(baos);
                var tar = new TarArchiveOutputStream(gz)) {
            putDir(tar, "package/");
            putDir(tar, "package/.kairo-plugin/");
            putFile(
                    tar,
                    "package/.kairo-plugin/plugin.json",
                    "{\"name\":\"npm-fixture\",\"version\":\"1.0.0\"}");
            putFile(tar, "package/SKILL.md", "npm-plugin-body");
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

    private static String sha1Hex(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
    }

    private static final class FakeHttpDownloader implements HttpDownloader {
        final Map<String, byte[]> bodies = new HashMap<>();
        int callCount = 0;

        FakeHttpDownloader(Map<String, byte[]> bodies) {
            this.bodies.putAll(bodies);
        }

        @Override
        public InputStream get(String url) throws IOException {
            callCount++;
            byte[] bytes = bodies.get(url);
            if (bytes == null) throw new IOException("HTTP 404 fetching " + url);
            return new ByteArrayInputStream(bytes);
        }
    }
}
