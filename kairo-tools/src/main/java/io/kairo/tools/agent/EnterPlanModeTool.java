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
import io.kairo.api.tool.ToolHandler;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.core.plan.PlanFileManager;
import io.kairo.core.tool.DefaultToolExecutor;
import java.util.Map;

/**
 * Enters plan mode for read-only exploration before implementation.
 *
 * <p>In plan mode, the agent focuses on gathering information, understanding the problem, and
 * formulating a plan. Write and system-change tools are blocked until plan mode is exited.
 *
 * <p>If a {@link PlanFileManager} is configured, a new plan file in DRAFT status is created on
 * disk.
 */
@Tool(
        name = "enter_plan_mode",
        description = "Enter plan mode for read-only exploration before implementation.",
        category = ToolCategory.AGENT_AND_TASK,
        sideEffect = ToolSideEffect.READ_ONLY)
public class EnterPlanModeTool implements ToolHandler {

    @ToolParam(description = "Name of the plan", required = false)
    private String name;

    private PlanFileManager planFileManager;
    private DefaultToolExecutor toolExecutor;

    /** Default constructor. */
    public EnterPlanModeTool() {}

    /**
     * Set the plan file manager for persisting plans.
     *
     * @param planFileManager the plan file manager
     */
    public void setPlanFileManager(PlanFileManager planFileManager) {
        this.planFileManager = planFileManager;
    }

    /**
     * Set the tool executor to enable plan mode enforcement.
     *
     * @param toolExecutor the tool executor
     */
    public void setToolExecutor(DefaultToolExecutor toolExecutor) {
        this.toolExecutor = toolExecutor;
    }

    @Override
    public ToolResult execute(Map<String, Object> input) {
        String planName =
                input.containsKey("name") ? input.get("name").toString() : "Untitled Plan";

        String planId = null;
        if (planFileManager != null) {
            PlanFile plan = planFileManager.createPlan(planName);
            planId = plan.id();
        }

        // Enable plan mode on the tool executor
        if (toolExecutor != null) {
            toolExecutor.setPlanMode(true);
        }

        String message =
                "Entered Plan Mode"
                        + (planId != null ? " (Plan: " + planId + ")" : "")
                        + "\n\n"
                        + "Available tools in Plan Mode (read-only only):\n"
                        + "- Read: Read file contents\n"
                        + "- Grep: Search for patterns\n"
                        + "- Glob: Find files by pattern\n"
                        + "- List: List directory contents\n\n"
                        + "Write tools (Write, Edit, Bash) are blocked until you exit Plan Mode.";

        return new ToolResult(
                null,
                message,
                false,
                planId != null ? Map.of("mode", "plan", "planId", planId) : Map.of("mode", "plan"));
    }
}
