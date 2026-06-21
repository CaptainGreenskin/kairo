/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.core.tool;

import io.kairo.api.Experimental;
import io.kairo.api.tool.ToolDefinition;
import java.util.List;
import java.util.Set;

/**
 * Splits tools into core (schema always sent to model) and deferred (name-only announcement).
 *
 * <p>Core tools are high-frequency tools needed in almost every conversation. Deferred tools are
 * available via {@code search_tools} + {@code execute_tool} meta-tools. This reduces the
 * per-API-call tool schema cost from ~11,000 tokens to ~6,100 tokens (45% savings).
 */
@Experimental("Deferred tool loading; v0.13")
public final class DeferredToolFilter {

    private static final Set<String> CORE_TOOL_NAMES =
            Set.of(
                    "read",
                    "write",
                    "edit",
                    "glob",
                    "grep",
                    "batch_read",
                    "batch_write",
                    "diff",
                    "tree",
                    "bash",
                    "search_replace",
                    "todo_write",
                    "todo_read",
                    "task_create",
                    "task_list",
                    "task_update",
                    "task_get",
                    "enter_plan_mode",
                    "exit_plan_mode",
                    "search_tools",
                    "execute_tool",
                    "memory_read",
                    "memory_write");

    private DeferredToolFilter() {}

    public static boolean isCore(ToolDefinition tool) {
        return CORE_TOOL_NAMES.contains(tool.name());
    }

    public static boolean isDeferred(ToolDefinition tool) {
        return !isCore(tool);
    }

    public static List<ToolDefinition> coreOnly(List<ToolDefinition> tools) {
        return tools.stream().filter(DeferredToolFilter::isCore).toList();
    }

    public static List<ToolDefinition> deferredOnly(List<ToolDefinition> tools) {
        return tools.stream().filter(DeferredToolFilter::isDeferred).toList();
    }

    public static String deferredToolNames(List<ToolDefinition> tools) {
        return tools.stream()
                .filter(DeferredToolFilter::isDeferred)
                .map(t -> t.name() + " — " + t.description().split("\\.")[0])
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }
}
