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
package io.kairo.expertteam.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.agent.Agent;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.team.RoleDefinition;
import io.kairo.api.team.Team;
import io.kairo.api.team.TeamExecutionPlan;
import io.kairo.api.team.TeamExecutionRequest;
import io.kairo.api.team.TeamStep;
import io.kairo.expertteam.role.ExpertProfile;
import io.kairo.expertteam.role.ExpertRoleRegistry;
import io.kairo.expertteam.role.RoleMatchResult;
import io.kairo.expertteam.role.RoleMatcher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LLM-driven planner that produces a DAG of {@link TeamStep}s for parallel execution.
 *
 * <p>The planner invokes an LLM (via a planning {@link Agent}) to decompose a user goal into a
 * directed acyclic graph of steps, each assigned to an expert role. If the LLM fails or produces
 * invalid output, the planner retries ONCE with error feedback; on second failure, it falls back to
 * the deterministic one-step-per-role sequential plan.
 *
 * <p>DAG validation includes:
 *
 * <ul>
 *   <li>Cycle detection (Kahn's algorithm)
 *   <li>Dangling dependsOn references (all referenced stepIds must exist)
 *   <li>Unknown roleId validation (all roleIds must exist in the registry)
 * </ul>
 *
 * @since v0.10.1 (Experimental)
 */
public final class DefaultPlanner {

    private static final Logger log = LoggerFactory.getLogger(DefaultPlanner.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ExpertRoleRegistry registry;
    @Nullable private final Agent planningAgent;
    @Nullable private final List<String> memorySummaries;
    private final List<RoleDefinition> configuredRoles;
    private final boolean useRoleMatching;

    /**
     * LLM-driven planner with registry and planning agent.
     *
     * @param registry the expert role registry providing available profiles
     * @param planningAgent the agent used to invoke the LLM for planning (may be null to disable
     *     LLM planning and use deterministic fallback only)
     * @param memorySummaries optional memory context to include in the planning prompt
     */
    public DefaultPlanner(
            ExpertRoleRegistry registry,
            @Nullable Agent planningAgent,
            @Nullable List<String> memorySummaries) {
        this(registry, planningAgent, memorySummaries, List.of());
    }

    /**
     * Full constructor with explicit role configuration.
     *
     * @param registry the expert role registry
     * @param planningAgent optional planning agent (null = deterministic only)
     * @param memorySummaries optional memory summaries
     * @param configuredRoles explicit roles to use for deterministic fallback
     */
    public DefaultPlanner(
            ExpertRoleRegistry registry,
            @Nullable Agent planningAgent,
            @Nullable List<String> memorySummaries,
            List<RoleDefinition> configuredRoles) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.planningAgent = planningAgent;
        this.memorySummaries = memorySummaries != null ? List.copyOf(memorySummaries) : List.of();
        this.configuredRoles = configuredRoles == null ? List.of() : List.copyOf(configuredRoles);
        this.useRoleMatching = true;
    }

    /** Backward-compatible deterministic planner (no LLM, no registry-driven profiles). */
    public DefaultPlanner() {
        this(List.of());
    }

    /** Backward-compatible deterministic planner with explicit role list. */
    public DefaultPlanner(List<RoleDefinition> configuredRoles) {
        this.registry = new ExpertRoleRegistry();
        this.planningAgent = null;
        this.memorySummaries = List.of();
        this.configuredRoles = configuredRoles == null ? List.of() : List.copyOf(configuredRoles);
        this.useRoleMatching = false;
    }

    /**
     * Produce a plan for the given request against the given team.
     *
     * <p>When a planning agent is configured, the planner invokes the LLM to produce a DAG. On
     * failure it retries once with error feedback; on second failure it falls back to
     * deterministic.
     */
    public TeamExecutionPlan plan(TeamExecutionRequest request, Team team) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(team, "team must not be null");

        if (planningAgent != null) {
            try {
                return llmPlan(request, team);
            } catch (PlanValidationException ex) {
                log.warn(
                        "LLM planner failed: {}; falling back to deterministic plan",
                        ex.getMessage());
            } catch (RuntimeException ex) {
                log.warn(
                        "LLM planner threw unexpectedly: {}; falling back to deterministic plan",
                        ex.getMessage());
            }
        }

        return deterministicPlan(request, team);
    }

    /**
     * Build a single-step fallback plan from the raw request goal. Used when {@link
     * io.kairo.api.team.PlannerFailureMode#SINGLE_STEP_FALLBACK} is in effect and the primary
     * planner throws.
     */
    public TeamExecutionPlan singleStepFallback(TeamExecutionRequest request, Team team) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(team, "team must not be null");
        RoleDefinition role = firstResolvableRole(team);
        TeamStep step =
                new TeamStep(
                        "step-1-" + role.roleId(),
                        "Single-step fallback for goal: " + summarise(request.goal()),
                        role,
                        List.of(),
                        0);
        return new TeamExecutionPlan(
                "plan-fallback-" + UUID.randomUUID(), List.of(step), Instant.now());
    }

    /** Roles visible to this planner, preferring explicit configuration over agent derivation. */
    public List<RoleDefinition> resolveRoles(Team team) {
        if (!configuredRoles.isEmpty()) {
            return configuredRoles;
        }
        List<Agent> agents = team.agents();
        List<RoleDefinition> derived = new ArrayList<>(agents.size());
        for (Agent agent : agents) {
            derived.add(
                    new RoleDefinition(
                            agent.id(),
                            agent.name(),
                            "Auto-derived role bound to agent '" + agent.name() + "'",
                            "agent.default",
                            List.of()));
        }
        return List.copyOf(derived);
    }

    /** Build a role→agent binding map the coordinator will feed to the generator. */
    public Map<String, Agent> bindRoles(Team team, TeamExecutionPlan plan) {
        Objects.requireNonNull(plan, "plan must not be null");
        List<Agent> agents = team.agents();
        if (agents.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot bind roles: team has no agents (ADR-015 fix #4)");
        }
        Map<String, Agent> bindings = new java.util.LinkedHashMap<>();
        for (int i = 0; i < plan.steps().size(); i++) {
            TeamStep step = plan.steps().get(i);
            Agent agent = agents.get(i % agents.size());
            bindings.put(step.assignedRole().roleId(), agent);
        }
        return Map.copyOf(bindings);
    }

    // ------------------------------------------------------------------ LLM planning

    private TeamExecutionPlan llmPlan(TeamExecutionRequest request, Team team) {
        List<ExpertProfile> profiles = registry.allProfiles();
        String prompt =
                LlmPlannerPrompt.buildPlanningPrompt(request.goal(), profiles, memorySummaries);

        // First attempt
        String firstError;
        try {
            String response = callPlanningAgent(prompt);
            List<PlannedStep> steps = parseAndValidate(response);
            return convertToPlan(steps);
        } catch (PlanValidationException ex) {
            firstError = ex.getMessage();
            log.debug("LLM plan first attempt failed: {}", firstError);
        }

        // Retry with error feedback
        String retryPrompt = LlmPlannerPrompt.buildRetryPrompt(prompt, firstError);
        String retryResponse = callPlanningAgent(retryPrompt);
        List<PlannedStep> steps = parseAndValidate(retryResponse);
        return convertToPlan(steps);
    }

    private String callPlanningAgent(String prompt) {
        assert planningAgent != null;
        Msg input = Msg.of(MsgRole.USER, prompt);
        Msg response = planningAgent.call(input).block();
        if (response == null) {
            throw new PlanValidationException("Planning agent returned null response");
        }
        return response.text();
    }

    // ------------------------------------------------------------------ parsing & validation

    /**
     * Parse the LLM response as JSON and validate the DAG structure.
     *
     * @param response the raw LLM response text
     * @return validated list of planned steps
     * @throws PlanValidationException if parsing or validation fails
     */
    List<PlannedStep> parseAndValidate(String response) {
        List<PlannedStep> steps = parseLlmResponse(response);
        validateDag(steps);
        return steps;
    }

    /** Parse the LLM response into a list of PlannedStep records. */
    static List<PlannedStep> parseLlmResponse(String response) {
        if (response == null || response.isBlank()) {
            throw new PlanValidationException("Empty LLM response");
        }

        // Strip markdown code fences if present
        String json = response.strip();
        if (json.startsWith("```")) {
            int firstNewline = json.indexOf('\n');
            if (firstNewline > 0) {
                json = json.substring(firstNewline + 1);
            }
            if (json.endsWith("```")) {
                json = json.substring(0, json.length() - 3);
            }
            json = json.strip();
        }

        try {
            return MAPPER.readValue(json, new TypeReference<List<PlannedStep>>() {});
        } catch (JsonProcessingException ex) {
            throw new PlanValidationException("JSON parse error: " + ex.getMessage());
        }
    }

    /**
     * Validate the DAG:
     *
     * <ul>
     *   <li>All dependsOn references point to existing stepIds
     *   <li>All roleId values exist in the registry
     *   <li>No cycles (Kahn's algorithm)
     *   <li>No duplicate stepIds
     * </ul>
     */
    void validateDag(List<PlannedStep> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new PlanValidationException("Plan must contain at least one step");
        }

        // Collect all stepIds and check uniqueness
        Set<String> stepIds = new HashSet<>();
        for (PlannedStep step : steps) {
            if (step.stepId() == null || step.stepId().isBlank()) {
                throw new PlanValidationException("Step has null or blank stepId");
            }
            if (!stepIds.add(step.stepId())) {
                throw new PlanValidationException("Duplicate stepId: '" + step.stepId() + "'");
            }
        }

        // Validate roleIds
        Set<String> validRoleIds = registry.registeredRoleIds();
        for (PlannedStep step : steps) {
            if (step.roleId() == null || step.roleId().isBlank()) {
                throw new PlanValidationException(
                        "Step '" + step.stepId() + "' has null or blank roleId");
            }
            if (!validRoleIds.contains(step.roleId())) {
                throw new PlanValidationException(
                        "Step '"
                                + step.stepId()
                                + "' references unknown roleId: '"
                                + step.roleId()
                                + "'");
            }
        }

        // Validate dependsOn references
        for (PlannedStep step : steps) {
            List<String> deps = step.dependsOn() != null ? step.dependsOn() : List.of();
            for (String dep : deps) {
                if (!stepIds.contains(dep)) {
                    throw new PlanValidationException(
                            "Step '"
                                    + step.stepId()
                                    + "' depends on non-existent stepId: '"
                                    + dep
                                    + "'");
                }
            }
        }

        // Cycle detection via Kahn's algorithm
        detectCycles(steps);
    }

    /**
     * Kahn's algorithm for topological sort / cycle detection.
     *
     * @throws PlanValidationException if the graph contains a cycle
     */
    static void detectCycles(List<PlannedStep> steps) {
        // Build adjacency and in-degree
        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (PlannedStep step : steps) {
            adjacency.put(step.stepId(), new ArrayList<>());
            inDegree.put(step.stepId(), 0);
        }

        for (PlannedStep step : steps) {
            List<String> deps = step.dependsOn() != null ? step.dependsOn() : List.of();
            for (String dep : deps) {
                // edge: dep -> step.stepId (dep must finish before step)
                adjacency.get(dep).add(step.stepId());
                inDegree.merge(step.stepId(), 1, Integer::sum);
            }
        }

        // Enqueue nodes with in-degree 0
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        int processed = 0;
        while (!queue.isEmpty()) {
            String node = queue.poll();
            processed++;
            for (String neighbor : adjacency.get(node)) {
                int newDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newDegree);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (processed != steps.size()) {
            throw new PlanValidationException(
                    "DAG contains a cycle (processed "
                            + processed
                            + " of "
                            + steps.size()
                            + " steps)");
        }
    }

    // ------------------------------------------------------------------ conversion

    private TeamExecutionPlan convertToPlan(List<PlannedStep> steps) {
        List<TeamStep> teamSteps = new ArrayList<>(steps.size());
        for (int i = 0; i < steps.size(); i++) {
            PlannedStep ps = steps.get(i);
            ExpertProfile profile =
                    registry.resolve(ps.roleId())
                            .orElseThrow(
                                    () ->
                                            new PlanValidationException(
                                                    "roleId '"
                                                            + ps.roleId()
                                                            + "' not found in registry"));
            List<String> deps = ps.dependsOn() != null ? ps.dependsOn() : List.of();
            teamSteps.add(
                    new TeamStep(ps.stepId(), ps.instruction(), profile.roleDefinition(), deps, i));
        }
        return new TeamExecutionPlan(
                "plan-llm-" + UUID.randomUUID(), List.copyOf(teamSteps), Instant.now());
    }

    // ------------------------------------------------------------------ deterministic fallback

    private TeamExecutionPlan deterministicPlan(TeamExecutionRequest request, Team team) {
        List<RoleDefinition> roles;

        if (useRoleMatching && configuredRoles.isEmpty()) {
            List<Agent> agents = team.agents();
            int maxRoles = Math.max(1, agents.size());
            RoleMatcher matcher = new RoleMatcher(registry);
            List<RoleMatchResult> lineup = matcher.selectLineup(request.goal(), maxRoles);

            if (!lineup.isEmpty()) {
                roles = new ArrayList<>(lineup.size());
                for (RoleMatchResult r : lineup) {
                    roles.add(r.profile().roleDefinition());
                }
            } else {
                roles = resolveRoles(team);
            }
        } else {
            roles = resolveRoles(team);
        }

        if (roles.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot plan: no roles configured and team has no agents to derive roles from");
        }

        List<TeamStep> steps = new ArrayList<>(roles.size());
        for (int i = 0; i < roles.size(); i++) {
            RoleDefinition role = roles.get(i);
            String stepId = "step-" + (i + 1) + "-" + role.roleId();
            String description =
                    "Execute role '" + role.roleName() + "' for goal: " + summarise(request.goal());
            steps.add(new TeamStep(stepId, description, role, List.of(), i));
        }
        return new TeamExecutionPlan(
                "plan-" + UUID.randomUUID(), List.copyOf(steps), Instant.now());
    }

    // ------------------------------------------------------------------ helpers

    private RoleDefinition firstResolvableRole(Team team) {
        List<RoleDefinition> roles = resolveRoles(team);
        if (roles.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot build fallback plan: no roles configured and team has no agents");
        }
        return roles.get(0);
    }

    private static String summarise(String goal) {
        if (goal.length() <= 120) {
            return goal;
        }
        return goal.substring(0, 117) + "...";
    }

    // ------------------------------------------------------------------ internal types

    /** Internal record for JSON parsing of LLM-planned steps. */
    public record PlannedStep(
            String stepId, String roleId, String instruction, List<String> dependsOn) {
        public PlannedStep {
            dependsOn = dependsOn != null ? List.copyOf(dependsOn) : List.of();
        }
    }

    /** Thrown when LLM plan parsing or DAG validation fails. */
    public static final class PlanValidationException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public PlanValidationException(String message) {
            super(message);
        }
    }
}
