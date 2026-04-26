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
import static org.mockito.Mockito.*;

import io.kairo.api.team.TeamManager;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolResult;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TeamDeleteToolTest {

    private TeamManager teamManager;
    private TeamDeleteTool tool;

    @BeforeEach
    void setUp() {
        teamManager = mock(TeamManager.class);
        tool = new TeamDeleteTool(teamManager);
    }

    @Test
    void toolAnnotationName() {
        Tool annotation = TeamDeleteTool.class.getAnnotation(Tool.class);
        assertEquals("team_delete", annotation.name());
    }

    @Test
    void toolAnnotationCategory() {
        Tool annotation = TeamDeleteTool.class.getAnnotation(Tool.class);
        assertEquals(ToolCategory.AGENT_AND_TASK, annotation.category());
    }

    @Test
    void executeMissingNameReturnsError() {
        ToolResult result = tool.execute(Map.of());
        assertTrue(result.isError());
        assertTrue(result.content().contains("'name' is required"));
    }

    @Test
    void executeBlankNameReturnsError() {
        ToolResult result = tool.execute(Map.of("name", ""));
        assertTrue(result.isError());
    }

    @Test
    void executeDeletesTeamViaManager() {
        tool.execute(Map.of("name", "alpha"));
        verify(teamManager).delete("alpha");
    }

    @Test
    void executeSuccessNotError() {
        ToolResult result = tool.execute(Map.of("name", "alpha"));
        assertFalse(result.isError());
    }

    @Test
    void executeResponseTextContainsTeamName() {
        ToolResult result = tool.execute(Map.of("name", "gamma"));
        assertTrue(result.content().contains("gamma"));
    }
}
