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

import io.kairo.api.a2a.AgentCardResolver;
import io.kairo.api.agent.Agent;
import io.kairo.api.team.Team;
import io.kairo.api.team.TeamCreateRequest;
import io.kairo.api.team.TeamLifecycleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DefaultTeamManagerTest {

    private DefaultTeamManager manager;

    @BeforeEach
    void setUp() {
        manager = new DefaultTeamManager();
    }

    @Test
    void createTeamShouldReturnTeamWithGivenName() {
        Team team = manager.create(TeamCreateRequest.of("alpha", "test goal"));
        assertNotNull(team);
        assertEquals("alpha", team.name());
        assertEquals("test goal", team.goal());
        assertTrue(team.teamId().startsWith("team-"));
        assertEquals(TeamLifecycleStatus.INITIALIZING, team.status());
        assertNotNull(team.messageBus());
        assertTrue(team.agents().isEmpty());
    }

    @Test
    void getTeamShouldReturnCreatedTeam() {
        Team created = manager.create("beta");
        Team team = manager.get(created.teamId());
        assertNotNull(team);
        assertEquals("beta", team.name());
    }

    @Test
    void getTeamShouldReturnNullForNonExistent() {
        assertNull(manager.get("ghost"));
    }

    @Test
    void deleteTeamShouldRemoveIt() {
        Team created = manager.create("gamma");
        assertNotNull(manager.get(created.teamId()));
        manager.delete(created.teamId());
        assertNull(manager.get(created.teamId()));
    }

    @Test
    void deleteNonExistentTeamShouldNotThrow() {
        assertDoesNotThrow(() -> manager.delete("nonexistent"));
    }

    @Test
    void deleteTeamShouldInterruptAgents() {
        Team created = manager.create("delta");
        Agent agent = mock(Agent.class);
        when(agent.id()).thenReturn("agent-1");
        when(agent.name()).thenReturn("agent-1");
        manager.addAgent(created.teamId(), agent);

        manager.delete(created.teamId());
        verify(agent).interrupt();
    }

    @Test
    void addAgentShouldAddToTeam() {
        Team created = manager.create("echo");
        Agent agent = mock(Agent.class);
        when(agent.id()).thenReturn("agent-2");

        manager.addAgent(created.teamId(), agent);

        Team team = manager.get(created.teamId());
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
        Team created = manager.create("foxtrot");
        Agent agent = mock(Agent.class);
        when(agent.id()).thenReturn("agent-3");
        manager.addAgent(created.teamId(), agent);
        assertEquals(1, manager.get(created.teamId()).agents().size());

        manager.removeAgent(created.teamId(), "agent-3");
        assertTrue(manager.get(created.teamId()).agents().isEmpty());
    }

    @Test
    void removeAgentFromNonExistentTeamShouldThrow() {
        assertThrows(
                IllegalArgumentException.class, () -> manager.removeAgent("noTeam", "agent-x"));
    }

    @Test
    void addMultipleAgentsToTeam() {
        Team created = manager.create("golf");
        Agent a1 = mock(Agent.class);
        when(a1.id()).thenReturn("a1");
        Agent a2 = mock(Agent.class);
        when(a2.id()).thenReturn("a2");

        manager.addAgent(created.teamId(), a1);
        manager.addAgent(created.teamId(), a2);

        assertEquals(2, manager.get(created.teamId()).agents().size());
    }

    @Test
    void listActiveShouldReturnActiveTeams() {
        Team t1 = manager.create(TeamCreateRequest.of("team-a", "goal a"));
        Team t2 = manager.create(TeamCreateRequest.of("team-b", "goal b"));
        manager.updateStatus(t1.teamId(), TeamLifecycleStatus.ACTIVE);
        manager.updateStatus(t2.teamId(), TeamLifecycleStatus.COMPLETED);

        var active = manager.listActive();
        assertEquals(1, active.size());
        assertEquals("team-a", active.get(0).name());
    }

    @Test
    void updateStatusShouldChangeTeamStatus() {
        Team created = manager.create("status-test");
        assertEquals(TeamLifecycleStatus.INITIALIZING, manager.get(created.teamId()).status());

        manager.updateStatus(created.teamId(), TeamLifecycleStatus.ACTIVE);
        assertEquals(TeamLifecycleStatus.ACTIVE, manager.get(created.teamId()).status());
    }

    @Nested
    @DisplayName("A2A AgentCard integration")
    class AgentCardIntegration {

        private AgentCardResolver resolver;
        private DefaultTeamManager managerWithResolver;

        @BeforeEach
        void setUp() {
            resolver = mock(AgentCardResolver.class);
            managerWithResolver = new DefaultTeamManager(resolver);
        }

        @Test
        @DisplayName("addAgent registers AgentCard when resolver is present")
        void addAgentRegistersCard() {
            Team created = managerWithResolver.create("team-a2a");
            Agent agent = mock(Agent.class);
            when(agent.id()).thenReturn("agent-x");
            when(agent.name()).thenReturn("Agent X");

            managerWithResolver.addAgent(created.teamId(), agent);

            verify(resolver)
                    .register(
                            argThat(
                                    card ->
                                            card.id().equals("agent-x")
                                                    && card.name().equals("Agent X")));
        }

        @Test
        @DisplayName("removeAgent unregisters AgentCard when resolver is present")
        void removeAgentUnregistersCard() {
            Team created = managerWithResolver.create("team-a2a");
            Agent agent = mock(Agent.class);
            when(agent.id()).thenReturn("agent-y");
            when(agent.name()).thenReturn("Agent Y");
            managerWithResolver.addAgent(created.teamId(), agent);

            managerWithResolver.removeAgent(created.teamId(), "agent-y");

            verify(resolver).unregister("agent-y");
        }

        @Test
        @DisplayName("no-arg constructor works without resolver (backward compatible)")
        void noArgConstructorBackwardCompatible() {
            DefaultTeamManager plain = new DefaultTeamManager();
            Team created = plain.create("plain-team");
            Agent agent = mock(Agent.class);
            when(agent.id()).thenReturn("a1");
            when(agent.name()).thenReturn("A1");

            assertDoesNotThrow(() -> plain.addAgent(created.teamId(), agent));
            assertDoesNotThrow(() -> plain.removeAgent(created.teamId(), "a1"));
        }

        @Test
        @DisplayName("null resolver constructor works without resolver")
        void nullResolverConstructor() {
            DefaultTeamManager nullResolver = new DefaultTeamManager(null);
            Team created = nullResolver.create("null-team");
            Agent agent = mock(Agent.class);
            when(agent.id()).thenReturn("a2");
            when(agent.name()).thenReturn("A2");

            assertDoesNotThrow(() -> nullResolver.addAgent(created.teamId(), agent));
            assertDoesNotThrow(() -> nullResolver.removeAgent(created.teamId(), "a2"));
        }
    }
}
