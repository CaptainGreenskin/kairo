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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.team.Team;
import io.kairo.api.team.TeamManager;
import io.kairo.api.tool.ToolResult;
import io.kairo.multiagent.team.InProcessMessageBus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TeamCreateTool} and {@link TeamDeleteTool}.
 *
 * <p>Uses a simple in-memory stub TeamManager to avoid Mockito concrete-class limitations on Java
 * 25 (Byte Buddy does not support that JVM version).
 */
class TeamToolsTest {

    /** Minimal in-memory TeamManager stub. */
    private static class StubTeamManager implements TeamManager {
        final List<String> created = new ArrayList<>();
        final List<String> deleted = new ArrayList<>();

        @Override
        public Team create(String name) {
            created.add(name);
            return new Team(name, List.of(), new InProcessMessageBus());
        }

        @Override
        public void delete(String name) {
            deleted.add(name);
        }

        @Override
        public Team get(String name) {
            return new Team(name, List.of(), new InProcessMessageBus());
        }

        @Override
        public void addAgent(String teamName, io.kairo.api.agent.Agent agent) {}

        @Override
        public void removeAgent(String teamName, String agentId) {}
    }

    private StubTeamManager teamManager;
    private TeamCreateTool createTool;
    private TeamDeleteTool deleteTool;

    @BeforeEach
    void setUp() {
        teamManager = new StubTeamManager();
        createTool = new TeamCreateTool(teamManager);
        deleteTool = new TeamDeleteTool(teamManager);
    }

    // --- TeamCreateTool ---

    @Test
    void createWithValidNameSucceeds() {
        ToolResult result = createTool.execute(Map.of("name", "alpha"));
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("alpha");
    }

    @Test
    void createDelegatesToTeamManager() {
        createTool.execute(Map.of("name", "bravo"));
        assertThat(teamManager.created).containsExactly("bravo");
    }

    @Test
    void createMetadataContainsTeamName() {
        ToolResult result = createTool.execute(Map.of("name", "charlie"));
        assertThat(result.metadata()).containsEntry("teamName", "charlie");
    }

    @Test
    void createWithEmptyNameReturnsError() {
        ToolResult result = createTool.execute(Map.of("name", ""));
        assertThat(result.isError()).isTrue();
        assertThat(teamManager.created).isEmpty();
    }

    @Test
    void createWithNullNameReturnsError() {
        ToolResult result = createTool.execute(Map.of());
        assertThat(result.isError()).isTrue();
    }

    // --- TeamDeleteTool ---

    @Test
    void deleteWithValidNameSucceeds() {
        ToolResult result = deleteTool.execute(Map.of("name", "delta"));
        assertThat(result.isError()).isFalse();
        assertThat(teamManager.deleted).containsExactly("delta");
    }

    @Test
    void deleteWithEmptyNameReturnsError() {
        ToolResult result = deleteTool.execute(Map.of("name", ""));
        assertThat(result.isError()).isTrue();
        assertThat(teamManager.deleted).isEmpty();
    }
}
