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
package io.kairo.tools.agent;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.agent.Agent;
import io.kairo.api.message.Msg;
import io.kairo.api.team.MessageBus;
import io.kairo.api.team.Team;
import io.kairo.api.team.TeamManager;
import io.kairo.api.tool.ToolResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class TeamDeleteToolTest {

    private StubTeamManager teamManager;
    private TeamDeleteTool tool;

    @BeforeEach
    void setUp() {
        teamManager = new StubTeamManager();
        tool = new TeamDeleteTool(teamManager);
    }

    @Test
    void nullNameReturnsError() {
        Map<String, Object> input = new java.util.HashMap<>();
        input.put("name", null);
        ToolResult result = tool.execute(input);
        assertTrue(result.isError());
        assertTrue(result.content().contains("'name' is required"));
    }

    @Test
    void blankNameReturnsError() {
        ToolResult result = tool.execute(Map.of("name", "   "));
        assertTrue(result.isError());
        assertTrue(result.content().contains("'name' is required"));
    }

    @Test
    void missingNameReturnsError() {
        ToolResult result = tool.execute(Map.of());
        assertTrue(result.isError());
    }

    @Test
    void validNameCallsDelete() {
        tool.execute(Map.of("name", "my-team"));
        assertEquals(1, teamManager.deletedNames.size());
        assertEquals("my-team", teamManager.deletedNames.get(0));
    }

    @Test
    void successResultIsNotError() {
        ToolResult result = tool.execute(Map.of("name", "my-team"));
        assertFalse(result.isError());
    }

    @Test
    void successContentMentionsTeamName() {
        ToolResult result = tool.execute(Map.of("name", "epsilon-team"));
        assertTrue(result.content().contains("epsilon-team"));
    }

    static class StubTeamManager implements TeamManager {
        final List<String> createdNames = new ArrayList<>();
        final List<String> deletedNames = new ArrayList<>();

        private static final MessageBus NOOP_BUS =
                new MessageBus() {
                    @Override
                    public Mono<Void> send(String fromAgentId, String toAgentId, Msg message) {
                        return Mono.empty();
                    }

                    @Override
                    public Flux<Msg> receive(String agentId) {
                        return Flux.empty();
                    }

                    @Override
                    public Mono<Void> broadcast(String fromAgentId, Msg message) {
                        return Mono.empty();
                    }
                };

        @Override
        public Team create(String name) {
            createdNames.add(name);
            return new Team(name, List.of(), NOOP_BUS);
        }

        @Override
        public void delete(String name) {
            deletedNames.add(name);
        }

        @Override
        public Team get(String name) {
            return new Team(name, List.of(), NOOP_BUS);
        }

        @Override
        public void addAgent(String teamName, Agent agent) {}

        @Override
        public void removeAgent(String teamName, String agentId) {}
    }
}
