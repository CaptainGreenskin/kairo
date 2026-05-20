/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin.component;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.plugin.PluginComponent;
import io.kairo.plugin.testsupport.Fixtures;
import io.kairo.plugin.variable.PluginVariableResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class McpComponentLoaderTest {

    private final McpComponentLoader loader = new McpComponentLoader();

    @Test
    void loadsFromInlineManifestServers(@TempDir Path tmp) throws Exception {
        Path root = Files.createDirectories(tmp.resolve("inline"));
        var inline =
                Map.<String, Object>of(
                        "demo",
                        Map.of(
                                "command", "/usr/bin/server",
                                "args", List.of("--stdio"),
                                "env", Map.of("X", "1")));
        var components = loader.load(root, inline, null);
        assertThat(components).hasSize(1);
        var demo = components.get(0);
        assertThat(demo.serverName()).isEqualTo("demo");
        assertThat(demo.command()).isEqualTo("/usr/bin/server");
        assertThat(demo.args()).containsExactly("--stdio");
        assertThat(demo.env()).containsEntry("X", "1");
    }

    @Test
    void loadsFromMcpJsonFile() throws Exception {
        Path root = Fixtures.copyToTemp("full-plugin");
        var resolver = new PluginVariableResolver(root, null, null);
        var components = loader.load(root, Map.of(), resolver);
        assertThat(components).hasSize(1);
        var secondary = components.get(0);
        assertThat(secondary.serverName()).isEqualTo("secondary");
        assertThat(secondary.command())
                .startsWith(root.toAbsolutePath().toString())
                .endsWith("/bin/extra-mcp");
        assertThat(secondary.args()).containsExactly("--stdio");
        assertThat(secondary.env()).containsEntry("DEBUG", "1");
    }

    @Test
    void mergesInlineWithMcpJsonInlineWins() throws Exception {
        Path root = Fixtures.copyToTemp("full-plugin");
        // Inline overrides the .mcp.json secondary entry.
        var inline =
                Map.<String, Object>of(
                        "secondary", Map.of("command", "/inline-wins", "args", List.of()));
        var components = loader.load(root, inline, null);
        var secondary =
                components.stream()
                        .filter(c -> c.serverName().equals("secondary"))
                        .findFirst()
                        .orElseThrow();
        assertThat(secondary.command()).isEqualTo("/inline-wins");
    }

    @Test
    void readsManifestMcpServersField(@TempDir Path tmp) throws Exception {
        // full-plugin manifest declares a "demo" mcpServer; we can pass it in directly.
        Path root = Fixtures.copyToTemp("full-plugin");
        var inline =
                Map.<String, Object>of("demo", Map.of("command", "echo", "args", List.of("hi")));
        var components = loader.load(root, inline, null);
        assertThat(components)
                .extracting(PluginComponent.McpComponent::serverName)
                .contains("demo", "secondary");
    }

    @Test
    void noMcpJsonAndNoInlineReturnsEmpty(@TempDir Path tmp) throws Exception {
        Path root = Files.createDirectories(tmp.resolve("empty"));
        assertThat(loader.load(root, Map.of(), null)).isEmpty();
    }

    @Test
    void rejectsEntryMissingCommand(@TempDir Path tmp) throws Exception {
        Path root = Files.createDirectories(tmp.resolve("malformed"));
        var inline = Map.<String, Object>of("bad", Map.of("args", List.of("--no-cmd")));
        assertThat(loader.load(root, inline, null)).isEmpty();
    }

    @Test
    void resolvesVariablesInCommandAndArgs(@TempDir Path tmp) throws Exception {
        Path root = Files.createDirectories(tmp.resolve("vars"));
        var resolver = new PluginVariableResolver(root, null, null);
        var inline =
                Map.<String, Object>of(
                        "demo",
                        Map.of(
                                "command",
                                "${KAIRO_PLUGIN_ROOT}/bin/server",
                                "args",
                                List.of("--config=${KAIRO_PLUGIN_ROOT}/cfg.json")));
        var components = loader.load(root, inline, resolver);
        assertThat(components.get(0).command()).startsWith(root.toAbsolutePath().toString());
        assertThat(components.get(0).args().get(0)).startsWith("--config=" + root.toAbsolutePath());
    }

    @Test
    void resolvesEnvValues(@TempDir Path tmp) throws Exception {
        Path root = Files.createDirectories(tmp.resolve("envvars"));
        var resolver = new PluginVariableResolver(root, null, null).with("FOO", "bar");
        var inline =
                Map.<String, Object>of(
                        "demo",
                        Map.of(
                                "command",
                                "/bin/echo",
                                "env",
                                Map.of("FOO_FILE", "${KAIRO_PLUGIN_ROOT}/foo")));
        var components = loader.load(root, inline, resolver);
        assertThat(components.get(0).env().get("FOO_FILE"))
                .startsWith(root.toAbsolutePath().toString());
    }

    @Test
    void loadsStreamableHttpFromUrlEntry(@TempDir Path tmp) throws Exception {
        Path root = Files.createDirectories(tmp.resolve("http"));
        // No explicit "type" → defaults to STREAMABLE_HTTP.
        var inline =
                Map.<String, Object>of(
                        "remote",
                        Map.of(
                                "url",
                                "https://api.example.com/mcp",
                                "headers",
                                Map.of("Authorization", "Bearer xyz")));
        var components = loader.load(root, inline, null);
        assertThat(components).hasSize(1);
        var remote = components.get(0);
        assertThat(remote.transport())
                .isEqualTo(PluginComponent.McpComponent.Transport.STREAMABLE_HTTP);
        assertThat(remote.url()).isEqualTo("https://api.example.com/mcp");
        assertThat(remote.headers()).containsEntry("Authorization", "Bearer xyz");
        assertThat(remote.command()).isEmpty();
        assertThat(remote.args()).isEmpty();
    }

    @Test
    void loadsSseWhenTypeIsSse(@TempDir Path tmp) throws Exception {
        Path root = Files.createDirectories(tmp.resolve("sse"));
        var inline =
                Map.<String, Object>of(
                        "events",
                        Map.of(
                                "type", "sse",
                                "url", "https://api.example.com/events"));
        var components = loader.load(root, inline, null);
        assertThat(components.get(0).transport())
                .isEqualTo(PluginComponent.McpComponent.Transport.SSE);
        assertThat(components.get(0).url()).isEqualTo("https://api.example.com/events");
    }

    @Test
    void loadsStreamableHttpWhenTypeExplicit(@TempDir Path tmp) throws Exception {
        Path root = Files.createDirectories(tmp.resolve("explicit-http"));
        var inline =
                Map.<String, Object>of(
                        "remote",
                        Map.of(
                                "type", "http",
                                "url", "https://api.example.com/mcp"));
        assertThat(loader.load(root, inline, null).get(0).transport())
                .isEqualTo(PluginComponent.McpComponent.Transport.STREAMABLE_HTTP);
    }

    @Test
    void resolvesVariablesInUrlAndHeaders(@TempDir Path tmp) throws Exception {
        Path root = Files.createDirectories(tmp.resolve("vars-url"));
        var resolver = new PluginVariableResolver(root, null, null);
        var inline =
                Map.<String, Object>of(
                        "remote",
                        Map.of(
                                "url",
                                "https://api.example.com/${KAIRO_PLUGIN_ROOT}",
                                "headers",
                                Map.of("X-Plugin-Path", "${KAIRO_PLUGIN_ROOT}")));
        var c = loader.load(root, inline, resolver).get(0);
        assertThat(c.url()).contains(root.toAbsolutePath().toString());
        assertThat(c.headers().get("X-Plugin-Path")).isEqualTo(root.toAbsolutePath().toString());
    }

    @Test
    void jsonNodeFromMcpJsonGetsConvertedProperly() throws Exception {
        // Sanity: ensure JsonNode → Map conversion path in parseEntry handles nested structures
        Path root = Fixtures.copyToTemp("full-plugin");
        var components = loader.load(root, Map.of(), null);
        // .mcp.json provides "secondary" with all fields.
        var secondary =
                components.stream()
                        .filter(c -> c.serverName().equals("secondary"))
                        .findFirst()
                        .orElseThrow();
        assertThat(secondary.args()).isNotEmpty();
        assertThat(secondary.env()).isNotEmpty();
    }
}
