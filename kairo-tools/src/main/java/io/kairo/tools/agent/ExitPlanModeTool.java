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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.plan.PlanStatus;
import io.kairo.api.tool.SyncTool;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCallRequest;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.api.tool.UserApprovalHandler;
import io.kairo.core.plan.PlanFileManager;
import io.kairo.core.tool.DefaultToolExecutor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Exits plan mode and submits a structured plan for execution.
 *
 * <p>The agent provides an overview plus an array of {@code items} ({@code {content, priority}}).
 * On approval, the tool atomically seeds {@code .kairo/todos.json} from the items so the UI's
 * sticky TodoListPanel and InlineTodoCard render immediately — the agent does NOT need a follow-up
 * {@code todo_write} call.
 *
 * <p>The frontend approval card surfaces the items as a checkable/editable list. If the user edits
 * items before approving, {@code WebSocketApprovalHandler} mutates the input map in place before
 * resolving the approval, so this tool reads the user's edited items below.
 *
 * <p>If a {@link PlanFileManager} is configured, the plan content is saved with status {@link
 * PlanStatus#APPROVED}.
 */
@Tool(
        name = "exit_plan_mode",
        description =
                "Exit plan mode and submit a plan for human review. Three required fields:"
                        + " (1) 'overview' — one-line summary; (2) 'plan_content' — the full plan"
                        + " as markdown (sections, bullets, code samples, file paths, the WHY of"
                        + " the approach). This is the main review surface, write it richly. "
                        + "(3) 'items' — actionable checklist derived from the plan, each item is"
                        + " {content, priority?}; on user approval the items are atomically written"
                        + " to the working todo list (do NOT call todo_write afterwards)."
                        + " 'items' accepts a JSON array or stringified JSON array."
                        + " Example items: [{\"content\":\"Refactor auth middleware\",\"priority\":\"high\"}].",
        category = ToolCategory.AGENT_AND_TASK,
        sideEffect = ToolSideEffect.READ_ONLY,
        // 4h: this tool blocks on UserApprovalHandler.requestApproval().block() while the
        // human reviews/edits the plan in the UI. The default 120s per-tool timeout would
        // interrupt the await with InterruptedException, surfacing as "Plan submission failed".
        timeoutSeconds = 14_400)
public class ExitPlanModeTool implements SyncTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TODO_FILE = ".kairo/todos.json";
    private static final List<String> VALID_PRIORITIES = List.of("high", "medium", "low");

    @ToolParam(description = "One-line summary of the plan", required = true)
    private String overview;

    @ToolParam(
            description =
                    "The full plan as markdown. Use sections (## Goal, ## Approach, ## Files, ##"
                            + " Validation), bullet lists, code blocks. This is what the user reads"
                            + " to decide approve/reject — write it richly.",
            required = true)
    private String plan_content;

    @ToolParam(
            description =
                    "Array of plan items. Each item must have 'content' (string); 'priority'"
                            + " (high|medium|low) is optional. Pass either the array directly or"
                            + " a stringified JSON array.",
            required = true)
    private String items;

    @ToolParam(description = "The plan ID to update", required = false)
    private String planId;

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
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        return Mono.fromCallable(() -> doExecute(args, ctx.workspace().root()));
    }

    private ToolResult doExecute(Map<String, Object> input, Path workspaceRoot) {
        String overview = (String) input.get("overview");
        if (overview == null || overview.isBlank()) {
            return ToolResult.error(null, "Parameter 'overview' is required");
        }
        String planContentInput = (String) input.get("plan_content");
        if (planContentInput == null || planContentInput.isBlank()) {
            return ToolResult.error(
                    null,
                    "Parameter 'plan_content' is required — provide the plan as markdown so the"
                            + " user can review the rationale, not just a checklist");
        }

        // Approval blocks here; the WebSocket handler may mutate input.items in place to
        // reflect user edits before resolving — so we re-read items AFTER the await.
        if (approvalHandler != null) {
            var request =
                    new ToolCallRequest("exit_plan_mode", input, ToolSideEffect.SYSTEM_CHANGE);
            var result = approvalHandler.requestApproval(request).block();
            if (result != null && !result.approved()) {
                String reason =
                        result.reason() != null && !result.reason().isBlank()
                                ? result.reason()
                                : "User denied";
                return ToolResult.success(
                        null,
                        "Plan exit denied: "
                                + reason
                                + "\nStill in Plan Mode. Refine the plan"
                                + " based on the feedback and call exit_plan_mode again.",
                        Map.of("mode", "plan", "feedback", reason));
            }
        }

        // Parse items (possibly user-edited at this point).
        List<Map<String, Object>> parsedItems;
        try {
            parsedItems = parseItems(input.get("items"));
        } catch (JsonProcessingException e) {
            return ToolResult.error(null, "Invalid JSON in 'items': " + e.getOriginalMessage());
        } catch (IllegalArgumentException e) {
            return ToolResult.error(null, e.getMessage());
        }

        // Atomic todo seed — write .kairo/todos.json before flipping plan-mode off so the UI
        // panel and the agent's first post-plan tool call see the same state.
        int seeded = 0;
        if (!parsedItems.isEmpty()) {
            try {
                seedTodos(workspaceRoot, parsedItems);
                seeded = parsedItems.size();
            } catch (IOException e) {
                return ToolResult.error(null, "Failed to seed todos: " + e.getMessage());
            }
        }

        String planId = input.containsKey("planId") ? String.valueOf(input.get("planId")) : null;
        String planContent =
                input.containsKey("plan_content")
                        ? String.valueOf(input.get("plan_content"))
                        : planContentInput;

        if (planFileManager != null && planId != null) {
            try {
                planFileManager.updatePlan(planId, planContent, PlanStatus.APPROVED);
            } catch (IllegalArgumentException e) {
                // Plan not found — continue with exit anyway
            }
        }

        if (toolExecutor != null) {
            toolExecutor.setPlanMode(false);
        }

        String message =
                "Exited Plan Mode. Write tools are now available.\n\n"
                        + "Plan submitted: "
                        + overview
                        + "\nSeeded "
                        + seeded
                        + " todo(s) — start working on the first item NOW;"
                        + " do not call todo_write to recreate them.";
        return ToolResult.success(null, message, Map.of("mode", "execute", "todoCount", seeded));
    }

    /**
     * Coerce the {@code items} parameter into a {@code List<Map<String, Object>>}. Accepts either a
     * direct {@code List} (the model passed an array) or a {@code String} (the model honored the
     * declared parameter type and stringified the JSON).
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseItems(Object raw) throws JsonProcessingException {
        if (raw == null) {
            return List.of();
        }
        List<?> rawList;
        if (raw instanceof List<?> list) {
            rawList = list;
        } else if (raw instanceof String s) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) {
                return List.of();
            }
            rawList = MAPPER.readValue(trimmed, List.class);
        } else {
            throw new IllegalArgumentException(
                    "Parameter 'items' must be a JSON array (or stringified JSON array), got: "
                            + raw.getClass().getSimpleName());
        }

        List<Map<String, Object>> result = new ArrayList<>(rawList.size());
        int idx = 0;
        for (Object element : rawList) {
            idx++;
            if (!(element instanceof Map<?, ?> m)) {
                throw new IllegalArgumentException(
                        "Each plan item must be a JSON object (item #" + idx + ")");
            }
            Object content = m.get("content");
            if (!(content instanceof String contentStr) || contentStr.isBlank()) {
                throw new IllegalArgumentException(
                        "Plan item #" + idx + " requires a non-empty 'content' string");
            }
            Object priority = m.get("priority");
            if (priority != null
                    && (!(priority instanceof String) || !VALID_PRIORITIES.contains(priority))) {
                throw new IllegalArgumentException(
                        "Plan item #"
                                + idx
                                + " has invalid 'priority' '"
                                + priority
                                + "'; must be one of "
                                + VALID_PRIORITIES);
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("id", String.valueOf(idx));
            normalized.put("content", contentStr.trim());
            if (priority instanceof String p) {
                normalized.put("priority", p);
            }
            normalized.put("status", "pending");
            result.add(normalized);
        }
        return result;
    }

    private void seedTodos(Path workspaceRoot, List<Map<String, Object>> items) throws IOException {
        Path todoFile = workspaceRoot.resolve(TODO_FILE);
        Files.createDirectories(todoFile.getParent());
        String formatted = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(items);
        Files.writeString(todoFile, formatted, StandardCharsets.UTF_8);
    }
}
