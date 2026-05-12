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
import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.agent.Agent;
import io.kairo.api.team.Team;
import io.kairo.api.team.TeamManager;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolResult;
import io.kairo.multiagent.team.InProcessMessageBus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for {@link TeamCreateTool}.
 *
 * <p>Uses an in-memory stub TeamManager to avoid Mockito concrete-class limitations on modern JVMs.
 */
class TeamCreateToolTest {

    /** In-memory TeamManager stub that records create calls. */
    private static class StubTeamManager implements TeamManager {
        final List<String> created = new ArrayList<>();
        boolean shouldThrow = false;
        RuntimeException exceptionToThrow;

        @Override
        public Team create(String name) {
            if (shouldThrow) {
                throw exceptionToThrow != null
                        ? exceptionToThrow
                        : new RuntimeException("create failed");
            }
            created.add(name);
            return new Team(name, List.of(), new InProcessMessageBus());
        }

        @Override
        public void delete(String name) {}

        @Override
        public Team get(String name) {
            return new Team(name, List.of(), new InProcessMessageBus());
        }

        @Override
        public void addAgent(String teamName, Agent agent) {}

        @Override
        public void removeAgent(String teamName, String agentId) {}
    }

    private StubTeamManager teamManager;
    private TeamCreateTool tool;
    private static final ToolContext CTX = new ToolContext("agent-1", "sess-1", Map.of());

    /** Helper to execute the tool synchronously. */
    private ToolResult exec(Map<String, Object> args) {
        return tool.execute(args, CTX).block();
    }

    @BeforeEach
    void setUp() {
        teamManager = new StubTeamManager();
        tool = new TeamCreateTool(teamManager);
    }

    // --- Happy path ---

    @Test
    void createWithValidName_succeeds() {
        ToolResult result = exec(Map.of("name", "alpha-team"));

        assertFalse(result.isError());
        assertThat(result.content()).contains("alpha-team");
        assertThat(teamManager.created).containsExactly("alpha-team");
    }

    @Test
    void createReturnsFormattedSuccessMessage() {
        ToolResult result = exec(Map.of("name", "my-team"));

        assertThat(result.content()).isEqualTo("Created team 'my-team'");
    }

    @Test
    void createDelegatesToTeamManager() {
        exec(Map.of("name", "bravo"));
        exec(Map.of("name", "charlie"));

        assertThat(teamManager.created).containsExactly("bravo", "charlie");
    }

    // --- Metadata ---

    @Test
    void metadataContainsTeamName() {
        ToolResult result = exec(Map.of("name", "delta"));

        assertThat(result.metadata()).containsEntry("teamName", "delta");
    }

    @Test
    void metadataTeamNameMatchesInput() {
        ToolResult result = exec(Map.of("name", "special-123"));

        assertThat(result.metadata().get("teamName")).isEqualTo("special-123");
    }

    @Test
    void toolUseIdIsNull() {
        ToolResult result = exec(Map.of("name", "echo"));

        assertNull(result.toolUseId());
    }

    // --- Missing / blank name ---

    @Test
    void missingNameReturnsError() {
        ToolResult result = exec(Map.of());

        assertTrue(result.isError());
        assertThat(result.content()).contains("name");
        assertThat(teamManager.created).isEmpty();
    }

    @Test
    void emptyNameReturnsError() {
        ToolResult result = exec(Map.of("name", ""));

        assertTrue(result.isError());
        assertThat(result.content()).containsIgnoringCase("required");
        assertThat(teamManager.created).isEmpty();
    }

    @Test
    void blankNameReturnsError() {
        ToolResult result = exec(Map.of("name", "   "));

        assertTrue(result.isError());
        assertThat(teamManager.created).isEmpty();
    }

    @Test
    void nullNameViaHashMapReturnsError() {
        Map<String, Object> input = new HashMap<>();
        input.put("name", null);

        ToolResult result = exec(input);

        assertTrue(result.isError());
        assertThat(teamManager.created).isEmpty();
    }

    // --- Error message quality ---

    @Test
    void errorMessageMentionsParameterName() {
        ToolResult result = exec(Map.of());

        assertThat(result.content()).contains("'name'");
    }

    @Test
    void errorResultHasEmptyMetadata() {
        ToolResult result = exec(Map.of("name", ""));

        assertThat(result.metadata()).isEmpty();
    }

    // --- Edge cases: names ---

    @Test
    void singleCharacterNameSucceeds() {
        ToolResult result = exec(Map.of("name", "x"));

        assertFalse(result.isError());
        assertThat(teamManager.created).containsExactly("x");
    }

    @Test
    void nameWithSpecialCharactersSucceeds() {
        ToolResult result = exec(Map.of("name", "team-alpha_v2.0"));

        assertFalse(result.isError());
        assertThat(result.content()).contains("team-alpha_v2.0");
    }

    @Test
    void nameWithSpacesSucceeds() {
        ToolResult result = exec(Map.of("name", "my team"));

        assertFalse(result.isError());
        assertThat(teamManager.created).containsExactly("my team");
    }

    @Test
    void nameWithUnicodeSucceeds() {
        ToolResult result = exec(Map.of("name", "团队-测试"));

        assertFalse(result.isError());
        assertThat(result.metadata()).containsEntry("teamName", "团队-测试");
    }

    @Test
    void longNameSucceeds() {
        String longName = "a".repeat(500);
        ToolResult result = exec(Map.of("name", longName));

        assertFalse(result.isError());
        assertThat(teamManager.created).containsExactly(longName);
    }

    // --- Invalid parameter types ---

    @Test
    void nonStringNameCausesCastException() {
        // The tool casts input.get("name") to (String), so a non-String value
        // will throw a ClassCastException
        assertThrows(ClassCastException.class, () -> exec(Map.of("name", 42)));
    }

    // --- Extra parameters are ignored ---

    @Test
    void extraParametersAreIgnored() {
        ToolResult result = exec(Map.of("name", "foxtrot", "description", "some description"));

        assertFalse(result.isError());
        assertThat(teamManager.created).containsExactly("foxtrot");
    }

    // --- TeamManager exception propagation ---

    @Test
    void teamManagerExceptionPropagates() {
        teamManager.shouldThrow = true;
        teamManager.exceptionToThrow = new RuntimeException("duplicate team name");

        assertThrows(RuntimeException.class, () -> exec(Map.of("name", "duplicate")));
        // TeamManager was never recorded (exception thrown before add)
        assertThat(teamManager.created).isEmpty();
    }
}
