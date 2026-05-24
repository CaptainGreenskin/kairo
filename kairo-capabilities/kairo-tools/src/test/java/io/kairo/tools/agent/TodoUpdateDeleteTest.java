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

/**
 * Tests for update and delete workflows using {@link TodoWriteTool} and {@link TodoReadTool}.
 *
 * <p>{@link TodoWriteTool} is a full-replace tool: updating or deleting a single entry is done by
 * writing the modified full list back. These tests verify that pattern works correctly.
 */
class TodoUpdateDeleteTest {

    private static final ToolContext CTX = new ToolContext("agent-1", "sess-1", Map.of());

    @TempDir Path workspaceRoot;

    private TodoWriteTool writer;
    private TodoReadTool reader;

    /** Helper to execute write tool synchronously. */
    private ToolResult write(Map<String, Object> args) {
        return writer.execute(args, CTX).block();
    }

    /** Helper to execute read tool synchronously. */
    private ToolResult read(Map<String, Object> args) {
        return reader.execute(args, CTX).block();
    }

    @BeforeEach
    void setUp() {
        writer = new TodoWriteTool(workspaceRoot);
        reader = new TodoReadTool(workspaceRoot);
    }

    @Test
    void updateTodoStatusByReplacingList() {
        // Initial write with status pending
        write(
                Map.of(
                        "todos",
                        "[{\"id\":\"1\",\"content\":\"task A\",\"status\":\"pending\",\"priority\":\"high\"}]"));

        // Update: write back with status completed
        var updateResult =
                write(
                        Map.of(
                                "todos",
                                "[{\"id\":\"1\",\"content\":\"task A\",\"status\":\"completed\",\"priority\":\"high\"}]"));

        assertThat(updateResult.isError()).isFalse();
        var readResult = read(Map.of());
        assertThat(readResult.content()).contains("\"completed\"");
        assertThat(readResult.content()).doesNotContain("\"pending\"");
    }

    @Test
    void deleteTodoByWritingSmallerList() {
        // Write two todos
        write(
                Map.of(
                        "todos",
                        """
                        [
                          {"id":"1","content":"keep me","status":"pending","priority":"high"},
                          {"id":"2","content":"delete me","status":"pending","priority":"low"}
                        ]"""));

        // Delete todo id=2 by writing only id=1
        var deleteResult =
                write(
                        Map.of(
                                "todos",
                                "[{\"id\":\"1\",\"content\":\"keep me\",\"status\":\"pending\",\"priority\":\"high\"}]"));

        assertThat(deleteResult.isError()).isFalse();
        assertThat(deleteResult.metadata()).containsEntry("count", 1);
        var readResult = read(Map.of());
        assertThat(readResult.content()).contains("keep me").doesNotContain("delete me");
    }

    @Test
    void updateStatusAndPriorityTogether() {
        write(
                Map.of(
                        "todos",
                        "[{\"id\":\"x\",\"content\":\"review PR\",\"status\":\"pending\",\"priority\":\"low\"}]"));

        write(
                Map.of(
                        "todos",
                        "[{\"id\":\"x\",\"content\":\"review PR\",\"status\":\"in_progress\",\"priority\":\"high\"}]"));

        var read = read(Map.of());
        assertThat(read.content()).contains("\"in_progress\"").contains("\"high\"");
        assertThat(read.content()).doesNotContain("\"pending\"").doesNotContain("\"low\"");
    }

    @Test
    void readCountMatchesWrittenCount() {
        var writeResult =
                write(
                        Map.of(
                                "todos",
                                """
                                [
                                  {"id":"a","content":"one","status":"pending","priority":"high"},
                                  {"id":"b","content":"two","status":"pending","priority":"medium"},
                                  {"id":"c","content":"three","status":"completed","priority":"low"}
                                ]"""));

        assertThat(writeResult.metadata()).containsEntry("count", 3);
        var readResult = read(Map.of());
        // Read returns file path in metadata; count is in the write result
        assertThat(readResult.isError()).isFalse();
        assertThat(readResult.content())
                .contains("\"one\"")
                .contains("\"two\"")
                .contains("\"three\"");
    }

    @Test
    void writeEmptyListSucceeds() {
        // First write something
        write(
                Map.of(
                        "todos",
                        "[{\"id\":\"1\",\"content\":\"x\",\"status\":\"pending\",\"priority\":\"low\"}]"));

        // Clear the list
        var clearResult = write(Map.of("todos", "[]"));
        assertThat(clearResult.isError()).isFalse();
        assertThat(clearResult.metadata()).containsEntry("count", 0);

        var readResult = read(Map.of());
        // File exists but contains an empty array
        assertThat(readResult.isError()).isFalse();
        assertThat(readResult.content().trim()).isEqualTo("[ ]");
    }

    @Test
    void multipleSequentialUpdatesAreReflected() {
        // Simulate a status progression: pending → in_progress → completed
        String idTemplate =
                "[{\"id\":\"1\",\"content\":\"task\",\"status\":\"%s\",\"priority\":\"medium\"}]";

        write(Map.of("todos", String.format(idTemplate, "pending")));
        write(Map.of("todos", String.format(idTemplate, "in_progress")));
        write(Map.of("todos", String.format(idTemplate, "completed")));

        var final_ = read(Map.of());
        assertThat(final_.content()).contains("\"completed\"");
        assertThat(final_.content())
                .doesNotContain("\"pending\"")
                .doesNotContain("\"in_progress\"");
    }
}
