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
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kairo.api.team.TeamConfig;
import io.kairo.api.team.TeamExecutionRequest;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * L2 team self-evolution CLOSED-LOOP (recall side) regression test. A recalled team-collaboration
 * pattern must actually reach the planner — {@code ExpertTeamCoordinator#requestForPlanning}
 * augments the planning goal with recalled compositions, which {@code DefaultPlanner} then puts in
 * the planner LLM prompt. This guards against the L1-style "wired but silently a no-op" failure.
 */
class TeamPatternRecallLoopTest {

    private static TeamExecutionRequest req(String goal) {
        return new TeamExecutionRequest("req-1", goal, Map.of(), TeamConfig.defaults());
    }

    private static TeamExecutionRequest requestForPlanning(
            ExpertTeamCoordinator coord, TeamExecutionRequest request) throws Exception {
        Method m =
                ExpertTeamCoordinator.class.getDeclaredMethod(
                        "requestForPlanning", TeamExecutionRequest.class);
        m.setAccessible(true);
        return (TeamExecutionRequest) m.invoke(coord, request);
    }

    @Test
    @DisplayName("recalled pattern is injected into the planning goal (recall → planner prompt)")
    void recallAugmentsPlanningGoal(@TempDir Path dir) throws Exception {
        TeamPatternStore store = new TeamPatternStore(dir);
        store.record(
                        new TeamPattern(
                                "design a REST API for the users service",
                                List.of("expert:architect", "expert:coder"),
                                "serial",
                                true,
                                1.0,
                                Instant.now()))
                .block();

        ExpertTeamCoordinator coord = new ExpertTeamCoordinator(null);
        coord.setTeamPatternStore(store);

        TeamExecutionRequest planReq =
                requestForPlanning(coord, req("create a REST API users endpoint"));

        // The augmented goal must carry the recalled composition into the planner prompt.
        assertTrue(planReq.goal().contains("Learned team compositions"),
                "planning goal should include the recalled-compositions section");
        assertTrue(planReq.goal().contains("expert:architect"),
                "planning goal should include the recalled role sequence");
        // Original goal is preserved.
        assertTrue(planReq.goal().contains("create a REST API users endpoint"));
    }

    @Test
    @DisplayName("no store wired → request passes through unchanged (L2 disabled)")
    void noStorePassThrough() throws Exception {
        ExpertTeamCoordinator coord = new ExpertTeamCoordinator(null);
        TeamExecutionRequest original = req("do something");
        TeamExecutionRequest out = requestForPlanning(coord, original);
        assertEquals(original.goal(), out.goal());
    }

    @Test
    @DisplayName("store wired but no relevant pattern → goal unchanged (no spurious injection)")
    void noMatchPassThrough(@TempDir Path dir) throws Exception {
        TeamPatternStore store = new TeamPatternStore(dir);
        ExpertTeamCoordinator coord = new ExpertTeamCoordinator(null);
        coord.setTeamPatternStore(store);
        TeamExecutionRequest out = requestForPlanning(coord, req("write a haiku"));
        // Empty store → recall returns nothing → goal must be the original, no marker section.
        assertEquals("write a haiku", out.goal());
    }
}
