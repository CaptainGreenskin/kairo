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

class MemoryToolsTest {

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

    // -- MemoryWriteTool --

    @Test
    void writeSuccess() {
        MemoryWriteTool tool = new MemoryWriteTool();
        ToolResult result =
                tool.execute(
                                Map.of(
                                        "name", "feedback-testing",
                                        "description", "Integration tests must hit real DB",
                                        "type", "feedback",
                                        "content", "The body content."),
                                ctx)
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("Created");
        assertThat(result.content()).contains("feedback-testing");
        assertThat(result.metadata()).containsEntry("action", "Created");
    }

    @Test
    void writeUpdate() {
        MemoryWriteTool tool = new MemoryWriteTool();
        tool.execute(
                        Map.of(
                                "name", "test-mem",
                                "description", "v1",
                                "type", "user",
                                "content", "first"),
                        ctx)
                .block();

        ToolResult result =
                tool.execute(
                                Map.of(
                                        "name", "test-mem",
                                        "description", "v2",
                                        "type", "user",
                                        "content", "second"),
                                ctx)
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("Updated");
        assertThat(result.metadata()).containsEntry("action", "Updated");
    }

    @Test
    void writeMissingName() {
        MemoryWriteTool tool = new MemoryWriteTool();
        ToolResult result =
                tool.execute(
                                Map.of(
                                        "description", "desc",
                                        "type", "user",
                                        "content", "body"),
                                ctx)
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("name");
    }

    @Test
    void writeMissingDescription() {
        MemoryWriteTool tool = new MemoryWriteTool();
        ToolResult result =
                tool.execute(
                                Map.of(
                                        "name", "test",
                                        "type", "user",
                                        "content", "body"),
                                ctx)
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("description");
    }

    @Test
    void writeMissingType() {
        MemoryWriteTool tool = new MemoryWriteTool();
        ToolResult result =
                tool.execute(
                                Map.of(
                                        "name", "test",
                                        "description", "desc",
                                        "content", "body"),
                                ctx)
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("type");
    }

    @Test
    void writeMissingContent() {
        MemoryWriteTool tool = new MemoryWriteTool();
        ToolResult result =
                tool.execute(
                                Map.of(
                                        "name", "test",
                                        "description", "desc",
                                        "type", "user"),
                                ctx)
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("content");
    }

    @Test
    void writeInvalidType() {
        MemoryWriteTool tool = new MemoryWriteTool();
        ToolResult result =
                tool.execute(
                                Map.of(
                                        "name", "test",
                                        "description", "desc",
                                        "type", "invalid",
                                        "content", "body"),
                                ctx)
                        .block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("Invalid type");
    }

    // -- MemoryReadTool --

    @Test
    void readByName() {
        new MemoryWriteTool()
                .execute(
                        Map.of(
                                "name", "test-read",
                                "description", "Test read",
                                "type", "user",
                                "content", "Body for reading."),
                        ctx)
                .block();

        MemoryReadTool tool = new MemoryReadTool();
        ToolResult result = tool.execute(Map.of("name", "test-read"), ctx).block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("test-read");
        assertThat(result.content()).contains("Body for reading.");
        assertThat(result.metadata()).containsEntry("name", "test-read");
    }

    @Test
    void readByNameNotFound() {
        MemoryReadTool tool = new MemoryReadTool();
        ToolResult result = tool.execute(Map.of("name", "nonexistent"), ctx).block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("No memory found");
    }

    @Test
    void readByQuery() {
        new MemoryWriteTool()
                .execute(
                        Map.of(
                                "name", "db-testing",
                                "description", "Database testing strategy",
                                "type", "feedback",
                                "content", "Use real database for integration tests."),
                        ctx)
                .block();
        new MemoryWriteTool()
                .execute(
                        Map.of(
                                "name", "api-design",
                                "description", "REST API guidelines",
                                "type", "project",
                                "content", "Follow RESTful conventions."),
                        ctx)
                .block();

        MemoryReadTool tool = new MemoryReadTool();
        ToolResult result = tool.execute(Map.of("query", "database testing"), ctx).block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("db-testing");
    }

    @Test
    void readIndex() {
        new MemoryWriteTool()
                .execute(
                        Map.of(
                                "name", "indexed",
                                "description", "An indexed memory",
                                "type", "user",
                                "content", "body"),
                        ctx)
                .block();

        MemoryReadTool tool = new MemoryReadTool();
        ToolResult result = tool.execute(Map.of(), ctx).block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("indexed.md");
        assertThat(result.content()).contains("An indexed memory");
    }

    @Test
    void readIndexEmpty() {
        MemoryReadTool tool = new MemoryReadTool();
        ToolResult result = tool.execute(Map.of(), ctx).block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("No memories stored");
    }

    // -- MemoryDeleteTool --

    @Test
    void deleteSuccess() {
        new MemoryWriteTool()
                .execute(
                        Map.of(
                                "name", "to-delete",
                                "description", "will delete",
                                "type", "user",
                                "content", "body"),
                        ctx)
                .block();

        MemoryDeleteTool tool = new MemoryDeleteTool();
        ToolResult result = tool.execute(Map.of("name", "to-delete"), ctx).block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("Deleted");
    }

    @Test
    void deleteNotFound() {
        MemoryDeleteTool tool = new MemoryDeleteTool();
        ToolResult result = tool.execute(Map.of("name", "nonexistent"), ctx).block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("No memory found");
    }

    @Test
    void deleteMissingName() {
        MemoryDeleteTool tool = new MemoryDeleteTool();
        ToolResult result = tool.execute(Map.of(), ctx).block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("name");
    }
}
