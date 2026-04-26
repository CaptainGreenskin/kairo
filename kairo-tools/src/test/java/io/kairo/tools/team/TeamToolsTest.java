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
package io.kairo.tools.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.kairo.api.team.TeamManager;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolResult;
import io.kairo.tools.agent.EnterPlanModeTool;
import io.kairo.tools.agent.TeamCreateTool;
import io.kairo.tools.agent.TeamDeleteTool;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TeamToolsTest {

    private TeamManager teamManager;

    @BeforeEach
    void setUp() {
        teamManager = mock(TeamManager.class);
    }

    // ── TeamCreateTool ────────────────────────────────────────────────────────

    @Test
    void teamCreateTool_annotationNameNotNull() {
        Tool annotation = TeamCreateTool.class.getAnnotation(Tool.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.name()).isNotBlank();
    }

    @Test
    void teamCreateTool_annotationDescriptionNotNull() {
        Tool annotation = TeamCreateTool.class.getAnnotation(Tool.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.description()).isNotBlank();
    }

    @Test
    void teamCreateTool_constructorDoesNotThrow() {
        TeamCreateTool tool = new TeamCreateTool(teamManager);
        assertThat(tool).isNotNull();
    }

    @Test
    void teamCreateTool_missingNameReturnsError() {
        TeamCreateTool tool = new TeamCreateTool(teamManager);
        ToolResult result = tool.execute(Map.of());
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("'name' is required");
    }

    @Test
    void teamCreateTool_blankNameReturnsError() {
        TeamCreateTool tool = new TeamCreateTool(teamManager);
        ToolResult result = tool.execute(Map.of("name", "  "));
        assertThat(result.isError()).isTrue();
    }

    @Test
    void teamCreateTool_validNameCreatesTeam() {
        TeamCreateTool tool = new TeamCreateTool(teamManager);
        ToolResult result = tool.execute(Map.of("name", "alpha"));
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("alpha");
        verify(teamManager).create("alpha");
    }

    @Test
    void teamCreateTool_metadataContainsTeamName() {
        TeamCreateTool tool = new TeamCreateTool(teamManager);
        ToolResult result = tool.execute(Map.of("name", "beta"));
        assertThat(result.metadata()).containsEntry("teamName", "beta");
    }

    // ── TeamDeleteTool ────────────────────────────────────────────────────────

    @Test
    void teamDeleteTool_annotationNameNotNull() {
        Tool annotation = TeamDeleteTool.class.getAnnotation(Tool.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.name()).isNotBlank();
    }

    @Test
    void teamDeleteTool_annotationDescriptionNotNull() {
        Tool annotation = TeamDeleteTool.class.getAnnotation(Tool.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.description()).isNotBlank();
    }

    @Test
    void teamDeleteTool_constructorDoesNotThrow() {
        TeamDeleteTool tool = new TeamDeleteTool(teamManager);
        assertThat(tool).isNotNull();
    }

    @Test
    void teamDeleteTool_missingNameReturnsError() {
        TeamDeleteTool tool = new TeamDeleteTool(teamManager);
        ToolResult result = tool.execute(Map.of());
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("'name' is required");
    }

    @Test
    void teamDeleteTool_validNameDeletesTeam() {
        TeamDeleteTool tool = new TeamDeleteTool(teamManager);
        ToolResult result = tool.execute(Map.of("name", "alpha"));
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("alpha");
        verify(teamManager).delete("alpha");
    }

    // ── EnterPlanModeTool ─────────────────────────────────────────────────────

    @Test
    void enterPlanModeTool_annotationNameNotNull() {
        Tool annotation = EnterPlanModeTool.class.getAnnotation(Tool.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.name()).isNotBlank();
    }

    @Test
    void enterPlanModeTool_annotationDescriptionNotNull() {
        Tool annotation = EnterPlanModeTool.class.getAnnotation(Tool.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.description()).isNotBlank();
    }

    @Test
    void enterPlanModeTool_defaultConstructorDoesNotThrow() {
        EnterPlanModeTool tool = new EnterPlanModeTool();
        assertThat(tool).isNotNull();
    }

    @Test
    void enterPlanModeTool_executeWithoutDependenciesSucceeds() {
        EnterPlanModeTool tool = new EnterPlanModeTool();
        ToolResult result = tool.execute(Map.of());
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("Plan Mode");
    }

    @Test
    void enterPlanModeTool_executeWithNameSucceeds() {
        EnterPlanModeTool tool = new EnterPlanModeTool();
        ToolResult result = tool.execute(Map.of("name", "My Plan"));
        assertThat(result.isError()).isFalse();
    }

    @Test
    void enterPlanModeTool_metadataContainsPlanMode() {
        EnterPlanModeTool tool = new EnterPlanModeTool();
        ToolResult result = tool.execute(Map.of());
        assertThat(result.metadata()).containsEntry("mode", "plan");
    }
}
