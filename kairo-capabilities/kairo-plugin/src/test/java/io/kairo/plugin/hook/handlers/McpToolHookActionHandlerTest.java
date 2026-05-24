/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin.hook.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.plugin.PluginComponent.HookComponent.HookAction;
import io.kairo.api.tool.ToolResult;
import io.kairo.plugin.variable.PluginVariableResolver;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class McpToolHookActionHandlerTest {

    @Test
    void dispatchesServerAndToolToHostAdapter() {
        AtomicReference<String> capturedServer = new AtomicReference<>();
        AtomicReference<String> capturedTool = new AtomicReference<>();
        AtomicReference<Map<String, Object>> capturedInput = new AtomicReference<>();

        var handler =
                new McpToolHookActionHandler(
                        (server, tool, input) -> {
                            capturedServer.set(server);
                            capturedTool.set(tool);
                            capturedInput.set(input);
                            return Mono.just(ToolResult.success("call-1", "scan-clean"));
                        });

        var action =
                new HookAction(
                        "mcp_tool",
                        Map.of(
                                "server", "security",
                                "tool", "scan",
                                "input", Map.of("path", "/foo")));
        var result =
                handler.execute(action, Map.of("event", "PreToolUse"), null)
                        .block(Duration.ofSeconds(5));

        assertThat(capturedServer.get()).isEqualTo("security");
        assertThat(capturedTool.get()).isEqualTo("scan");
        // Event payload first, action input overrides on same key.
        assertThat(capturedInput.get()).containsEntry("event", "PreToolUse");
        assertThat(capturedInput.get()).containsEntry("path", "/foo");
        assertThat(result.exitCode()).isZero();
        assertThat(result.rawOutput()).isEqualTo("scan-clean");
    }

    @Test
    void resolvesVariablesInInputValues() {
        AtomicReference<Map<String, Object>> capturedInput = new AtomicReference<>();
        var handler =
                new McpToolHookActionHandler(
                        (server, tool, input) -> {
                            capturedInput.set(input);
                            return Mono.just(ToolResult.success("c", ""));
                        });

        var resolver = new PluginVariableResolver(Path.of("/plugin"), null, null);
        var action =
                new HookAction(
                        "mcp_tool",
                        Map.of(
                                "server", "x",
                                "tool", "y",
                                "input", Map.of("script", "${KAIRO_PLUGIN_ROOT}/run.sh")));
        handler.execute(action, Map.of(), resolver).block(Duration.ofSeconds(5));

        assertThat((String) capturedInput.get().get("script")).startsWith("/plugin");
    }

    @Test
    void toolErrorMapsToHookErrorExit() {
        var handler =
                new McpToolHookActionHandler(
                        (server, tool, input) -> Mono.just(ToolResult.error("c", "tool boomed")));
        var result =
                handler.execute(
                                new HookAction("mcp_tool", Map.of("server", "s", "tool", "t")),
                                Map.of(),
                                null)
                        .block(Duration.ofSeconds(5));
        assertThat(result.exitCode()).isNotZero();
        assertThat(result.rawOutput()).contains("tool boomed");
    }

    @Test
    void missingServerOrToolReturnsError() {
        var handler =
                new McpToolHookActionHandler(
                        (server, tool, input) -> Mono.just(ToolResult.success("c", "")));
        var result =
                handler.execute(new HookAction("mcp_tool", Map.of("server", "s")), Map.of(), null)
                        .block(Duration.ofSeconds(5));
        assertThat(result.errorOutput()).contains("'server' and 'tool'");
    }

    @Test
    void dispatcherErrorIsSurfacedAsHookError() {
        var handler =
                new McpToolHookActionHandler(
                        (server, tool, input) -> Mono.error(new RuntimeException("conn refused")));
        var result =
                handler.execute(
                                new HookAction("mcp_tool", Map.of("server", "s", "tool", "t")),
                                Map.of(),
                                null)
                        .block(Duration.ofSeconds(5));
        assertThat(result.errorOutput()).contains("conn refused");
    }

    @Test
    void typeIsMcpTool() {
        var handler =
                new McpToolHookActionHandler(
                        (server, tool, input) -> Mono.just(ToolResult.success("c", "")));
        assertThat(handler.type()).isEqualTo("mcp_tool");
    }
}
