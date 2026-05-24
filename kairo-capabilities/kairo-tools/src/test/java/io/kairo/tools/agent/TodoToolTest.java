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

import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolResult;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TodoToolTest {

    private static final ToolContext CTX = new ToolContext("agent-1", "sess-1", Map.of());

    @TempDir Path workspaceRoot;

    private TodoWriteTool writer;
    private TodoReadTool reader;

    private ToolResult write(Map<String, Object> args) {
        return writer.execute(args, CTX).block();
    }

    private ToolResult read(Map<String, Object> args) {
        return reader.execute(args, CTX).block();
    }

    @BeforeEach
    void setUp() {
        writer = new TodoWriteTool(workspaceRoot);
        reader = new TodoReadTool(workspaceRoot);
    }

    @Test
    void writeCreatesTodosFile() {
        String todos =
                """
                [{"id":"1","content":"Fix bug","status":"pending","priority":"high"}]
                """;
        ToolResult result = write(Map.of("todos", todos));
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).isEqualTo("Wrote 1 todo(s)");
        assertThat(workspaceRoot.resolve(".kairo/todos.json")).exists();
    }

    @Test
    void readReturnsPreviouslyWrittenTodos() {
        String todos =
                "[{\"id\":\"1\",\"content\":\"Task A\",\"status\":\"pending\",\"priority\":\"high\"},"
                        + "{\"id\":\"2\",\"content\":\"Task B\",\"status\":\"completed\",\"priority\":\"low\"}]";
        write(Map.of("todos", todos));

        ToolResult result = read(Map.of());
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("Task A");
        assertThat(result.content()).contains("Task B");
    }

    @Test
    void readReturnsEmptyListWhenNoFileExists() {
        ToolResult result = read(Map.of());
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).isEqualTo("[]");
        assertThat(result.metadata()).containsEntry("count", 0);
    }

    @Test
    void writeEmptyListClearsFile() {
        write(
                Map.of(
                        "todos",
                        "[{\"id\":\"1\",\"content\":\"X\",\"status\":\"pending\",\"priority\":\"low\"}]"));
        ToolResult result = write(Map.of("todos", "[]"));
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).isEqualTo("Wrote 0 todo(s)");

        ToolResult readResult = read(Map.of());
        assertThat(readResult.content().trim()).isEqualTo("[ ]");
    }

    @Test
    void writeInvalidJsonReturnsError() {
        ToolResult result = write(Map.of("todos", "not-json"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("Invalid JSON");
    }

    @Test
    void writeMissingTodosParamReturnsError() {
        ToolResult result = write(Map.of());
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("'todos' is required");
    }

    @Test
    void writeInvalidStatusReturnsError() {
        String todos =
                "[{\"id\":\"1\",\"content\":\"X\",\"status\":\"unknown\",\"priority\":\"high\"}]";
        ToolResult result = write(Map.of("todos", todos));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("Invalid status");
    }

    @Test
    void writeMultipleTodosReportsCount() {
        String todos =
                "[{\"id\":\"1\",\"content\":\"A\",\"status\":\"pending\",\"priority\":\"high\"},"
                        + "{\"id\":\"2\",\"content\":\"B\",\"status\":\"in_progress\",\"priority\":\"medium\"},"
                        + "{\"id\":\"3\",\"content\":\"C\",\"status\":\"completed\",\"priority\":\"low\"}]";
        ToolResult result = write(Map.of("todos", todos));
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).isEqualTo("Wrote 3 todo(s)");
        assertThat(result.metadata()).containsEntry("count", 3);
    }
}
