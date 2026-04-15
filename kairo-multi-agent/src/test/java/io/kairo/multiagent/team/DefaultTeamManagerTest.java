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
package io.kairo.multiagent.team;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.kairo.api.agent.Agent;
import io.kairo.api.team.Team;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultTeamManagerTest {

    private DefaultTeamManager manager;

    @BeforeEach
    void setUp() {
        manager = new DefaultTeamManager();
    }

    @Test
    void createTeamShouldReturnTeamWithGivenName() {
        Team team = manager.create("alpha");
        assertNotNull(team);
        assertEquals("alpha", team.name());
        assertNotNull(team.taskBoard());
        assertNotNull(team.messageBus());
        assertTrue(team.agents().isEmpty());
    }

    @Test
    void getTeamShouldReturnCreatedTeam() {
        manager.create("beta");
        Team team = manager.get("beta");
        assertNotNull(team);
        assertEquals("beta", team.name());
    }

    @Test
    void getTeamShouldReturnNullForNonExistent() {
        assertNull(manager.get("ghost"));
    }

    @Test
    void deleteTeamShouldRemoveIt() {
        manager.create("gamma");
        assertNotNull(manager.get("gamma"));
        manager.delete("gamma");
        assertNull(manager.get("gamma"));
    }

    @Test
    void deleteNonExistentTeamShouldNotThrow() {
        assertDoesNotThrow(() -> manager.delete("nonexistent"));
    }

    @Test
    void deleteTeamShouldInterruptAgents() {
        Team team = manager.create("delta");
        Agent agent = mock(Agent.class);
        when(agent.id()).thenReturn("agent-1");
        team.agents().add(agent);

        manager.delete("delta");
        verify(agent).interrupt();
    }

    @Test
    void addAgentShouldAddToTeam() {
        manager.create("echo");
        Agent agent = mock(Agent.class);
        when(agent.id()).thenReturn("agent-2");

        manager.addAgent("echo", agent);

        Team team = manager.get("echo");
        assertEquals(1, team.agents().size());
        assertEquals("agent-2", team.agents().get(0).id());
    }

    @Test
    void addAgentToNonExistentTeamShouldThrow() {
        Agent agent = mock(Agent.class);
        assertThrows(IllegalArgumentException.class, () -> manager.addAgent("noTeam", agent));
    }

    @Test
    void removeAgentShouldRemoveFromTeam() {
        manager.create("foxtrot");
        Agent agent = mock(Agent.class);
        when(agent.id()).thenReturn("agent-3");
        manager.addAgent("foxtrot", agent);
        assertEquals(1, manager.get("foxtrot").agents().size());

        manager.removeAgent("foxtrot", "agent-3");
        assertTrue(manager.get("foxtrot").agents().isEmpty());
    }

    @Test
    void removeAgentFromNonExistentTeamShouldThrow() {
        assertThrows(
                IllegalArgumentException.class, () -> manager.removeAgent("noTeam", "agent-x"));
    }

    @Test
    void addMultipleAgentsToTeam() {
        manager.create("golf");
        Agent a1 = mock(Agent.class);
        when(a1.id()).thenReturn("a1");
        Agent a2 = mock(Agent.class);
        when(a2.id()).thenReturn("a2");

        manager.addAgent("golf", a1);
        manager.addAgent("golf", a2);

        assertEquals(2, manager.get("golf").agents().size());
    }
}
