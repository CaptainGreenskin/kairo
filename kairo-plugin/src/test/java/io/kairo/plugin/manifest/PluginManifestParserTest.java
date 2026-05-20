/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.plugin.PluginMetadata;
import io.kairo.plugin.testsupport.Fixtures;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginManifestParserTest {

    private final PluginManifestParser parser = new PluginManifestParser();

    @Test
    void parsesMinimalPlugin() throws Exception {
        Path root = Fixtures.copyToTemp("minimal-plugin");
        PluginMetadata m = parser.parse(root);
        assertThat(m.name()).isEqualTo("minimal");
        assertThat(m.version()).isEqualTo("1.0.0");
        assertThat(m.description()).isEqualTo("Minimum viable plugin");
        assertThat(m.dependencies()).isEmpty();
        assertThat(m.keywords()).isEmpty();
    }

    @Test
    void parsesFullPlugin() throws Exception {
        Path root = Fixtures.copyToTemp("full-plugin");
        PluginMetadata m = parser.parse(root);
        assertThat(m.name()).isEqualTo("full-fixture");
        assertThat(m.version()).isEqualTo("1.2.3");
        assertThat(m.author()).isNotNull();
        assertThat(m.author().name()).isEqualTo("Kairo Team");
        assertThat(m.license()).isEqualTo("Apache-2.0");
        assertThat(m.homepage()).isEqualTo("https://kairo.io");
        assertThat(m.keywords()).containsExactly("test", "fixture");
    }

    @Test
    void readsMcpServers() throws Exception {
        Path root = Fixtures.copyToTemp("full-plugin");
        var servers = parser.readMcpServers(parser.locateManifest(root));
        assertThat(servers).containsKey("demo");
    }

    @Test
    void readsMcpServersFromExternalRelativeFile(@TempDir Path tmp) throws Exception {
        Path root = Files.createDirectory(tmp.resolve("ext-rel"));
        Files.createDirectories(root.resolve(".kairo-plugin"));
        Files.writeString(
                root.resolve(".kairo-plugin/plugin.json"),
                "{\"name\":\"ext-rel\",\"version\":\"1.0.0\","
                        + "\"mcpServers\":\"./external-mcp.json\"}");
        Files.writeString(
                root.resolve("external-mcp.json"),
                "{\"mcpServers\":{\"github\":{\"command\":\"node\",\"args\":[\"x.js\"]}}}");
        var servers = parser.readMcpServers(parser.locateManifest(root));
        assertThat(servers).containsKey("github");
    }

    @Test
    void readsMcpServersFromExternalFileWithBareTopLevel(@TempDir Path tmp) throws Exception {
        // Plugin author dropped the `mcpServers` wrapper in the external file — we tolerate it.
        Path root = Files.createDirectory(tmp.resolve("ext-bare"));
        Files.createDirectories(root.resolve(".kairo-plugin"));
        Files.writeString(
                root.resolve(".kairo-plugin/plugin.json"),
                "{\"name\":\"ext-bare\",\"version\":\"1.0.0\","
                        + "\"mcpServers\":\"./external-mcp.json\"}");
        Files.writeString(
                root.resolve("external-mcp.json"), "{\"weather\":{\"command\":\"weather-mcp\"}}");
        var servers = parser.readMcpServers(parser.locateManifest(root));
        assertThat(servers).containsKey("weather");
    }

    @Test
    void mcpServersStringPointingAtMissingFileThrows(@TempDir Path tmp) throws Exception {
        Path root = Files.createDirectory(tmp.resolve("ext-missing"));
        Files.createDirectories(root.resolve(".kairo-plugin"));
        Files.writeString(
                root.resolve(".kairo-plugin/plugin.json"),
                "{\"name\":\"ext-missing\",\"version\":\"1.0.0\","
                        + "\"mcpServers\":\"./does-not-exist.json\"}");
        assertThatThrownBy(() -> parser.readMcpServers(parser.locateManifest(root)))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("does-not-exist.json");
    }

    @Test
    void mcpServersStringEmptyIsTreatedAsAbsent(@TempDir Path tmp) throws Exception {
        Path root = Files.createDirectory(tmp.resolve("ext-empty"));
        Files.createDirectories(root.resolve(".kairo-plugin"));
        Files.writeString(
                root.resolve(".kairo-plugin/plugin.json"),
                "{\"name\":\"ext-empty\",\"version\":\"1.0.0\",\"mcpServers\":\"\"}");
        assertThat(parser.readMcpServers(parser.locateManifest(root))).isEmpty();
    }

    @Test
    void rejectsRangeVersion() throws Exception {
        Path root = Fixtures.copyToTemp("bad-version-plugin");
        assertThatThrownBy(() -> parser.parse(root))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-exact version");
    }

    @Test
    void synthesisesMinimalManifestWhenMissing(@TempDir Path tmp) throws Exception {
        Path empty = Files.createDirectory(tmp.resolve("empty-plugin"));
        PluginMetadata m = parser.parse(empty);
        assertThat(m.name()).isEqualTo("empty-plugin");
        assertThat(m.version()).isEqualTo("0.0.0");
    }

    @Test
    void readsKairoPluginDir(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("kairo-only");
        Files.createDirectories(root.resolve(".kairo-plugin"));
        Files.writeString(
                root.resolve(".kairo-plugin/plugin.json"),
                "{\"name\":\"kairo-only\",\"version\":\"1.0.0\"}");
        assertThat(parser.parse(root).name()).isEqualTo("kairo-only");
    }

    @Test
    void ignoresClaudePluginDir(@TempDir Path tmp) throws Exception {
        // A directory that still has Claude Code's .claude-plugin/ layout MUST be migrated to
        // .kairo-plugin/ before Kairo can load it. Plugin schema is shared; directory namespace
        // is not.
        Path root = tmp.resolve("claude-only");
        Files.createDirectories(root.resolve(".claude-plugin"));
        Files.writeString(
                root.resolve(".claude-plugin/plugin.json"),
                "{\"name\":\"should-be-ignored\",\"version\":\"1.0.0\"}");
        // No .kairo-plugin/ → falls through to synthesised manifest using the directory name.
        assertThat(parser.parse(root).name()).isEqualTo("claude-only");
    }
}
