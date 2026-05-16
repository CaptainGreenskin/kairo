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
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskGetToolTest {

    private static final ToolContext CTX = new ToolContext("agent-1", "sess-1", Map.of());

    @TempDir Path workspaceRoot;

    private TaskCreateTool createTool;
    private TaskGetTool getTool;

    @BeforeEach
    void setUp() {
        FileTaskStore.clearInstances();
        createTool = new TaskCreateTool(workspaceRoot);
        getTool = new TaskGetTool(workspaceRoot);
    }

    @Test
    void getExistingTask() {
        createTool
                .execute(Map.of("subject", "Test task", "description", "Do the thing"), CTX)
                .block();

        ToolResult result = getTool.execute(Map.of("taskId", "1"), CTX).block();
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("Test task");
        assertThat(result.content()).contains("Do the thing");
        assertThat(result.content()).contains("pending");
    }

    @Test
    void getMissingTaskReturnsError() {
        ToolResult result = getTool.execute(Map.of("taskId", "999"), CTX).block();
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("not found");
    }

    @Test
    void getMissingTaskIdReturnsError() {
        ToolResult result = getTool.execute(Map.of(), CTX).block();
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("'taskId' is required");
    }
}
