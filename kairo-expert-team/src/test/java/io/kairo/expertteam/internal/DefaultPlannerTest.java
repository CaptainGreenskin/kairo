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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.agent.Agent;
import io.kairo.api.team.EvaluatorPreference;
import io.kairo.api.team.PlannerFailureMode;
import io.kairo.api.team.RiskProfile;
import io.kairo.api.team.RoleDefinition;
import io.kairo.api.team.Team;
import io.kairo.api.team.TeamConfig;
import io.kairo.api.team.TeamExecutionPlan;
import io.kairo.api.team.TeamExecutionRequest;
import io.kairo.api.team.TeamResourceConstraint;
import io.kairo.expertteam.tck.NoopMessageBus;
import io.kairo.expertteam.tck.StubAgent;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class DefaultPlannerTest {

    private final DefaultPlanner planner = new DefaultPlanner();

    @Test
    void derivesOneRolePerAgentWhenNoRolesConfigured() {
        Agent alice = StubAgent.fixed("alice", "ok");
        Agent bob = StubAgent.fixed("bob", "ok");
        Team team = new Team("pair", List.of(alice, bob), new NoopMessageBus());

        TeamExecutionPlan plan = planner.plan(sampleRequest(), team);

        assertThat(plan.steps()).hasSize(2);
        assertThat(plan.steps().get(0).assignedRole().roleName()).isEqualTo("alice");
        assertThat(plan.steps().get(1).assignedRole().roleName()).isEqualTo("bob");
        assertThat(plan.steps().get(0).stepIndex()).isEqualTo(0);
        assertThat(plan.steps().get(1).stepIndex()).isEqualTo(1);
    }

    @Test
    void usesConfiguredRolesWhenProvided() {
        RoleDefinition reviewer =
                new RoleDefinition(
                        "reviewer", "Reviewer", "Review the artifact.", "text.review", List.of());
        RoleDefinition scribe =
                new RoleDefinition(
                        "scribe", "Scribe", "Write the summary.", "text.write", List.of());
        DefaultPlanner configured = new DefaultPlanner(List.of(scribe, reviewer));

        Agent agent = StubAgent.fixed("only-agent", "ok");
        Team team = new Team("solo", List.of(agent), new NoopMessageBus());

        TeamExecutionPlan plan = configured.plan(sampleRequest(), team);
        assertThat(plan.steps()).hasSize(2);
        assertThat(plan.steps().get(0).assignedRole()).isEqualTo(scribe);
        assertThat(plan.steps().get(1).assignedRole()).isEqualTo(reviewer);
    }

    @Test
    void failsWhenNoRolesAndNoAgents() {
        Team empty = new Team("empty", List.of(), new NoopMessageBus());
        assertThatThrownBy(() -> planner.plan(sampleRequest(), empty))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no roles");
    }

    @Test
    void singleStepFallbackCollapsesToOneStep() {
        Agent agent = StubAgent.fixed("solo", "ok");
        Team team = new Team("t", List.of(agent), new NoopMessageBus());
        TeamExecutionPlan plan = planner.singleStepFallback(sampleRequest(), team);
        assertThat(plan.steps()).hasSize(1);
        assertThat(plan.steps().get(0).stepId()).startsWith("step-1-");
    }

    @Test
    void bindRolesMapsEachStepRoleToAnAgent() {
        Agent alice = StubAgent.fixed("alice", "ok");
        Agent bob = StubAgent.fixed("bob", "ok");
        Team team = new Team("pair", List.of(alice, bob), new NoopMessageBus());
        TeamExecutionPlan plan = planner.plan(sampleRequest(), team);

        Map<String, Agent> bindings = planner.bindRoles(team, plan);
        assertThat(bindings).hasSize(2);
        assertThat(bindings).containsValues(alice, bob);
    }

    @Test
    void bindRolesFailsWhenTeamHasNoAgents() {
        Agent alice = StubAgent.fixed("alice", "ok");
        Team populated = new Team("pair", List.of(alice), new NoopMessageBus());
        TeamExecutionPlan plan = planner.plan(sampleRequest(), populated);

        Team empty = new Team("empty", List.of(), new NoopMessageBus());
        assertThatThrownBy(() -> planner.bindRoles(empty, plan))
                .isInstanceOf(IllegalStateException.class);
    }

    private TeamExecutionRequest sampleRequest() {
        return new TeamExecutionRequest(
                "req-1",
                "write something",
                Map.of(),
                new TeamConfig(
                        RiskProfile.MEDIUM,
                        2,
                        Duration.ofSeconds(5L),
                        EvaluatorPreference.SIMPLE,
                        PlannerFailureMode.FAIL_FAST,
                        TeamResourceConstraint.unbounded()));
    }
}
