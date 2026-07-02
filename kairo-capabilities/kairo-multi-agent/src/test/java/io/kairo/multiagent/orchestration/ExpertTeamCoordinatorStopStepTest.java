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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.kairo.api.agent.Agent;
import io.kairo.api.event.KairoEventBus;
import io.kairo.api.team.Team;
import io.kairo.api.team.TeamExecutionRequest;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ExpertTeamCoordinator#stopStep} — per-step stop/interrupt. Seeds the
 * private {@code activeStepAgents} map via reflection (same pattern as {@link
 * ExpertTeamCoordinatorSteerTest}).
 */
class ExpertTeamCoordinatorStopStepTest {

    @SuppressWarnings("unchecked")
    private static void putActiveStep(
            ExpertTeamCoordinator coord,
            String stepId,
            Agent agent,
            Team team,
            TeamExecutionRequest req)
            throws Exception {
        Field f = ExpertTeamCoordinator.class.getDeclaredField("activeStepAgents");
        f.setAccessible(true);
        Map<String, Object> map = (ConcurrentHashMap<String, Object>) f.get(coord);
        Class<?> activeStepCls =
                Class.forName("io.kairo.multiagent.orchestration.ExpertTeamCoordinator$ActiveStep");
        Constructor<?> ctor =
                activeStepCls.getDeclaredConstructor(
                        Agent.class, Team.class, TeamExecutionRequest.class);
        ctor.setAccessible(true);
        map.put(stepId, ctor.newInstance(agent, team, req));
    }

    private static TeamExecutionRequest req() {
        TeamExecutionRequest r = mock(TeamExecutionRequest.class);
        when(r.requestId()).thenReturn("req-1");
        return r;
    }

    private static Team team() {
        Team t = mock(Team.class);
        when(t.name()).thenReturn("team-1");
        return t;
    }

    @Test
    @DisplayName("stopStep interrupts the running worker agent")
    void stopStep_interruptsWorker() throws Exception {
        ExpertTeamCoordinator coord = new ExpertTeamCoordinator(mock(KairoEventBus.class));
        Agent agent = mock(Agent.class);
        putActiveStep(coord, "step-1", agent, team(), req());

        boolean stopped = coord.stopStep("step-1");

        assertTrue(stopped);
        verify(agent).interrupt();
    }

    @Test
    @DisplayName("stopStep returns false for a step that is not running")
    void stopStep_nonexistentReturnsFalse() {
        ExpertTeamCoordinator coord = new ExpertTeamCoordinator(mock(KairoEventBus.class));

        assertFalse(coord.stopStep("no-such-step"));
    }

    @Test
    @DisplayName("stopStep returns false for null/blank stepId")
    void stopStep_nullOrBlankReturnsFalse() {
        ExpertTeamCoordinator coord = new ExpertTeamCoordinator(mock(KairoEventBus.class));

        assertFalse(coord.stopStep(null));
        assertFalse(coord.stopStep(""));
        assertFalse(coord.stopStep("   "));
    }

    @Test
    @DisplayName("stopStep does not affect other running steps")
    void stopStep_doesNotAffectOtherSteps() throws Exception {
        ExpertTeamCoordinator coord = new ExpertTeamCoordinator(mock(KairoEventBus.class));
        Agent agent1 = mock(Agent.class);
        Agent agent2 = mock(Agent.class);
        putActiveStep(coord, "step-1", agent1, team(), req());
        putActiveStep(coord, "step-2", agent2, team(), req());

        coord.stopStep("step-1");

        verify(agent1).interrupt();
        verify(agent2, never()).interrupt();
    }
}
