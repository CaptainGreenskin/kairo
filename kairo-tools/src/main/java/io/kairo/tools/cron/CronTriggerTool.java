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
        name = "CronTrigger",
        description =
                "Fire a cron job immediately, outside its normal schedule. The job's normal"
                        + " cadence is unchanged.",
        category = ToolCategory.SCHEDULING,
        sideEffect = ToolSideEffect.WRITE)
public class CronTriggerTool implements SyncTool {

    private final CronScheduler scheduler;

    public CronTriggerTool(CronScheduler scheduler) {
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
            return ToolResult.error("CronTrigger", "Parameter 'id' is required");
        }
        return scheduler.trigger(taskId)
                ? ToolResult.success("CronTrigger", "Triggered cron job " + taskId)
                : ToolResult.error("CronTrigger", "No cron job found with id '" + taskId + "'");
    }
}
