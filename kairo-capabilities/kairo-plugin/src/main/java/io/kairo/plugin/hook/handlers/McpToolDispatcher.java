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

import io.kairo.api.tool.ToolResult;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Adapter the host application provides so {@link McpToolHookActionHandler} can call MCP tools
 * without kairo-plugin knowing the host's MCP registration scheme.
 *
 * <p>Implementations typically wrap a {@code McpClientRegistry} or {@code McpPluginRegistration}
 * lookup, locate the tool by {@code (server, tool)}, and invoke it with the supplied input.
 */
@FunctionalInterface
public interface McpToolDispatcher {

    /**
     * Calls the named tool on the named MCP server.
     *
     * @param serverName matches {@code McpServerConfig.name()} / the {@code server} field in {@code
     *     hooks.json}
     * @param toolName the MCP tool's name as exposed by the server (no kairo prefix needed)
     * @param input merged arguments for the tool call
     * @return Mono emitting the tool result; should error with a clear message when the server or
     *     tool is unknown
     */
    Mono<ToolResult> dispatch(String serverName, String toolName, Map<String, Object> input);
}
