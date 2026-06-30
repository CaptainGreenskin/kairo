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

    // Core tools: full schema sent to model every API call (~22 tools).
    // Selection criteria: used in >50% of conversations.
    // Everything else is deferred (name-only, discovered via search_tools).
    //
    // Removed from core (can be discovered on demand):
    //   batch_read, batch_write — read/write cover the same; saves ~400 tokens
    //   diff — only code-review scenarios
    //   search_replace — edit covers the same
    //   todo_read — model rarely reads todos explicitly
    //
    // Added to core (Claude Code has them, high frequency):
    //   web_fetch, web_search — research tasks
    //   ask_user — interactive clarification
    private static final Set<String> CORE_TOOL_NAMES =
            Set.of(
                    // File operations
                    "read",
                    "write",
                    "edit",
                    "glob",
                    "grep",
                    "tree",
                    "bash",
                    // Task & planning
                    "todo_write",
                    "task_create",
                    "task_list",
                    "task_update",
                    "task_get",
                    "enter_plan_mode",
                    "exit_plan_mode",
                    // Memory
                    "memory_read",
                    "memory_write",
                    // Web & interaction
                    "web_fetch",
                    "web_search",
                    "ask_user",
                    // Scheduling (agent should reach these directly — recurring/deferred tasks are
                    // a primary use case, not a rare lookup)
                    "CronCreate",
                    "CronList",
                    "CronTrigger",
                    // Meta (deferred tool system)
                    "search_tools",
                    "execute_tool");

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
