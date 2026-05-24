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
package io.kairo.tools.agent;

import io.kairo.api.plan.PlanFile;
import io.kairo.api.tool.SyncTool;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.core.plan.PlanFileManager;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Lists all saved plans with their status.
 *
 * <p>Requires a {@link PlanFileManager} to be configured. Returns a formatted table of all plans
 * sorted by creation time (newest first).
 */
@Tool(
        name = "list_plans",
        description = "List all saved plans with their status",
        category = ToolCategory.AGENT_AND_TASK,
        sideEffect = ToolSideEffect.READ_ONLY)
public class ListPlansTool implements SyncTool {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private PlanFileManager planFileManager;

    /** Default constructor. */
    public ListPlansTool() {}

    /**
     * Set the plan file manager.
     *
     * @param planFileManager the plan file manager
     */
    public void setPlanFileManager(PlanFileManager planFileManager) {
        this.planFileManager = planFileManager;
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        return Mono.fromCallable(() -> executeSync(args, ctx));
    }

    private ToolResult executeSync(Map<String, Object> input, ToolContext ctx) {
        if (planFileManager == null) {
            return ToolResult.error(null, "PlanFileManager is not configured.");
        }

        List<PlanFile> plans = planFileManager.listPlans();
        if (plans.isEmpty()) {
            return ToolResult.success(null, "No plans found.");
        }

        var sb = new StringBuilder();
        sb.append(String.format("%-10s | %-30s | %-12s | %s%n", "ID", "Name", "Status", "Created"));
        sb.append("-".repeat(75)).append('\n');
        for (PlanFile plan : plans) {
            sb.append(
                    String.format(
                            "%-10s | %-30s | %-12s | %s%n",
                            plan.id(),
                            truncate(plan.name(), 30),
                            plan.status(),
                            FORMATTER.format(plan.createdAt())));
        }

        return ToolResult.success(null, sb.toString(), Map.of("count", plans.size()));
    }

    private static String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() <= maxLen ? str : str.substring(0, maxLen - 3) + "...";
    }
}
