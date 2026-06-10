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
import java.util.List;
import java.util.Map;

/**
 * Searches deferred tools by keyword. Returns matching tool names, descriptions, and input schemas
 * so the model can call them via {@code execute_tool}.
 */
@Tool(
        name = "search_tools",
        description =
                "Search for additional tools not in the main tool list. Use when you need a tool"
                        + " that isn't directly available (e.g., cron scheduling, web search, git,"
                        + " agent spawning). Returns tool name, description, and input schema.",
        category = ToolCategory.GENERAL,
        sideEffect = ToolSideEffect.READ_ONLY)
public class SearchToolsTool implements SyncTool {

    @ToolParam(description = "Search query — tool name or keyword (e.g. 'cron', 'web', 'git')")
    private String query;

    @Override
    public ToolResult execute(Map<String, Object> input, ToolContext ctx) {
        String q = ((String) input.getOrDefault("query", "")).toLowerCase().trim();
        if (q.isBlank()) {
            return ToolResult.error(ctx.agentId(), "Parameter 'query' is required");
        }

        ToolRegistry registry = (ToolRegistry) ctx.dependencies().get("toolRegistry");
        if (registry == null) {
            return ToolResult.error(ctx.agentId(), "Tool registry not available");
        }

        List<ToolDefinition> deferred = DeferredToolFilter.deferredOnly(registry.getAll());
        List<ToolDefinition> matches =
                deferred.stream()
                        .filter(
                                t ->
                                        t.name().toLowerCase().contains(q)
                                                || t.description().toLowerCase().contains(q)
                                                || t.category().name().toLowerCase().contains(q))
                        .toList();

        if (matches.isEmpty()) {
            return ToolResult.success(
                    ctx.agentId(),
                    "No deferred tools matching '"
                            + q
                            + "'. Available: "
                            + deferred.stream()
                                    .map(ToolDefinition::name)
                                    .reduce((a, b) -> a + ", " + b)
                                    .orElse("none"));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(matches.size()).append(" tool(s):\n\n");
        for (ToolDefinition t : matches) {
            sb.append("### ").append(t.name()).append("\n");
            sb.append(t.description()).append("\n");
            if (t.inputSchema() != null) {
                sb.append("Input: ").append(formatSchema(t.inputSchema())).append("\n");
            }
            sb.append("\nCall via: execute_tool({\"tool_name\": \"")
                    .append(t.name())
                    .append("\", \"params\": {...}})\n\n");
        }
        return ToolResult.success(ctx.agentId(), sb.toString());
    }

    private static String formatSchema(JsonSchema schema) {
        if (schema.properties() == null) return "{}";
        StringBuilder sb = new StringBuilder("{");
        schema.properties()
                .forEach(
                        (k, v) -> {
                            sb.append(k).append(": ").append(v.type());
                            if (v.description() != null)
                                sb.append(" (").append(v.description()).append(")");
                            sb.append(", ");
                        });
        if (sb.length() > 1) sb.setLength(sb.length() - 2);
        sb.append("}");
        return sb.toString();
    }
}
