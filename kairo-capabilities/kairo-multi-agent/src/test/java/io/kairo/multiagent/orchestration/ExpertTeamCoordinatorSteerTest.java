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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.kairo.api.agent.Agent;
import io.kairo.api.event.KairoEventBus;
import io.kairo.api.message.Msg;
import io.kairo.api.team.Team;
import io.kairo.api.team.TeamExecutionRequest;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ExpertTeamCoordinator#steer} — the v2 mid-flight steering entry point.
 * Seeds the private {@code activeStepAgents} map via reflection (it is normally populated only
 * during a live worker {@code generate(...)} window) so the steer routing can be tested in
 * isolation without running a full team execution.
 */
class ExpertTeamCoordinatorSteerTest {

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
    @DisplayName("blank directive is a no-op and returns false")
    void blankDirectiveNoOp() {
        ExpertTeamCoordinator coord = new ExpertTeamCoordinator(mock(KairoEventBus.class));
        assertFalse(coord.steer(null, "   "));
        assertFalse(coord.steer("step-1", ""));
    }

    @Test
    @DisplayName("steer with no active steps returns false (caller falls back to queue)")
    void noActiveStepsMiss() {
        ExpertTeamCoordinator coord = new ExpertTeamCoordinator(mock(KairoEventBus.class));
        assertFalse(coord.steer(null, "do X"));
        assertFalse(coord.steer("nonexistent", "do X"));
    }

    @Test
    @DisplayName("targeted steer injects the directive into the matching active worker")
    void targetedSteerInjects() throws Exception {
        ExpertTeamCoordinator coord = new ExpertTeamCoordinator(mock(KairoEventBus.class));
        Agent agent = mock(Agent.class);
        putActiveStep(coord, "step-1", agent, team(), req());

        boolean hit = coord.steer("step-1", "also add a header comment");

        assertTrue(hit);
        verify(agent).injectMessages(anyList());
    }

    @Test
    @DisplayName("targeted steer for an inactive step does not touch other workers")
    void targetedSteerWrongStepMisses() throws Exception {
        ExpertTeamCoordinator coord = new ExpertTeamCoordinator(mock(KairoEventBus.class));
        Agent agent = mock(Agent.class);
        putActiveStep(coord, "step-1", agent, team(), req());

        boolean hit = coord.steer("other-step", "x");

        assertFalse(hit);
        verify(agent, never()).injectMessages(anyList());
    }

    @Test
    @DisplayName("broadcast steer (null stepId) injects into all active workers")
    void broadcastSteerInjectsAll() throws Exception {
        ExpertTeamCoordinator coord = new ExpertTeamCoordinator(mock(KairoEventBus.class));
        Agent a1 = mock(Agent.class);
        Agent a2 = mock(Agent.class);
        putActiveStep(coord, "step-1", a1, team(), req());
        putActiveStep(coord, "step-2", a2, team(), req());

        boolean hit = coord.steer(null, "parallel directive");

        assertTrue(hit);
        verify(a1).injectMessages(anyList());
        verify(a2).injectMessages(anyList());
    }

    @Test
    @DisplayName("every steer is recorded for subsequent steps (belt-and-suspenders)")
    @SuppressWarnings("unchecked")
    void steerRecordedForSubsequentSteps() throws Exception {
        ExpertTeamCoordinator coord = new ExpertTeamCoordinator(mock(KairoEventBus.class));
        // No active step: steer "misses" live injection but must still be recorded so an upcoming
        // step's worker picks it up via augmentGoal.
        coord.steer(null, "use tabs not spaces");
        Field f = ExpertTeamCoordinator.class.getDeclaredField("steerDirectives");
        f.setAccessible(true);
        List<String> recorded = (List<String>) f.get(coord);
        assertTrue(recorded.contains("use tabs not spaces"));
    }

    @Test
    @DisplayName("injected directive carries the user text as a USER message")
    void injectedMessageContent() throws Exception {
        ExpertTeamCoordinator coord = new ExpertTeamCoordinator(mock(KairoEventBus.class));
        Agent agent = mock(Agent.class);
        putActiveStep(coord, "step-1", agent, team(), req());

        coord.steer("step-1", "RENAME the function to foo");

        org.mockito.ArgumentCaptor<List<Msg>> cap = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(agent).injectMessages(cap.capture());
        List<Msg> injected = cap.getValue();
        assertTrue(injected.size() == 1);
        assertTrue(injected.get(0).text().contains("RENAME the function to foo"));
    }
}
