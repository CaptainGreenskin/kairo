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
package io.kairo.multiagent.orchestration.internal;

import io.kairo.multiagent.subagent.ExpertProfile;
import io.kairo.multiagent.subagent.RoleCapabilities;
import java.util.List;
import java.util.Set;

/**
 * Builds LLM prompts for DAG-based task planning.
 *
 * <p>Separated from {@link DefaultPlanner} for testability. The prompt instructs the LLM to output
 * a JSON array of planned steps with dependency edges forming a directed acyclic graph.
 *
 * @since v0.10.1 (Experimental)
 */
public final class LlmPlannerPrompt {

    private LlmPlannerPrompt() {}

    /**
     * Build the planning prompt for the LLM.
     *
     * @param goal the user's goal / task description
     * @param profiles available expert profiles (roleId + skillProfile for each)
     * @param memorySummaries optional memory context summaries (may be empty)
     * @return the full prompt string instructing the LLM to produce a DAG plan
     */
    public static String buildPlanningPrompt(
            String goal, List<ExpertProfile> profiles, List<String> memorySummaries) {
        StringBuilder sb = new StringBuilder(2048);

        // System instruction
        sb.append("You are a task planner for an expert team. Your job is to decompose a goal ");
        sb.append(
                "into a directed acyclic graph (DAG) of steps, each assigned to an expert role.\n\n");

        // Output format specification
        sb.append("## Output Format\n");
        sb.append("Respond with ONLY a JSON array. No markdown fences, no explanation.\n");
        sb.append("Each element must have this exact structure:\n");
        sb.append("```\n");
        sb.append("[{\"stepId\": \"<unique-id>\", \"roleId\": \"<expert-role-id>\", ");
        sb.append("\"instruction\": \"<what this step should do>\", ");
        sb.append("\"dependsOn\": [\"<stepId-of-prerequisite>\", ...]}]\n");
        sb.append("```\n\n");

        // Rules
        sb.append("## Rules\n");
        sb.append(
                "- stepId must be unique across all steps (use descriptive ids like \"analyze-reqs\", ");
        sb.append("\"impl-core\", \"write-tests\")\n");
        sb.append("- roleId MUST be one of the available roles listed below\n");
        sb.append("- dependsOn references stepIds that must complete before this step can start\n");
        sb.append("- dependsOn may be empty [] for steps with no prerequisites\n");
        sb.append("- The graph MUST be acyclic (no circular dependencies)\n");
        sb.append("- Maximize parallelism: only add a dependency if truly required\n");
        sb.append("- Each step should be a meaningful unit of work, not trivially small\n\n");

        // Available roles with capabilities
        sb.append("## Available Expert Roles\n");
        for (ExpertProfile profile : profiles) {
            sb.append("- **").append(profile.roleId()).append("**: ");
            sb.append(profile.skillProfile());
            appendCapabilities(sb, profile.capabilities());
            sb.append('\n');
        }
        sb.append('\n');

        // Memory context (if available)
        if (memorySummaries != null && !memorySummaries.isEmpty()) {
            sb.append("## Context from Memory\n");
            for (String summary : memorySummaries) {
                sb.append("- ").append(summary).append('\n');
            }
            sb.append('\n');
        }

        // The goal
        sb.append("## Goal\n");
        sb.append(goal).append('\n');

        return sb.toString();
    }

    /**
     * Build a retry prompt appending the error from the first attempt.
     *
     * @param originalPrompt the original planning prompt
     * @param error description of what went wrong (parse error or validation failure)
     * @return the augmented prompt with error feedback
     */
    public static String buildRetryPrompt(String originalPrompt, String error) {
        return originalPrompt
                + "\n## Previous Attempt Failed\n"
                + "Your previous response could not be parsed or validated. Error:\n"
                + error
                + "\n\nPlease fix the issue and respond with ONLY a valid JSON array.\n";
    }

    private static void appendCapabilities(StringBuilder sb, RoleCapabilities caps) {
        if (caps == null || caps.equals(RoleCapabilities.EMPTY)) {
            return;
        }
        StringBuilder inner = new StringBuilder();
        appendSet(inner, caps.domains(), "domains");
        appendSet(inner, caps.actions(), "actions");
        appendSet(inner, caps.languages(), "languages");
        appendSet(inner, caps.frameworks(), "frameworks");
        if (!inner.isEmpty()) {
            sb.append(" [").append(inner).append(']');
        }
    }

    private static void appendSet(StringBuilder sb, Set<String> items, String label) {
        if (items.isEmpty()) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append(", ");
        }
        sb.append(label).append(": ").append(String.join("/", items));
    }
}
