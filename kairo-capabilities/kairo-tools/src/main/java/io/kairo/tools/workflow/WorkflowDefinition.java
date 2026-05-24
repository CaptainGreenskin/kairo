/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.tools.workflow;

import java.util.List;
import java.util.Map;

/**
 * A user-defined multi-step automation declared in {@code .kairo/workflows/*.yaml}.
 *
 * <p>Schema:
 *
 * <pre>{@code
 * name: "code-review"
 * description: "lint then test then summarise"
 * steps:
 *   - name: "lint"
 *     tool: bash
 *     args:
 *       command: "mvn -q compile"
 *   - name: "test"
 *     tool: verify_execution
 *   - name: "report"
 *     tool: bash
 *     args:
 *       command: "echo done"
 *     continue_on_error: false
 * }</pre>
 *
 * <p>Each step is a direct tool invocation against the agent's tool registry. Agent-spawning steps
 * can be modelled via {@code tool: agent_spawn} with {@code args: { subagent_type: ..., task: ...
 * }} — no special syntax needed.
 *
 * @param name workflow identifier; required
 * @param description optional human-readable summary
 * @param steps ordered list of {@link Step}s; non-empty
 */
public record WorkflowDefinition(String name, String description, List<Step> steps) {

    public WorkflowDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Workflow name is required");
        }
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("Workflow must have at least one step");
        }
        steps = List.copyOf(steps);
    }

    /**
     * One ordered step in a workflow. Each step calls a registered tool with a fixed input map; the
     * {@code continueOnError} flag controls whether the workflow aborts or proceeds on a
     * non-success tool result.
     *
     * @param name step label for logs / progress reporting; required
     * @param tool registered tool name (e.g. {@code "bash"}, {@code "verify_execution"}); required
     * @param args input map passed to the tool; never null (empty map = no args)
     * @param continueOnError if {@code true}, a failing tool result is logged but doesn't abort the
     *     rest of the workflow. Default {@code false}.
     */
    public record Step(
            String name, String tool, Map<String, Object> args, boolean continueOnError) {

        public Step {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Step name is required");
            }
            if (tool == null || tool.isBlank()) {
                throw new IllegalArgumentException("Step tool is required");
            }
            args = args == null ? Map.of() : Map.copyOf(args);
        }
    }
}
