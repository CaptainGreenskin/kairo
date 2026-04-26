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

import io.kairo.api.agent.Agent;
import io.kairo.api.team.RoleDefinition;
import io.kairo.api.team.Team;
import io.kairo.api.team.TeamExecutionPlan;
import io.kairo.api.team.TeamExecutionRequest;
import io.kairo.api.team.TeamStep;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Deterministic planner used by {@link io.kairo.expertteam.ExpertTeamCoordinator} when no external
 * planner is supplied.
 *
 * <p>v0.10 MVP policy:
 *
 * <ul>
 *   <li>If the caller supplies an explicit role list, emit one {@link TeamStep} per role in order.
 *   <li>Otherwise, synthesise one {@link RoleDefinition} per agent in {@link Team#agents()} and
 *       emit a step per role.
 *   <li>If neither roles nor agents are available, throw — this is the ADR-015 fix #4 "fail at plan
 *       time, not mid-execution" contract.
 * </ul>
 *
 * <p>This planner does NOT invoke an LLM. An LLM-driven planner is a v0.10.1+ enhancement; the
 * current deterministic behaviour keeps the MVP auditable and reproducible.
 *
 * @since v0.10 (Experimental)
 */
public final class DefaultPlanner {

    private final List<RoleDefinition> configuredRoles;

    /** Planner that derives roles from {@link Team#agents()} at plan time. */
    public DefaultPlanner() {
        this(List.of());
    }

    /**
     * Planner with an explicit role list. When non-empty, the planner emits one step per role and
     * ignores {@link Team#agents()} for role derivation (the coordinator still needs agents bound
     * to roles at generation time).
     */
    public DefaultPlanner(List<RoleDefinition> configuredRoles) {
        this.configuredRoles = configuredRoles == null ? List.of() : List.copyOf(configuredRoles);
    }

    /** Produce a plan for the given request against the given team. */
    public TeamExecutionPlan plan(TeamExecutionRequest request, Team team) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(team, "team must not be null");

        List<RoleDefinition> roles = resolveRoles(team);
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

    /**
     * Build a role→agent binding map the coordinator will feed to the generator. For configured
     * roles the binding policy is "bind the role to the i-th agent". For derived roles the binding
     * is 1:1 with the agent that produced the role.
     */
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
}
