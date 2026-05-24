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
package io.kairo.tools.workflow;

import io.kairo.api.tool.SyncTool;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.api.workspace.Workspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Runs a user-defined YAML/JSON workflow file from {@code .kairo/workflows/} as a sequence of tool
 * calls.
 *
 * <p>Usage: the agent calls {@code workflow(name="code-review")} → the tool looks up {@code
 * .kairo/workflows/code-review.yaml} in the workspace root, parses it, and executes the steps
 * sequentially against the same {@link ToolExecutor} the agent is already using. Each step is a
 * direct invocation of a registered tool with a fixed input map; {@code continue_on_error: true} on
 * a step lets the workflow proceed past a failing tool result instead of aborting.
 *
 * <p>Returns a structured progress report with per-step status + the first 500 chars of each step's
 * output, plus an aggregate {@code completed_steps} / {@code failed_steps} / {@code aborted}
 * metadata.
 *
 * <p>Wiring: needs a {@link ToolExecutor} reference. The factory sets it via {@link
 * #setToolExecutor(ToolExecutor)} during agent bootstrap — without that, calls return a descriptive
 * error so the agent doesn't think the workflow "ran" with zero steps.
 *
 * @since 1.3
 */
@Tool(
        name = "workflow",
        description =
                "Execute a YAML/JSON workflow file from .kairo/workflows/. The workflow is a"
                        + " sequence of tool calls — each step names a registered tool + args."
                        + " Steps run sequentially; one failure aborts the rest unless the step"
                        + " has continue_on_error: true. Use for multi-step automations that"
                        + " a single agent prompt would be too brittle for (lint → test → release).",
        category = ToolCategory.AGENT_AND_TASK,
        sideEffect = ToolSideEffect.SYSTEM_CHANGE)
public class WorkflowTool implements SyncTool {

    private static final Logger log = LoggerFactory.getLogger(WorkflowTool.class);
    private static final String WORKFLOWS_DIR = ".kairo/workflows";
    private static final int OUTPUT_PREVIEW_CHARS = 500;

    @ToolParam(
            description =
                    "Workflow name (filename without .yaml/.yml/.json suffix), e.g. \"code-review\""
                            + " → loads .kairo/workflows/code-review.yaml. Or pass the absolute"
                            + " path to a workflow file instead.",
            required = true)
    private String name;

    private ToolExecutor toolExecutor;

    /** Default ctor for reflection-based registration. {@link #setToolExecutor} MUST be called. */
    public WorkflowTool() {}

    /** Setter wiring — called by the agent factory after the registry knows the executor. */
    public void setToolExecutor(ToolExecutor toolExecutor) {
        this.toolExecutor = toolExecutor;
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        if (toolExecutor == null) {
            return Mono.just(
                    ToolResult.error(
                            null,
                            "WorkflowTool was not wired with a ToolExecutor — the factory needs"
                                    + " to call setToolExecutor(...) during agent bootstrap."));
        }
        Object rawName = args.get("name");
        if (!(rawName instanceof String n) || n.isBlank()) {
            return Mono.just(ToolResult.error(null, "'name' is required"));
        }
        Path workflowFile = resolveWorkflowPath(n.trim(), ctx.workspace());
        if (workflowFile == null) {
            return Mono.just(
                    ToolResult.error(
                            null,
                            "Workflow '"
                                    + n
                                    + "' not found. Looked for .kairo/workflows/"
                                    + n
                                    + ".{yaml,yml,json} and as an absolute path."));
        }
        WorkflowDefinition workflow;
        try {
            workflow = WorkflowParser.fromFile(workflowFile);
        } catch (IOException e) {
            return Mono.just(
                    ToolResult.error(
                            null,
                            "Failed to parse workflow '"
                                    + n
                                    + "' from "
                                    + workflowFile
                                    + ": "
                                    + e.getMessage()));
        }
        return Mono.fromCallable(() -> runSequential(workflow, workflowFile));
    }

    /**
     * Look up the YAML/JSON file for {@code name}, trying (in order):
     *
     * <ol>
     *   <li>If {@code name} is an absolute path that exists, use it directly.
     *   <li>{@code <workspace>/.kairo/workflows/<name>.yaml}
     *   <li>{@code <workspace>/.kairo/workflows/<name>.yml}
     *   <li>{@code <workspace>/.kairo/workflows/<name>.json}
     * </ol>
     */
    static Path resolveWorkflowPath(String name, Workspace workspace) {
        Path asAbsolute = Path.of(name);
        if (asAbsolute.isAbsolute() && Files.isRegularFile(asAbsolute)) {
            return asAbsolute;
        }
        Path dir = workspace.root().resolve(WORKFLOWS_DIR);
        for (String suffix : new String[] {".yaml", ".yml", ".json"}) {
            Path candidate = dir.resolve(name + suffix);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private ToolResult runSequential(WorkflowDefinition wf, Path source) {
        long startMs = System.currentTimeMillis();
        StringBuilder report = new StringBuilder();
        report.append("Workflow: ").append(wf.name());
        if (wf.description() != null && !wf.description().isBlank()) {
            report.append(" — ").append(wf.description());
        }
        report.append("\nSource: ").append(source).append('\n');

        List<Map<String, Object>> stepResults = new ArrayList<>(wf.steps().size());
        int completed = 0;
        int failed = 0;
        boolean aborted = false;

        for (int i = 0; i < wf.steps().size(); i++) {
            WorkflowDefinition.Step step = wf.steps().get(i);
            long stepStart = System.currentTimeMillis();
            ToolResult stepResult;
            try {
                stepResult = toolExecutor.execute(step.tool(), step.args()).block();
            } catch (Exception e) {
                // The executor itself blew up (vs the tool returning an error result). Treat as
                // a failure but record both the kind and the exception.
                stepResult = ToolResult.error(null, "executor exception: " + e.getMessage());
            }
            long stepMs = System.currentTimeMillis() - stepStart;
            boolean isError = stepResult != null && stepResult.isError();
            if (isError) {
                failed++;
            } else {
                completed++;
            }

            String preview = stepResult == null ? "<no result>" : truncate(stepResult.content());
            report.append(
                    String.format(
                            "  [%d/%d] %s (tool=%s, %dms) %s%n",
                            i + 1,
                            wf.steps().size(),
                            step.name(),
                            step.tool(),
                            stepMs,
                            isError ? "FAIL" : "OK"));
            if (!preview.isEmpty()) {
                report.append("      ").append(preview.replace("\n", "\n      ")).append('\n');
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", step.name());
            entry.put("tool", step.tool());
            entry.put("durationMs", stepMs);
            entry.put("isError", isError);
            stepResults.add(entry);

            if (isError && !step.continueOnError()) {
                aborted = true;
                report.append("  [aborted at step ")
                        .append(i + 1)
                        .append(" — set continue_on_error: true to keep going]\n");
                log.info("Workflow '{}' aborted at step {} ('{}')", wf.name(), i + 1, step.name());
                break;
            }
        }

        long totalMs = System.currentTimeMillis() - startMs;
        report.append('\n')
                .append(
                        aborted
                                ? "ABORTED"
                                : (failed == 0 ? "COMPLETED" : "COMPLETED_WITH_FAILURES"))
                .append(" — ")
                .append(completed)
                .append("/")
                .append(wf.steps().size())
                .append(" steps in ")
                .append(totalMs)
                .append("ms");

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("workflow", wf.name());
        metadata.put("totalSteps", wf.steps().size());
        metadata.put("completedSteps", completed);
        metadata.put("failedSteps", failed);
        metadata.put("aborted", aborted);
        metadata.put("totalDurationMs", totalMs);
        metadata.put("stepResults", stepResults);
        return ToolResult.success(null, report.toString(), metadata);
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= OUTPUT_PREVIEW_CHARS
                ? s
                : s.substring(0, OUTPUT_PREVIEW_CHARS) + "…(truncated)";
    }
}
