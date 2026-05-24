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

import io.kairo.api.plugin.MarketplaceCatalog;
import io.kairo.api.plugin.MarketplaceEntry;
import io.kairo.api.plugin.PluginInstallation;
import io.kairo.api.plugin.PluginScope;
import io.kairo.api.plugin.PluginSource;
import io.kairo.plugin.installer.PluginCacheManager;
import io.kairo.plugin.manifest.MarketplaceParser;
import io.kairo.plugin.source.GitHubSourceFetcher;
import io.kairo.plugin.source.HttpDownloader;
import io.kairo.plugin.source.LocalPathSourceFetcher;
import io.kairo.plugin.source.SourceFetcherRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
 * End-to-end Phase C integration: parse a marketplace.json that lists multiple plugins, then
 * install each one through its declared source, and verify all of them reach the plugin registry.
 *
 * <p>Uses a fake HttpDownloader so no network access is needed; mixes a LocalPath plugin (resolved
 * relative to the marketplace file) with a GitHub plugin (resolved via the public archive URL).
 */
class PhaseCIntegrationTest {

    @Test
    void marketplaceWithMixedSourcesInstallsAllListedPlugins(@TempDir Path tmp) throws Exception {
        // 1. Lay out a local marketplace directory: marketplace.json + a sibling plugins/local-pkg/
        Path mpDir = Files.createDirectories(tmp.resolve("marketplace-root"));
        Path localPkg = mpDir.resolve("plugins/local-pkg");
        Files.createDirectories(localPkg.resolve(".kairo-plugin"));
        Files.writeString(
                localPkg.resolve(".kairo-plugin/plugin.json"),
                "{\"name\":\"local-pkg\",\"version\":\"1.0.0\"}");

        Path marketplaceFile = mpDir.resolve("marketplace.json");
        Files.writeString(
                marketplaceFile,
                """
                {
                  "name": "kairo-test-marketplace",
                  "owner": { "name": "kairo-team" },
                  "trustLevel": "official",
                  "plugins": [
                    { "name": "from-local", "source": "./plugins/local-pkg" },
                    { "name": "from-github", "source": { "github": "owner/repo", "ref": "v1.0" } }
                  ]
                }
                """);

        // 2. Parse marketplace and build the install pipeline.
        MarketplaceCatalog catalog = new MarketplaceParser().parse(marketplaceFile);
        assertThat(catalog.name()).isEqualTo("kairo-test-marketplace");
        assertThat(catalog.trustLevel()).isEqualTo("official");
        assertThat(catalog.plugins()).hasSize(2);

        var cache = new PluginCacheManager(tmp.resolve("cache"));
        var fakeHttp =
                new FakeHttpDownloader(
                        Map.of(
                                "https://github.com/owner/repo/archive/v1.0.tar.gz",
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

        // 3. Install every entry from the catalog.
        var installations = new java.util.ArrayList<PluginInstallation>();
        for (MarketplaceEntry entry : catalog.plugins()) {
            PluginInstallation inst =
                    manager.install(entry.source(), PluginScope.USER).block(Duration.ofSeconds(15));
            assertThat(inst).isNotNull();
            installations.add(inst);
        }

        // 4. Verify both installed plugins are visible in the registry with the right source types.
        assertThat(pluginRegistry.list()).hasSize(2);
        assertThat(installations)
                .extracting(i -> i.source().type())
                .containsExactlyInAnyOrder("path", "github");

        // 5. Verify the LocalPath one points at the actual on-disk plugin tree.
        PluginInstallation local =
                installations.stream()
                        .filter(i -> i.source() instanceof PluginSource.LocalPath)
                        .findFirst()
                        .orElseThrow();
        assertThat(local.metadata().name()).isEqualTo("local-pkg");
        assertThat(local.rootPath().resolve(".kairo-plugin/plugin.json")).exists();

        // 6. Verify the GitHub one was fetched into the cache and points there.
        PluginInstallation gh =
                installations.stream()
                        .filter(i -> i.source() instanceof PluginSource.GitHub)
                        .findFirst()
                        .orElseThrow();
        assertThat(gh.metadata().name()).isEqualTo("gh-fixture");
        assertThat(gh.rootPath().toString())
                .as("github plugin must live in the cache slot")
                .contains("cache");
    }

    @Test
    void enableLifecycleWorksForGithubInstalledPlugin(@TempDir Path tmp) throws Exception {
        // Parallel sanity check: install via marketplace + enable both end up consistent.
        Path mpDir = Files.createDirectories(tmp.resolve("mp"));
        Path marketplaceFile = mpDir.resolve("marketplace.json");
        Files.writeString(
                marketplaceFile,
                """
                { "name": "x", "owner": "y", "plugins": [
                  { "name": "p", "source": { "github": "o/r", "ref": "main" } }
                ] }
                """);
        var catalog = new MarketplaceParser().parse(marketplaceFile);

        var cache = new PluginCacheManager(tmp.resolve("cache"));
        var fakeHttp =
                new FakeHttpDownloader(
                        Map.of("https://github.com/o/r/archive/main.tar.gz", githubStyleTarball()));
        var fetchers =
                new SourceFetcherRegistry().register(new GitHubSourceFetcher(cache, fakeHttp));

        var pluginRegistry = new DefaultPluginRegistry();
        var manager =
                new DefaultPluginManager(
                        pluginRegistry,
                        new PluginLoader(),
                        tmp.resolve("data"),
                        ComponentRegistrar.noOp(),
                        fetchers);

        PluginInstallation inst =
                manager.install(catalog.plugins().get(0).source(), PluginScope.USER)
                        .block(Duration.ofSeconds(15));
        manager.enable(inst.id()).block(Duration.ofSeconds(10));

        assertThat(pluginRegistry.get(inst.id()))
                .isPresent()
                .map(PluginInstallation::enabled)
                .contains(true);

        manager.disable(inst.id()).block(Duration.ofSeconds(5));
        manager.uninstall(inst.id()).block(Duration.ofSeconds(5));
        assertThat(pluginRegistry.list()).isEmpty();
    }

    // ── tarball helper ──────────────────────────────────────────────────────

    private static byte[] githubStyleTarball() throws IOException {
        var baos = new ByteArrayOutputStream();
        try (var gz = new GZIPOutputStream(baos);
                var tar = new TarArchiveOutputStream(gz)) {
            putDir(tar, "repo-v1.0/");
            putDir(tar, "repo-v1.0/.kairo-plugin/");
            putFile(
                    tar,
                    "repo-v1.0/.kairo-plugin/plugin.json",
                    "{\"name\":\"gh-fixture\",\"version\":\"1.0.0\"}");
            putFile(tar, "repo-v1.0/SKILL.md", "body");
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
