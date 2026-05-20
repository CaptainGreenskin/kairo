/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.tools.cron;

import io.kairo.api.cron.CronScheduler;
import io.kairo.api.tool.SyncTool;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import java.util.Map;
import reactor.core.publisher.Mono;

@Tool(
        name = "CronPause",
        description =
                "Pause a cron job by ID. The job stays installed but won't fire on subsequent"
                        + " ticks until resumed via CronResume. Idempotent.",
        category = ToolCategory.SCHEDULING,
        sideEffect = ToolSideEffect.WRITE)
public class CronPauseTool implements SyncTool {

    private final CronScheduler scheduler;

    public CronPauseTool(CronScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @ToolParam(description = "Job ID returned by CronCreate", required = true)
    private String id;

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        return Mono.fromCallable(() -> doExecute(args));
    }

    private ToolResult doExecute(Map<String, Object> args) {
        String taskId = (String) args.get("id");
        if (taskId == null || taskId.isBlank()) {
            return ToolResult.error("CronPause", "Parameter 'id' is required");
        }
        return scheduler
                .pause(taskId)
                .map(t -> ToolResult.success("CronPause", "Paused cron job " + t.id()))
                .orElseGet(
                        () ->
                                ToolResult.error(
                                        "CronPause", "No cron job found with id '" + taskId + "'"));
    }
}
