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
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.team.EvaluatorPreference;
import io.kairo.api.team.PlannerFailureMode;
import io.kairo.api.team.RiskProfile;
import io.kairo.api.team.RoleDefinition;
import io.kairo.api.team.Team;
import io.kairo.api.team.TeamConfig;
import io.kairo.api.team.TeamExecutionPlan;
import io.kairo.api.team.TeamExecutionRequest;
import io.kairo.api.team.TeamResourceConstraint;
import io.kairo.api.team.TeamStep;
import io.kairo.expertteam.internal.DefaultPlanner.PlanValidationException;
import io.kairo.expertteam.internal.DefaultPlanner.PlannedStep;
import io.kairo.expertteam.role.ExpertRoleRegistry;
import io.kairo.expertteam.tck.NoopMessageBus;
import io.kairo.expertteam.tck.StubAgent;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

final class DefaultPlannerTest {

    private final ExpertRoleRegistry registry = new ExpertRoleRegistry();

    // ------------------------------------------------------------------ DAG parsing tests

    @Test
    void parsesValidDagFromLlmResponse() {
        String json =
                """
                [
                  {"stepId": "research", "roleId": "expert:researcher", "instruction": "Gather info", "dependsOn": []},
                  {"stepId": "implement", "roleId": "expert:coder", "instruction": "Write code", "dependsOn": ["research"]},
                  {"stepId": "review", "roleId": "expert:reviewer", "instruction": "Review code", "dependsOn": ["implement"]}
                ]
                """;

        Agent planAgent =
                new StubAgent("planner", msg -> Mono.just(Msg.of(MsgRole.ASSISTANT, json)));
        DefaultPlanner planner = new DefaultPlanner(registry, planAgent, List.of());

        TeamExecutionPlan plan = planner.plan(sampleRequest(), sampleTeam());

        assertThat(plan.steps()).hasSize(3);
        assertThat(plan.steps().get(0).stepId()).isEqualTo("research");
        assertThat(plan.steps().get(0).dependsOn()).isEmpty();
        assertThat(plan.steps().get(1).stepId()).isEqualTo("implement");
        assertThat(plan.steps().get(1).dependsOn()).containsExactly("research");
        assertThat(plan.steps().get(2).stepId()).isEqualTo("review");
        assertThat(plan.steps().get(2).dependsOn()).containsExactly("implement");
    }

    @Test
    void parsesJsonWithMarkdownFences() {
        String response =
                """
                ```json
                [{"stepId": "s1", "roleId": "expert:coder", "instruction": "Do it", "dependsOn": []}]
                ```
                """;

        List<PlannedStep> steps = DefaultPlanner.parseLlmResponse(response);
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).stepId()).isEqualTo("s1");
    }

    // ------------------------------------------------------------------ cycle detection tests

    @Test
    void cycleDetectionRejectsCircularDependencies() {
        List<PlannedStep> cyclic =
                List.of(
                        new PlannedStep("a", "expert:coder", "Step A", List.of("b")),
                        new PlannedStep("b", "expert:coder", "Step B", List.of("a")));

        assertThatThrownBy(() -> DefaultPlanner.detectCycles(cyclic))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void cycleDetectionAcceptsValidDag() {
        List<PlannedStep> valid =
                List.of(
                        new PlannedStep("a", "expert:coder", "Step A", List.of()),
                        new PlannedStep("b", "expert:coder", "Step B", List.of("a")),
                        new PlannedStep("c", "expert:coder", "Step C", List.of("a", "b")));

        // Should not throw
        DefaultPlanner.detectCycles(valid);
    }

    @Test
    void cycleDetectionRejectsThreeNodeCycle() {
        List<PlannedStep> cyclic =
                List.of(
                        new PlannedStep("a", "expert:coder", "A", List.of("c")),
                        new PlannedStep("b", "expert:coder", "B", List.of("a")),
                        new PlannedStep("c", "expert:coder", "C", List.of("b")));

        assertThatThrownBy(() -> DefaultPlanner.detectCycles(cyclic))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("cycle");
    }

    // ------------------------------------------------------------------ dangling reference tests

    @Test
    void danglingDependsOnReferenceRejected() {
        DefaultPlanner planner = new DefaultPlanner(registry, null, List.of());
        List<PlannedStep> steps =
                List.of(new PlannedStep("s1", "expert:coder", "Do work", List.of("non-existent")));

        assertThatThrownBy(() -> planner.validateDag(steps))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("non-existent");
    }

    // ------------------------------------------------------------------ unknown roleId tests

    @Test
    void unknownRoleIdRejected() {
        DefaultPlanner planner = new DefaultPlanner(registry, null, List.of());
        List<PlannedStep> steps =
                List.of(new PlannedStep("s1", "expert:nonexistent", "Do work", List.of()));

        assertThatThrownBy(() -> planner.validateDag(steps))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("unknown roleId")
                .hasMessageContaining("expert:nonexistent");
    }

    // ------------------------------------------------------------------ fallback tests

    @Test
    void fallbackToDeterministicOnLlmFailure() {
        Agent failingAgent =
                new StubAgent(
                        "planner", msg -> Mono.error(new RuntimeException("LLM unavailable")));
        DefaultPlanner planner = new DefaultPlanner(registry, failingAgent, List.of());

        Team team = sampleTeam();
        TeamExecutionPlan plan = planner.plan(sampleRequest(), team);

        // Should fall back to deterministic: one step per agent in the team
        assertThat(plan.steps()).isNotEmpty();
        assertThat(plan.planId()).startsWith("plan-");
    }

    @Test
    void fallbackToDeterministicOnInvalidJson() {
        Agent badJsonAgent =
                new StubAgent(
                        "planner",
                        msg -> Mono.just(Msg.of(MsgRole.ASSISTANT, "This is not JSON at all")));
        DefaultPlanner planner = new DefaultPlanner(registry, badJsonAgent, List.of());

        Team team = sampleTeam();
        TeamExecutionPlan plan = planner.plan(sampleRequest(), team);

        // Should fall back to deterministic
        assertThat(plan.steps()).isNotEmpty();
    }

    // ------------------------------------------------------------------ retry tests

    @Test
    void retriesWithErrorFeedbackOnFirstFailure() {
        AtomicInteger callCount = new AtomicInteger(0);
        String validJson =
                """
                [{"stepId": "s1", "roleId": "expert:coder", "instruction": "code", "dependsOn": []}]
                """;

        Agent retryAgent =
                new StubAgent(
                        "planner",
                        msg -> {
                            int call = callCount.incrementAndGet();
                            if (call == 1) {
                                return Mono.just(Msg.of(MsgRole.ASSISTANT, "not valid json"));
                            }
                            // Second call should contain error feedback
                            assertThat(msg.text()).contains("Previous Attempt Failed");
                            return Mono.just(Msg.of(MsgRole.ASSISTANT, validJson));
                        });

        DefaultPlanner planner = new DefaultPlanner(registry, retryAgent, List.of());
        TeamExecutionPlan plan = planner.plan(sampleRequest(), sampleTeam());

        assertThat(callCount.get()).isEqualTo(2);
        assertThat(plan.steps()).hasSize(1);
        assertThat(plan.steps().get(0).stepId()).isEqualTo("s1");
    }

    // ------------------------------------------------------------------ backward compat tests

    @Test
    void deterministicPlannerDerivesOneRolePerAgent() {
        DefaultPlanner planner = new DefaultPlanner();
        Agent alice = StubAgent.fixed("alice", "ok");
        Agent bob = StubAgent.fixed("bob", "ok");
        Team team = new Team("pair", List.of(alice, bob), new NoopMessageBus());

        TeamExecutionPlan plan = planner.plan(sampleRequest(), team);

        assertThat(plan.steps()).hasSize(2);
        assertThat(plan.steps().get(0).assignedRole().roleName()).isEqualTo("alice");
        assertThat(plan.steps().get(1).assignedRole().roleName()).isEqualTo("bob");
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
        DefaultPlanner planner = new DefaultPlanner();
        Team empty = new Team("empty", List.of(), new NoopMessageBus());
        assertThatThrownBy(() -> planner.plan(sampleRequest(), empty))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no roles");
    }

    @Test
    void singleStepFallbackCollapsesToOneStep() {
        DefaultPlanner planner = new DefaultPlanner();
        Agent agent = StubAgent.fixed("solo", "ok");
        Team team = new Team("t", List.of(agent), new NoopMessageBus());
        TeamExecutionPlan plan = planner.singleStepFallback(sampleRequest(), team);
        assertThat(plan.steps()).hasSize(1);
        assertThat(plan.steps().get(0).stepId()).startsWith("step-1-");
    }

    @Test
    void bindRolesMapsEachStepRoleToAnAgent() {
        DefaultPlanner planner = new DefaultPlanner();
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
        DefaultPlanner planner = new DefaultPlanner();
        Agent alice = StubAgent.fixed("alice", "ok");
        Team populated = new Team("pair", List.of(alice), new NoopMessageBus());
        TeamExecutionPlan plan = planner.plan(sampleRequest(), populated);

        Team empty = new Team("empty", List.of(), new NoopMessageBus());
        assertThatThrownBy(() -> planner.bindRoles(empty, plan))
                .isInstanceOf(IllegalStateException.class);
    }

    // ------------------------------------------------------------------ validation edge cases

    @Test
    void emptyStepListRejected() {
        DefaultPlanner planner = new DefaultPlanner(registry, null, List.of());
        assertThatThrownBy(() -> planner.validateDag(List.of()))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("at least one step");
    }

    @Test
    void duplicateStepIdsRejected() {
        DefaultPlanner planner = new DefaultPlanner(registry, null, List.of());
        List<PlannedStep> steps =
                List.of(
                        new PlannedStep("dup", "expert:coder", "First", List.of()),
                        new PlannedStep("dup", "expert:coder", "Second", List.of()));

        assertThatThrownBy(() -> planner.validateDag(steps))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("Duplicate stepId");
    }

    @Test
    void parallelDagStepsPreserveDependencies() {
        String json =
                """
                [
                  {"stepId": "root", "roleId": "expert:architect", "instruction": "Plan", "dependsOn": []},
                  {"stepId": "code-a", "roleId": "expert:coder", "instruction": "Module A", "dependsOn": ["root"]},
                  {"stepId": "code-b", "roleId": "expert:coder", "instruction": "Module B", "dependsOn": ["root"]},
                  {"stepId": "integrate", "roleId": "expert:synthesizer", "instruction": "Integrate", "dependsOn": ["code-a", "code-b"]}
                ]
                """;

        Agent planAgent =
                new StubAgent("planner", msg -> Mono.just(Msg.of(MsgRole.ASSISTANT, json)));
        DefaultPlanner planner = new DefaultPlanner(registry, planAgent, List.of());

        TeamExecutionPlan plan = planner.plan(sampleRequest(), sampleTeam());

        assertThat(plan.steps()).hasSize(4);
        TeamStep integrate = plan.steps().get(3);
        assertThat(integrate.dependsOn()).containsExactlyInAnyOrder("code-a", "code-b");
    }

    // ------------------------------------------------------------------ prompt builder tests

    @Test
    void buildPlanningPromptIncludesGoalAndRoles() {
        List<io.kairo.expertteam.role.ExpertProfile> profiles = registry.allProfiles();
        String prompt =
                LlmPlannerPrompt.buildPlanningPrompt("Build a REST API", profiles, List.of());

        assertThat(prompt).contains("Build a REST API");
        assertThat(prompt).contains("expert:coder");
        assertThat(prompt).contains("DAG");
        assertThat(prompt).contains("JSON array");
    }

    @Test
    void buildRetryPromptAppendsError() {
        String original = "Original prompt";
        String retry = LlmPlannerPrompt.buildRetryPrompt(original, "Invalid JSON at line 2");

        assertThat(retry).startsWith(original);
        assertThat(retry).contains("Previous Attempt Failed");
        assertThat(retry).contains("Invalid JSON at line 2");
    }

    // ------------------------------------------------------------------ helpers

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

    private Team sampleTeam() {
        Agent agent = StubAgent.fixed("agent-1", "ok");
        return new Team("test-team", List.of(agent), new NoopMessageBus());
    }
}
