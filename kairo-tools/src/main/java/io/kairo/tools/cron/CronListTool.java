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

import io.kairo.api.cron.CronTask;
import io.kairo.api.tool.SyncTool;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.cron.CronExpression;
import io.kairo.core.cron.CronScheduler;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

@Tool(
        name = "CronList",
        description = "List all cron jobs scheduled via CronCreate.",
        category = ToolCategory.SCHEDULING)
public class CronListTool implements SyncTool {

    private final CronScheduler scheduler;

    public CronListTool(CronScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        return Mono.fromCallable(this::doExecute);
    }

    private ToolResult doExecute() {
        List<CronTask> tasks = scheduler.list();
        if (tasks.isEmpty()) {
            return ToolResult.success("CronList", "No cron jobs scheduled.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(tasks.size()).append(" cron job(s):\n\n");
        for (CronTask task : tasks) {
            sb.append("  ID: ").append(task.id()).append('\n');
            sb.append("  Cron: ").append(task.cron()).append('\n');
            sb.append("  Prompt: ").append(truncate(task.prompt(), 80)).append('\n');
            sb.append("  Recurring: ").append(task.recurring()).append('\n');
            sb.append("  Durable: ").append(task.durable()).append('\n');
            sb.append("  Last fired: ")
                    .append(task.lastFiredAt() != null ? task.lastFiredAt().toString() : "never")
                    .append('\n');

            String nextFire = "unknown";
            try {
                CronExpression expr = CronExpression.parse(task.cron());
                ZonedDateTime next = expr.nextFireTime(ZonedDateTime.now(ZoneId.systemDefault()));
                if (next != null) {
                    nextFire = next.toString();
                }
            } catch (IllegalArgumentException ignored) {
            }
            sb.append("  Next fire: ").append(nextFire).append('\n');
            sb.append('\n');
        }

        return ToolResult.success(
                "CronList", sb.toString().stripTrailing(), Map.of("count", tasks.size()));
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }
}
