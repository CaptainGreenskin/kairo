/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin.mcp.testfixtures;

import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.api.tool.ToolInvocation;
import io.kairo.api.tool.ToolResult;
import io.kairo.mcp.KairoMcpServer;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Tiny in-test MCP server: launches via {@code main()}, speaks the real MCP JSON-RPC protocol over
 * stdio (using {@link KairoMcpServer} from kairo-mcp), and exposes a single {@code echo} tool. The
 * stdio MCP IT spawns a JVM running this class as the subprocess and verifies that {@code
 * DefaultMcpPlugin} can discover the tool through the actual production code path.
 *
 * <p>Why a Java fixture and not Node? Avoids the Node toolchain dependency for tests, and exercises
 * the same SDK both ends of the production wire actually use.
 */
public final class FakeMcpStdioServer {

    private FakeMcpStdioServer() {}

    public static void main(String[] args) throws Exception {
        JsonSchema messageProp = new JsonSchema("string", null, null, "text to echo back");
        JsonSchema inputSchema =
                new JsonSchema("object", Map.of("message", messageProp), List.of("message"), null);
        ToolDefinition echoTool =
                new ToolDefinition(
                        "echo",
                        "Echoes the provided 'message' argument back as text.",
                        ToolCategory.FILE_AND_CODE,
                        inputSchema,
                        FakeMcpStdioServer.class);

        ToolExecutor executor =
                new ToolExecutor() {
                    @Override
                    public Mono<ToolResult> execute(String toolName, Map<String, Object> input) {
                        Object msg = input == null ? null : input.get("message");
                        return Mono.just(
                                ToolResult.success(
                                        "fixture-call-" + System.nanoTime(),
                                        msg == null ? "" : msg.toString()));
                    }

                    @Override
                    public Mono<ToolResult> execute(
                            String toolName,
                            Map<String, Object> input,
                            java.time.Duration timeout) {
                        return execute(toolName, input);
                    }

                    @Override
                    public reactor.core.publisher.Flux<ToolResult> executeParallel(
                            List<ToolInvocation> invocations) {
                        return reactor.core.publisher.Flux.fromIterable(invocations)
                                .flatMap(this::executeSingle);
                    }
                };

        var server =
                KairoMcpServer.builder()
                        .serverName("fake-stdio")
                        .serverVersion("0.0.1")
                        .toolExecutor(executor)
                        .tools(List.of(echoTool))
                        .build();
        server.startStdio().block();

        // KairoMcpServer.startStdio binds to System.in/System.out via the SDK's stdio provider;
        // it returns immediately, but the SDK keeps the JVM alive on the transport's read loop.
        // Park indefinitely until the parent kills us.
        Thread.currentThread().join();
    }
}
