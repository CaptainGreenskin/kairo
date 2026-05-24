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
import io.kairo.core.task.FileTaskStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskListToolTest {

    private static final ToolContext CTX = new ToolContext("agent-1", "sess-1", Map.of());

    @TempDir Path workspaceRoot;

    private TaskCreateTool createTool;
    private TaskUpdateTool updateTool;
    private TaskListTool listTool;

    @BeforeEach
    void setUp() {
        FileTaskStore.clearInstances();
        createTool = new TaskCreateTool(workspaceRoot);
        updateTool = new TaskUpdateTool(workspaceRoot);
        listTool = new TaskListTool(workspaceRoot);
    }

    @Test
    void listEmptyReturnsNoTasks() {
        ToolResult result = listTool.execute(Map.of(), CTX).block();
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("No tasks");
        assertThat(result.metadata()).containsEntry("count", 0);
    }

    @Test
    void listShowsAllTasks() {
        createTool.execute(Map.of("subject", "Task A"), CTX).block();
        createTool.execute(Map.of("subject", "Task B"), CTX).block();

        ToolResult result = listTool.execute(Map.of(), CTX).block();
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("Task A");
        assertThat(result.content()).contains("Task B");
        assertThat(result.metadata()).containsEntry("count", 2);
    }

    @Test
    void listSortsUnblockedFirst() {
        createTool.execute(Map.of("subject", "A"), CTX).block();
        createTool.execute(Map.of("subject", "B"), CTX).block();
        createTool.execute(Map.of("subject", "C"), CTX).block();

        updateTool.execute(Map.of("taskId", "1", "addBlocks", List.of("2")), CTX).block();

        ToolResult result = listTool.execute(Map.of(), CTX).block();
        String content = result.content();

        int posA = content.indexOf("\"A\"");
        int posB = content.indexOf("\"B\"");
        int posC = content.indexOf("\"C\"");

        assertThat(posA).isLessThan(posB);
        assertThat(posC).isLessThan(posB);
    }

    @Test
    void listShowsOnlyUnresolvedBlockers() {
        createTool.execute(Map.of("subject", "A"), CTX).block();
        createTool.execute(Map.of("subject", "B"), CTX).block();

        updateTool.execute(Map.of("taskId", "1", "addBlocks", List.of("2")), CTX).block();
        updateTool.execute(Map.of("taskId", "1", "status", "completed"), CTX).block();

        ToolResult result = listTool.execute(Map.of(), CTX).block();
        assertThat(result.content()).doesNotContain("blockedBy");
    }
}
