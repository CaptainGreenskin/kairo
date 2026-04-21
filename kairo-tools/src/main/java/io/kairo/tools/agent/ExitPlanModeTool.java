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

import io.kairo.api.plan.PlanStatus;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCallRequest;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolHandler;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.api.tool.UserApprovalHandler;
import io.kairo.core.plan.PlanFileManager;
import io.kairo.core.tool.DefaultToolExecutor;
import java.util.Map;

/**
 * Exits plan mode and submits the plan for execution.
 *
 * <p>The agent provides a plan overview summarizing the intended actions, then transitions back to
 * execution mode. If a {@link UserApprovalHandler} is configured, user approval is requested before
 * exiting plan mode. If a {@link PlanFileManager} is configured, the plan content is saved and its
 * status is updated.
 */
@Tool(
        name = "exit_plan_mode",
        description = "Exit plan mode and submit the plan for execution.",
        category = ToolCategory.AGENT_AND_TASK,
        sideEffect = ToolSideEffect.READ_ONLY)
public class ExitPlanModeTool implements ToolHandler {

    @ToolParam(description = "The plan overview", required = true)
    private String overview;

    @ToolParam(description = "The plan ID to update", required = false)
    private String planId;

    @ToolParam(description = "The full plan content to save", required = false)
    private String planContent;

    private PlanFileManager planFileManager;
    private DefaultToolExecutor toolExecutor;
    private UserApprovalHandler approvalHandler;

    /** Default constructor. */
    public ExitPlanModeTool() {}

    /**
     * Set the plan file manager for persisting plans.
     *
     * @param planFileManager the plan file manager
     */
    public void setPlanFileManager(PlanFileManager planFileManager) {
        this.planFileManager = planFileManager;
    }

    /**
     * Set the tool executor to disable plan mode enforcement on exit.
     *
     * @param toolExecutor the tool executor
     */
    public void setToolExecutor(DefaultToolExecutor toolExecutor) {
        this.toolExecutor = toolExecutor;
    }

    /**
     * Set the approval handler for optional exit approval.
     *
     * @param approvalHandler the approval handler, or null to skip approval
     */
    public void setApprovalHandler(UserApprovalHandler approvalHandler) {
        this.approvalHandler = approvalHandler;
    }

    @Override
    public ToolResult execute(Map<String, Object> input) {
        String overview = (String) input.get("overview");
        if (overview == null || overview.isBlank()) {
            return new ToolResult(null, "Parameter 'overview' is required", true, Map.of());
        }

        String planId = input.containsKey("planId") ? input.get("planId").toString() : null;
        String planContent =
                input.containsKey("plan_content") ? input.get("plan_content").toString() : overview;

        // Optional approval check
        if (approvalHandler != null) {
            var request =
                    new ToolCallRequest("exit_plan_mode", input, ToolSideEffect.SYSTEM_CHANGE);
            var result = approvalHandler.requestApproval(request).block();
            if (result != null && !result.approved()) {
                return new ToolResult(
                        null,
                        "Plan exit denied: " + result.reason() + "\nStill in Plan Mode.",
                        false,
                        Map.of("mode", "plan"));
            }
        }

        // Save plan content if available
        if (planFileManager != null && planId != null) {
            try {
                planFileManager.updatePlan(planId, planContent, PlanStatus.APPROVED);
            } catch (IllegalArgumentException e) {
                // Plan not found — continue with exit anyway
            }
        }

        // Disable plan mode on the tool executor
        if (toolExecutor != null) {
            toolExecutor.setPlanMode(false);
        }

        return new ToolResult(
                null,
                "Exited Plan Mode. Write tools are now available.\n\nPlan submitted: " + overview,
                false,
                Map.of("mode", "execute"));
    }
}
