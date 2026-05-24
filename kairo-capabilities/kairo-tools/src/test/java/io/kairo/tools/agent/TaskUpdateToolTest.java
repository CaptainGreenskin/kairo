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
import io.kairo.core.task.TaskStatus;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskUpdateToolTest {

    private static final ToolContext CTX = new ToolContext("agent-1", "sess-1", Map.of());

    @TempDir Path workspaceRoot;

    private TaskCreateTool createTool;
    private TaskUpdateTool updateTool;
    private FileTaskStore store;

    @BeforeEach
    void setUp() {
        FileTaskStore.clearInstances();
        createTool = new TaskCreateTool(workspaceRoot);
        updateTool = new TaskUpdateTool(workspaceRoot);
        store = FileTaskStore.forWorkspace(workspaceRoot);
    }

    private ToolResult create(String subject) {
        return createTool.execute(Map.of("subject", subject), CTX).block();
    }

    private ToolResult update(Map<String, Object> args) {
        return updateTool.execute(args, CTX).block();
    }

    @Test
    void updateStatusToInProgress() {
        create("Task");
        ToolResult result = update(Map.of("taskId", "1", "status", "in_progress"));
        assertThat(result.isError()).isFalse();
        assertThat(store.get("1").get().status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void updateStatusToCompleted() {
        create("Task");
        ToolResult result = update(Map.of("taskId", "1", "status", "completed"));
        assertThat(result.isError()).isFalse();
        assertThat(store.get("1").get().status()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void updateDeletedStatusRemovesTask() {
        create("Task");
        ToolResult result = update(Map.of("taskId", "1", "status", "deleted"));
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("deleted");
        assertThat(store.get("1")).isEmpty();
    }

    @Test
    void updateNonExistentTaskReturnsError() {
        ToolResult result = update(Map.of("taskId", "999", "status", "completed"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("not found");
    }

    @Test
    void updateMissingTaskIdReturnsError() {
        ToolResult result = update(Map.of("status", "completed"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("'taskId' is required");
    }

    @Test
    void updateOwner() {
        create("Task");
        update(Map.of("taskId", "1", "owner", "researcher"));
        assertThat(store.get("1").get().owner()).isEqualTo("researcher");
    }

    @Test
    void updateSubject() {
        create("Original");
        update(Map.of("taskId", "1", "subject", "Renamed"));
        assertThat(store.get("1").get().subject()).isEqualTo("Renamed");
    }

    @Test
    void updateAddBlocksCreatesBidirectionalDependency() {
        create("A");
        create("B");
        update(Map.of("taskId", "1", "addBlocks", List.of("2")));

        assertThat(store.get("1").get().blocks()).contains("2");
        assertThat(store.get("2").get().blockedBy()).contains("1");
    }

    @Test
    void updateAddBlockedByCreatesBidirectionalDependency() {
        create("A");
        create("B");
        update(Map.of("taskId", "2", "addBlockedBy", List.of("1")));

        assertThat(store.get("1").get().blocks()).contains("2");
        assertThat(store.get("2").get().blockedBy()).contains("1");
    }

    @Test
    void completionCascadesRemovesBlockedBy() {
        create("A");
        create("B");
        update(Map.of("taskId", "1", "addBlocks", List.of("2")));

        update(Map.of("taskId", "1", "status", "completed"));

        assertThat(store.get("2").get().blockedBy()).isEmpty();
    }

    @Test
    void deleteNonExistentTaskReturnsError() {
        ToolResult result = update(Map.of("taskId", "999", "status", "deleted"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("not found");
    }

    @Test
    void mergeMetadata() {
        create("Task");
        Map<String, Object> args = new HashMap<>();
        args.put("taskId", "1");
        args.put("metadata", Map.of("key1", "val1"));
        update(args);

        assertThat(store.get("1").get().metadata()).containsEntry("key1", "val1");
    }
}
