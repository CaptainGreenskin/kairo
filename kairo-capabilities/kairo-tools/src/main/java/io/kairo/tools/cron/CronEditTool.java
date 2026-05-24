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
        name = "CronEdit",
        description =
                "Edit an existing cron job's schedule and/or prompt. Pass at least one of"
                        + " 'cron' or 'prompt'. The new schedule accepts 5-field cron"
                        + " expressions or Hermes-style 'every Nm/Nh/Nd' / 'every 1d at HH:MM'.",
        category = ToolCategory.SCHEDULING,
        sideEffect = ToolSideEffect.WRITE)
public class CronEditTool implements SyncTool {

    private final CronScheduler scheduler;

    public CronEditTool(CronScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @ToolParam(description = "Job ID returned by CronCreate", required = true)
    private String id;

    @ToolParam(description = "New schedule (cron or 'every Nm/Nh/Nd ...'); leave blank to keep")
    private String cron;

    @ToolParam(description = "New prompt text; leave blank to keep")
    private String prompt;

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        return Mono.fromCallable(() -> doExecute(args));
    }

    private ToolResult doExecute(Map<String, Object> args) {
        String taskId = (String) args.get("id");
        if (taskId == null || taskId.isBlank()) {
            return ToolResult.error("CronEdit", "Parameter 'id' is required");
        }
        String newCron = (String) args.get("cron");
        String newPrompt = (String) args.get("prompt");
        if ((newCron == null || newCron.isBlank()) && (newPrompt == null || newPrompt.isBlank())) {
            return ToolResult.error("CronEdit", "Pass at least one of 'cron' or 'prompt' to edit");
        }
        try {
            return scheduler
                    .edit(taskId, newCron, newPrompt)
                    .map(
                            t ->
                                    ToolResult.success(
                                            "CronEdit",
                                            "Edited cron job "
                                                    + t.id()
                                                    + " [cron="
                                                    + t.cron()
                                                    + "]"))
                    .orElseGet(
                            () ->
                                    ToolResult.error(
                                            "CronEdit",
                                            "No cron job found with id '" + taskId + "'"));
        } catch (IllegalArgumentException e) {
            return ToolResult.error("CronEdit", "Bad new schedule: " + e.getMessage());
        }
    }
}
