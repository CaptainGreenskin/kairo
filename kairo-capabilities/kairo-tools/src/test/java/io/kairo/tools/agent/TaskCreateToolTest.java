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

class TaskCreateToolTest {

    private static final ToolContext CTX = new ToolContext("agent-1", "sess-1", Map.of());

    @TempDir Path workspaceRoot;

    private TaskCreateTool tool;

    @BeforeEach
    void setUp() {
        FileTaskStore.clearInstances();
        tool = new TaskCreateTool(workspaceRoot);
    }

    private ToolResult exec(Map<String, Object> args) {
        return tool.execute(args, CTX).block();
    }

    @Test
    void createWithSubjectOnly() {
        ToolResult result = exec(Map.of("subject", "Fix the bug"));
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("Fix the bug");
        assertThat(result.metadata()).containsEntry("taskId", "1");
    }

    @Test
    void createWithAllFields() {
        ToolResult result =
                exec(
                        Map.of(
                                "subject", "Full task",
                                "description", "Detailed work",
                                "activeForm", "Working on it",
                                "metadata", "{\"key\": \"val\"}"));
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("Full task");
        assertThat(result.content()).contains("Detailed work");
    }

    @Test
    void createMissingSubjectReturnsError() {
        ToolResult result = exec(Map.of());
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("'subject' is required");
    }

    @Test
    void createBlankSubjectReturnsError() {
        ToolResult result = exec(Map.of("subject", "  "));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("'subject' is required");
    }

    @Test
    void createAutoIncrementsId() {
        ToolResult first = exec(Map.of("subject", "First"));
        ToolResult second = exec(Map.of("subject", "Second"));
        assertThat(first.metadata()).containsEntry("taskId", "1");
        assertThat(second.metadata()).containsEntry("taskId", "2");
    }
}
