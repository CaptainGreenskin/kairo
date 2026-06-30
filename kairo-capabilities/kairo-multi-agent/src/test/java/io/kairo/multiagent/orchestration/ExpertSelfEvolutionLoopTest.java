/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.multiagent.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kairo.api.team.EvaluationVerdict;
import io.kairo.api.team.EvaluationVerdict.VerdictOutcome;
import io.kairo.api.team.RoleDefinition;
import io.kairo.api.team.TeamExecutionPlan;
import io.kairo.api.team.TeamResult.StepOutcome;
import io.kairo.api.team.TeamStep;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * L1 self-evolution CLOSED-LOOP regression test. The write side ({@link
 * ExpertTeamCoordinator#groupOutcomesByRole}) must key lessons by the STABLE roleId so the read
 * side ({@code DefaultGenerator#appendPriorLessons}, which recalls by roleId via {@link
 * ExpertMemoryStore#recall}) actually finds them. Before the fix, the write side keyed by the
 * per-task requestId, so written lessons were never recalled (silent no-op).
 */
class ExpertSelfEvolutionLoopTest {

    private static RoleDefinition role(String id) {
        return new RoleDefinition(id, id, "instr", "agent.default", List.of());
    }

    private static TeamStep step(String stepId, String roleId, int idx) {
        return new TeamStep(stepId, "do " + stepId, role(roleId), List.of(), idx);
    }

    private static StepOutcome outcome(String stepId) {
        EvaluationVerdict v =
                new EvaluationVerdict(VerdictOutcome.PASS, 1.0, "ok", List.of(), Instant.now());
        return new StepOutcome(stepId, "output for " + stepId, v, 1);
    }

    @Test
    @DisplayName("outcomes are grouped by STABLE roleId, not the per-task requestId")
    void groupsByRoleNotRequestId() {
        TeamExecutionPlan plan =
                new TeamExecutionPlan(
                        "plan-1",
                        List.of(step("s1", "expert:coder", 0), step("s2", "expert:reviewer", 1)),
                        Instant.now());
        Map<String, List<StepOutcome>> byRole =
                ExpertTeamCoordinator.groupOutcomesByRole(
                        List.of(outcome("s1"), outcome("s2")), plan);

        // Keys must be the stable role ids the recall side reads by — never a UUID/requestId.
        assertEquals(2, byRole.size());
        assertTrue(byRole.containsKey("expert:coder"));
        assertTrue(byRole.containsKey("expert:reviewer"));
        assertFalse(byRole.keySet().stream().anyMatch(k -> k.contains("-") && k.length() > 20));
    }

    @Test
    @DisplayName("outcomes for an unmapped step (no plan role) are dropped, not misattributed")
    void dropsUnmappedOutcomes() {
        TeamExecutionPlan plan =
                new TeamExecutionPlan(
                        "plan-1", List.of(step("s1", "expert:coder", 0)), Instant.now());
        Map<String, List<StepOutcome>> byRole =
                ExpertTeamCoordinator.groupOutcomesByRole(
                        List.of(outcome("s1"), outcome("s-unknown")), plan);
        assertEquals(1, byRole.size());
        assertEquals(1, byRole.get("expert:coder").size());
    }

    @Test
    @DisplayName("null plan → nothing to attribute → empty grouping (no crash)")
    void nullPlanEmpty() {
        assertTrue(
                ExpertTeamCoordinator.groupOutcomesByRole(List.of(outcome("s1")), null).isEmpty());
    }

    @Test
    @DisplayName("closed loop: a lesson written under a roleId is recalled by the SAME roleId")
    void writeAndRecallRoundTripByRole(@TempDir Path dir) {
        ExpertMemoryStore store = new ExpertMemoryStore(dir);
        String roleId = "expert:coder";
        store.recordLessons(
                        roleId,
                        roleId,
                        List.of(
                                new ExpertMemoryEntry(
                                        roleId,
                                        roleId,
                                        "Prefer the write tool over bash echo for file creation.",
                                        Instant.now(),
                                        0.9)))
                .block();

        List<ExpertMemoryEntry> recalled = store.recall(roleId, roleId, 5).collectList().block();
        assertEquals(1, recalled.size());
        assertTrue(recalled.get(0).lesson().contains("write tool"));
    }
}
