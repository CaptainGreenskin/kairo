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
package io.kairo.tools.memory;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.workspace.Workspace;
import io.kairo.api.workspace.WorkspaceKind;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TeamMemoryToolsTest {

    @TempDir Path tempDir;

    private ToolContext ctx;

    @BeforeEach
    void setUp() {
        Workspace workspace =
                new Workspace() {
                    @Override
                    public String id() {
                        return "test";
                    }

                    @Override
                    public Path root() {
                        return tempDir;
                    }

                    @Override
                    public WorkspaceKind kind() {
                        return WorkspaceKind.LOCAL;
                    }

                    @Override
                    public Map<String, String> metadata() {
                        return Map.of();
                    }
                };
        ctx = new ToolContext("test-agent", "test-session", Map.of(), null, null, workspace);
    }

    // -- TeamMemoryWriteTool --

    @Test
    void writeCreatesTeamMemory() {
        TeamMemoryWriteTool tool = new TeamMemoryWriteTool(tempDir);
        ToolResult result =
                tool.execute(
                                Map.of(
                                        "teamName", "alpha",
                                        "name", "explored-auth",
                                        "description", "Auth module explored",
                                        "type", "project",
                                        "content", "Found potential XSS in login."),
                                ctx)
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("Created");
        assertThat(result.content()).contains("explored-auth");
        assertThat(result.content()).contains("alpha");
        assertThat(result.metadata()).containsEntry("action", "Created");
        assertThat(result.metadata()).containsEntry("teamName", "alpha");
    }

    @Test
    void writeUpdatesExistingTeamMemory() {
        TeamMemoryWriteTool tool = new TeamMemoryWriteTool(tempDir);
        tool.execute(
                        Map.of(
                                "teamName", "alpha",
                                "name", "finding-1",
                                "description", "v1",
                                "type", "project",
                                "content", "first version"),
                        ctx)
                .block();

        ToolResult result =
                tool.execute(
                                Map.of(
                                        "teamName", "alpha",
                                        "name", "finding-1",
                                        "description", "v2",
                                        "type", "project",
                                        "content", "updated version"),
                                ctx)
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("Updated");
        assertThat(result.metadata()).containsEntry("action", "Updated");
    }

    @Test
    void writeMissingTeamName() {
        TeamMemoryWriteTool tool = new TeamMemoryWriteTool(tempDir);
        ToolResult result =
                tool.execute(
                                Map.of(
                                        "name", "test",
                                        "description", "desc",
                                        "type", "project",
                                        "content", "body"),
                                ctx)
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("teamName");
    }

    @Test
    void writeMissingName() {
        TeamMemoryWriteTool tool = new TeamMemoryWriteTool(tempDir);
        ToolResult result =
                tool.execute(
                                Map.of(
                                        "teamName", "alpha",
                                        "description", "desc",
                                        "type", "project",
                                        "content", "body"),
                                ctx)
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("name");
    }

    @Test
    void writeInvalidType() {
        TeamMemoryWriteTool tool = new TeamMemoryWriteTool(tempDir);
        ToolResult result =
                tool.execute(
                                Map.of(
                                        "teamName", "alpha",
                                        "name", "test",
                                        "description", "desc",
                                        "type", "INVALID",
                                        "content", "body"),
                                ctx)
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("Invalid type");
    }

    // -- TeamMemoryReadTool --

    @Test
    void readByName() {
        TeamMemoryWriteTool writeTool = new TeamMemoryWriteTool(tempDir);
        writeTool
                .execute(
                        Map.of(
                                "teamName", "beta",
                                "name", "auth-finding",
                                "description", "Auth module finding",
                                "type", "project",
                                "content", "XSS vulnerability in login form."),
                        ctx)
                .block();

        TeamMemoryReadTool readTool = new TeamMemoryReadTool(tempDir);
        ToolResult result =
                readTool.execute(Map.of("teamName", "beta", "name", "auth-finding"), ctx).block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("auth-finding");
        assertThat(result.content()).contains("XSS vulnerability");
        assertThat(result.content()).contains("Team: beta");
        assertThat(result.metadata()).containsEntry("name", "auth-finding");
        assertThat(result.metadata()).containsEntry("teamName", "beta");
    }

    @Test
    void readByNameNotFound() {
        TeamMemoryReadTool readTool = new TeamMemoryReadTool(tempDir);
        ToolResult result =
                readTool.execute(Map.of("teamName", "beta", "name", "nonexistent"), ctx).block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("No memory found");
        assertThat(result.content()).contains("beta");
    }

    @Test
    void readByQuery() {
        TeamMemoryWriteTool writeTool = new TeamMemoryWriteTool(tempDir);
        writeTool
                .execute(
                        Map.of(
                                "teamName", "gamma",
                                "name", "db-issue",
                                "description", "Database connection pool exhaustion",
                                "type", "project",
                                "content", "The connection pool runs out under load."),
                        ctx)
                .block();
        writeTool
                .execute(
                        Map.of(
                                "teamName", "gamma",
                                "name", "api-review",
                                "description", "REST API design review",
                                "type", "feedback",
                                "content", "APIs follow REST conventions."),
                        ctx)
                .block();

        TeamMemoryReadTool readTool = new TeamMemoryReadTool(tempDir);
        ToolResult result =
                readTool.execute(Map.of("teamName", "gamma", "query", "database connection"), ctx)
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("db-issue");
    }

    @Test
    void readIndex() {
        TeamMemoryWriteTool writeTool = new TeamMemoryWriteTool(tempDir);
        writeTool
                .execute(
                        Map.of(
                                "teamName", "delta",
                                "name", "indexed-mem",
                                "description", "An indexed memory",
                                "type", "user",
                                "content", "some body"),
                        ctx)
                .block();

        TeamMemoryReadTool readTool = new TeamMemoryReadTool(tempDir);
        ToolResult result = readTool.execute(Map.of("teamName", "delta"), ctx).block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("indexed-mem");
        assertThat(result.content()).contains("An indexed memory");
    }

    @Test
    void readIndexEmpty() {
        TeamMemoryReadTool readTool = new TeamMemoryReadTool(tempDir);
        ToolResult result = readTool.execute(Map.of("teamName", "empty-team"), ctx).block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("No team memories stored");
    }

    @Test
    void readMissingTeamName() {
        TeamMemoryReadTool readTool = new TeamMemoryReadTool(tempDir);
        ToolResult result = readTool.execute(Map.of(), ctx).block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("teamName");
    }

    // -- TeamMemoryDeleteTool --

    @Test
    void deleteSuccess() {
        TeamMemoryWriteTool writeTool = new TeamMemoryWriteTool(tempDir);
        writeTool
                .execute(
                        Map.of(
                                "teamName", "epsilon",
                                "name", "to-delete",
                                "description", "Will be deleted",
                                "type", "project",
                                "content", "temp content"),
                        ctx)
                .block();

        TeamMemoryDeleteTool deleteTool = new TeamMemoryDeleteTool(tempDir);
        ToolResult result =
                deleteTool.execute(Map.of("teamName", "epsilon", "name", "to-delete"), ctx).block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("Deleted");
        assertThat(result.content()).contains("to-delete");
        assertThat(result.content()).contains("epsilon");
    }

    @Test
    void deleteNotFound() {
        TeamMemoryDeleteTool deleteTool = new TeamMemoryDeleteTool(tempDir);
        ToolResult result =
                deleteTool
                        .execute(Map.of("teamName", "epsilon", "name", "nonexistent"), ctx)
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("No memory found");
    }

    @Test
    void deleteMissingTeamName() {
        TeamMemoryDeleteTool deleteTool = new TeamMemoryDeleteTool(tempDir);
        ToolResult result = deleteTool.execute(Map.of("name", "test"), ctx).block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("teamName");
    }

    @Test
    void deleteMissingName() {
        TeamMemoryDeleteTool deleteTool = new TeamMemoryDeleteTool(tempDir);
        ToolResult result = deleteTool.execute(Map.of("teamName", "epsilon"), ctx).block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("name");
    }

    // -- Cross-team isolation --

    @Test
    void teamsAreIsolated() {
        TeamMemoryWriteTool writeTool = new TeamMemoryWriteTool(tempDir);
        writeTool
                .execute(
                        Map.of(
                                "teamName", "team-a",
                                "name", "shared-finding",
                                "description", "Team A finding",
                                "type", "project",
                                "content", "Team A content"),
                        ctx)
                .block();

        TeamMemoryReadTool readTool = new TeamMemoryReadTool(tempDir);
        ToolResult teamBResult =
                readTool.execute(Map.of("teamName", "team-b", "name", "shared-finding"), ctx)
                        .block();

        assertThat(teamBResult).isNotNull();
        assertThat(teamBResult.isError()).isTrue();
        assertThat(teamBResult.content()).contains("No memory found");

        ToolResult teamAResult =
                readTool.execute(Map.of("teamName", "team-a", "name", "shared-finding"), ctx)
                        .block();

        assertThat(teamAResult).isNotNull();
        assertThat(teamAResult.isError()).isFalse();
        assertThat(teamAResult.content()).contains("Team A content");
    }
}
