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
import io.kairo.api.cron.CronTask;
import io.kairo.api.tool.SyncTool;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.cron.CronExpression;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import reactor.core.publisher.Mono;

@Tool(
        name = "CronCreate",
        description =
                "Schedule a prompt to fire on a cron schedule. Uses standard 5-field cron"
                        + " expressions: minute hour day-of-month month day-of-week. Returns a job ID"
                        + " that can be passed to CronDelete.",
        category = ToolCategory.SCHEDULING,
        sideEffect = ToolSideEffect.WRITE)
public class CronCreateTool implements SyncTool {

    private final CronScheduler scheduler;

    public CronCreateTool(CronScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @ToolParam(
            description = "Standard 5-field cron expression (e.g. '*/5 * * * *' for every 5 min)",
            required = true)
    private String cron;

    @ToolParam(description = "The prompt to enqueue at each fire time", required = true)
    private String prompt;

    @ToolParam(
            description =
                    "true (default) = fire on every cron match; false = fire once then auto-delete")
    private String recurring;

    @ToolParam(
            description =
                    "true = persist to file and survive restarts; false (default) = session-only")
    private String durable;

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        return Mono.fromCallable(() -> doExecute(args));
    }

    private ToolResult doExecute(Map<String, Object> args) {
        String cronExpr = (String) args.get("cron");
        String promptText = (String) args.get("prompt");

        if (cronExpr == null || cronExpr.isBlank()) {
            return ToolResult.error("CronCreate", "Parameter 'cron' is required");
        }
        if (promptText == null || promptText.isBlank()) {
            return ToolResult.error("CronCreate", "Parameter 'prompt' is required");
        }

        boolean isRecurring = parseBoolean(args.get("recurring"), true);
        boolean isDurable = parseBoolean(args.get("durable"), false);

        try {
            CronExpression.parse(cronExpr);
        } catch (IllegalArgumentException e) {
            return ToolResult.error(
                    "CronCreate", "Invalid cron expression '" + cronExpr + "': " + e.getMessage());
        }

        try {
            CronTask task = scheduler.create(cronExpr, promptText, isRecurring, isDurable);
            CronExpression expr = CronExpression.parse(cronExpr);
            ZonedDateTime nextFire = expr.nextFireTime(ZonedDateTime.now(ZoneId.systemDefault()));
            String nextFireStr = nextFire != null ? nextFire.toString() : "unknown";

            return ToolResult.success(
                    "CronCreate",
                    "Created cron job "
                            + task.id()
                            + " ["
                            + cronExpr
                            + "]. Next fire: "
                            + nextFireStr,
                    Map.of(
                            "id", task.id(),
                            "cron", cronExpr,
                            "recurring", isRecurring,
                            "durable", isDurable,
                            "nextFire", nextFireStr));
        } catch (IllegalStateException e) {
            return ToolResult.error("CronCreate", e.getMessage());
        }
    }

    private static boolean parseBoolean(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }
}
