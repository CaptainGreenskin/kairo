/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.tools.agent;

import io.kairo.api.tool.*;
import io.kairo.core.tool.DeferredToolFilter;
import java.util.Map;
import reactor.core.publisher.Mono;

@Tool(
        name = "execute_tool",
        description =
                "Execute a deferred tool by name. Use after search_tools returns the tool you need."
                        + " Pass the tool name and its parameters.",
        category = ToolCategory.GENERAL,
        sideEffect = ToolSideEffect.SYSTEM_CHANGE)
public class ExecuteDeferredTool implements SyncTool {

    @ToolParam(description = "Name of the deferred tool to execute")
    private String tool_name;

    @ToolParam(description = "Parameters to pass to the tool (JSON object)")
    private Map<String, Object> params;

    @Override
    @SuppressWarnings("unchecked")
    public Mono<ToolResult> execute(Map<String, Object> input, ToolContext ctx) {
        String toolName = (String) input.get("tool_name");
        if (toolName == null || toolName.isBlank()) {
            return Mono.just(ToolResult.error(ctx.agentId(), "Parameter 'tool_name' is required"));
        }

        ToolRegistry registry = (ToolRegistry) ctx.dependencies().get("toolRegistry");
        ToolExecutor executor = (ToolExecutor) ctx.dependencies().get("toolExecutor");

        if (registry == null || executor == null) {
            return Mono.just(
                    ToolResult.error(ctx.agentId(), "Tool registry/executor not available"));
        }

        var toolDef = registry.get(toolName);
        if (toolDef.isEmpty()) {
            return Mono.just(
                    ToolResult.error(
                            ctx.agentId(), "Tool '" + toolName + "' not found in registry"));
        }

        if (DeferredToolFilter.isCore(toolDef.get())) {
            return Mono.just(
                    ToolResult.error(
                            ctx.agentId(),
                            "Tool '"
                                    + toolName
                                    + "' is a core tool — call it directly, not via execute_tool"));
        }

        Map<String, Object> toolParams =
                input.get("params") instanceof Map
                        ? (Map<String, Object>) input.get("params")
                        : Map.of();

        return executor.execute(toolName, toolParams)
                .onErrorResume(
                        e ->
                                Mono.just(
                                        ToolResult.error(
                                                ctx.agentId(),
                                                "Failed to execute '"
                                                        + toolName
                                                        + "': "
                                                        + e.getMessage())));
    }
}
