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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HookComponentLoaderTest {

    private final HookComponentLoader loader = new HookComponentLoader();

    @Test
    void loadsHooksFromFullPluginFixture() throws Exception {
        Path root = Fixtures.copyToTemp("full-plugin");
        List<PluginComponent.HookComponent> hooks = loader.load(root);
        // PreToolUse (1 binding, 2 actions) + PostToolUse (1 binding, 1 action) + Stop (1 binding,
        // 1)
        assertThat(hooks).hasSize(3);
        assertThat(hooks)
                .extracting(PluginComponent.HookComponent::event)
                .containsExactlyInAnyOrder("PreToolUse", "PostToolUse", "Stop");
    }

    @Test
    void preToolUseBindingHasMatcherAndTwoActions() throws Exception {
        Path root = Fixtures.copyToTemp("full-plugin");
        var preToolUse =
                loader.load(root).stream()
                        .filter(h -> h.event().equals("PreToolUse"))
                        .findFirst()
                        .orElseThrow();
        assertThat(preToolUse.matcher()).isEqualTo("Bash|Edit");
        assertThat(preToolUse.actions()).hasSize(2);
        assertThat(preToolUse.actions().get(0).type()).isEqualTo("command");
        assertThat(preToolUse.actions().get(1).type()).isEqualTo("http");
    }

    @Test
    void commandActionConfigCarriesAllFields() throws Exception {
        Path root = Fixtures.copyToTemp("full-plugin");
        var cmd =
                loader.load(root).stream()
                        .filter(h -> h.event().equals("PreToolUse"))
                        .findFirst()
                        .orElseThrow()
                        .actions()
                        .get(0);
        assertThat(cmd.config()).containsEntry("shell", "bash");
        assertThat(cmd.config()).containsKey("command");
        assertThat(cmd.config()).containsKey("args");
        assertThat(cmd.config()).containsEntry("timeout", 30);
    }

    @Test
    void httpActionConfigCarriesUrlAndHeaders() throws Exception {
        Path root = Fixtures.copyToTemp("full-plugin");
        var http =
                loader.load(root).stream()
                        .filter(h -> h.event().equals("PreToolUse"))
                        .findFirst()
                        .orElseThrow()
                        .actions()
                        .get(1);
        assertThat(http.type()).isEqualTo("http");
        assertThat(http.config()).containsEntry("url", "https://example.com/audit");
        assertThat(http.config().get("headers")).isInstanceOf(Map.class);
    }

    @Test
    void mcpToolActionPreservesServerAndTool() throws Exception {
        Path root = Fixtures.copyToTemp("full-plugin");
        var mcp =
                loader.load(root).stream()
                        .filter(h -> h.event().equals("PostToolUse"))
                        .findFirst()
                        .orElseThrow()
                        .actions()
                        .get(0);
        assertThat(mcp.type()).isEqualTo("mcp_tool");
        assertThat(mcp.config()).containsEntry("server", "demo");
        assertThat(mcp.config()).containsEntry("tool", "post_audit");
    }

    @Test
    void disableAllHooksReturnsEmptyList() throws Exception {
        Path root = Fixtures.copyToTemp("disabled-hooks-plugin");
        assertThat(loader.load(root)).isEmpty();
    }

    @Test
    void noHooksDirectoryReturnsEmpty(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("no-hooks");
        Files.createDirectories(root);
        assertThat(loader.load(root)).isEmpty();
    }

    @Test
    void noBindingMatcherIsNullable() throws Exception {
        Path root = Fixtures.copyToTemp("full-plugin");
        var stop =
                loader.load(root).stream()
                        .filter(h -> h.event().equals("Stop"))
                        .findFirst()
                        .orElseThrow();
        // Stop binding has no matcher field — must be allowed (null).
        assertThat(stop.matcher()).isNull();
        assertThat(stop.actions()).hasSize(1);
        assertThat(stop.actions().get(0).type()).isEqualTo("prompt");
    }

    @Test
    void rejectsActionWithBlankType(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("blank-type");
        Files.createDirectories(root.resolve("hooks"));
        Files.writeString(
                root.resolve("hooks/hooks.json"),
                "{\"hooks\":{\"PreToolUse\":[{\"hooks\":[{\"type\":\"\"}]}]}}");
        // Loader silently drops malformed actions; binding with no valid actions is dropped too.
        assertThat(loader.load(root)).isEmpty();
    }

    @Test
    void malformedJsonIsCaughtByCaller(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("bad-json");
        Files.createDirectories(root.resolve("hooks"));
        Files.writeString(root.resolve("hooks/hooks.json"), "{bad");
        try {
            loader.load(root);
        } catch (Exception e) {
            assertThat(e).isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
            return;
        }
        // If we reach here loader did not throw — fail.
        org.assertj.core.api.Assertions.fail("Expected JsonProcessingException");
    }
}
