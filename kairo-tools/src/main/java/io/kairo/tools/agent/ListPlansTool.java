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
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.core.plan.PlanFileManager;
import io.kairo.core.tool.ToolHandler;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

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
public class ListPlansTool implements ToolHandler {

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
    public ToolResult execute(Map<String, Object> input) {
        if (planFileManager == null) {
            return new ToolResult(null, "PlanFileManager is not configured.", true, Map.of());
        }

        List<PlanFile> plans = planFileManager.listPlans();
        if (plans.isEmpty()) {
            return new ToolResult(null, "No plans found.", false, Map.of());
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

        return new ToolResult(null, sb.toString(), false, Map.of("count", plans.size()));
    }

    private static String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() <= maxLen ? str : str.substring(0, maxLen - 3) + "...";
    }
}
