/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
        name = "CronDelete",
        description = "Cancel a cron job previously scheduled with CronCreate.",
        category = ToolCategory.SCHEDULING,
        sideEffect = ToolSideEffect.WRITE)
public class CronDeleteTool implements SyncTool {

    private final CronScheduler scheduler;

    public CronDeleteTool(CronScheduler scheduler) {
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
            return ToolResult.error("CronDelete", "Parameter 'id' is required");
        }

        boolean deleted = scheduler.delete(taskId);
        if (deleted) {
            return ToolResult.success("CronDelete", "Deleted cron job " + taskId);
        } else {
            return ToolResult.error("CronDelete", "No cron job found with id '" + taskId + "'");
        }
    }
}
