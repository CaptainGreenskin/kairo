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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.kairo.api.team.TeamManager;
import io.kairo.api.tool.ToolResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TeamDeleteToolTest {

    @Test
    void missingNameIsError() {
        TeamDeleteTool tool = new TeamDeleteTool(mock(TeamManager.class));
        ToolResult result = tool.execute(Map.of());
        assertThat(result.isError()).isTrue();
    }

    @Test
    void blankNameIsError() {
        TeamDeleteTool tool = new TeamDeleteTool(mock(TeamManager.class));
        ToolResult result = tool.execute(Map.of("name", ""));
        assertThat(result.isError()).isTrue();
    }

    @Test
    void validNameCallsTeamManagerDelete() {
        TeamManager manager = mock(TeamManager.class);
        TeamDeleteTool tool = new TeamDeleteTool(manager);
        tool.execute(Map.of("name", "beta"));
        verify(manager).delete("beta");
    }

    @Test
    void validNameIsNotError() {
        TeamDeleteTool tool = new TeamDeleteTool(mock(TeamManager.class));
        ToolResult result = tool.execute(Map.of("name", "beta"));
        assertThat(result.isError()).isFalse();
    }

    @Test
    void validNameContentMentionsTeamName() {
        TeamDeleteTool tool = new TeamDeleteTool(mock(TeamManager.class));
        ToolResult result = tool.execute(Map.of("name", "old-team"));
        assertThat(result.content()).contains("old-team");
    }
}
