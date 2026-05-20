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
import io.kairo.core.task.TaskEntry;
import io.kairo.core.task.TaskStatus;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaskDependencyIntegrationTest {

    private static final ToolContext CTX = new ToolContext("agent-1", "sess-1", Map.of());

    @TempDir Path workspaceRoot;

    private TaskCreateTool createTool;
    private TaskUpdateTool updateTool;
    private TaskGetTool getTool;
    private TaskListTool listTool;
    private FileTaskStore store;

    @BeforeEach
    void setUp() {
        FileTaskStore.clearInstances();
        createTool = new TaskCreateTool(workspaceRoot);
        updateTool = new TaskUpdateTool(workspaceRoot);
        getTool = new TaskGetTool(workspaceRoot);
        listTool = new TaskListTool(workspaceRoot);
        store = FileTaskStore.forWorkspace(workspaceRoot);
    }

    @Test
    void fullDependencyLifecycle() {
        createTool.execute(Map.of("subject", "A"), CTX).block();
        createTool.execute(Map.of("subject", "B"), CTX).block();
        createTool.execute(Map.of("subject", "C"), CTX).block();

        updateTool.execute(Map.of("taskId", "1", "addBlocks", List.of("2")), CTX).block();
        updateTool.execute(Map.of("taskId", "2", "addBlocks", List.of("3")), CTX).block();

        assertThat(store.get("2").get().blockedBy()).containsExactly("1");
        assertThat(store.get("3").get().blockedBy()).containsExactly("2");

        Map<String, TaskEntry> allTasks = buildTaskMap();
        assertThat(store.get("1").get().unresolvedBlockers(allTasks)).isEmpty();
        assertThat(store.get("2").get().unresolvedBlockers(allTasks)).containsExactly("1");
        assertThat(store.get("3").get().unresolvedBlockers(allTasks)).containsExactly("2");

        updateTool.execute(Map.of("taskId", "1", "status", "completed"), CTX).block();

        assertThat(store.get("1").get().status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(store.get("2").get().blockedBy()).isEmpty();
        assertThat(store.get("3").get().blockedBy()).containsExactly("2");

        updateTool.execute(Map.of("taskId", "2", "status", "completed"), CTX).block();
        assertThat(store.get("3").get().blockedBy()).isEmpty();

        updateTool.execute(Map.of("taskId", "3", "status", "deleted"), CTX).block();
        assertThat(store.get("3")).isEmpty();
        assertThat(store.get("2").get().blocks()).isEmpty();
    }

    @Test
    void listReflectsBlockingState() {
        createTool.execute(Map.of("subject", "Setup"), CTX).block();
        createTool.execute(Map.of("subject", "Build"), CTX).block();
        createTool.execute(Map.of("subject", "Deploy"), CTX).block();

        updateTool.execute(Map.of("taskId", "1", "addBlocks", List.of("2")), CTX).block();
        updateTool.execute(Map.of("taskId", "2", "addBlocks", List.of("3")), CTX).block();

        ToolResult listResult = listTool.execute(Map.of(), CTX).block();
        assertThat(listResult.isError()).isFalse();

        String content = listResult.content();
        int posSetup = content.indexOf("Setup");
        int posBuild = content.indexOf("Build");
        int posDeploy = content.indexOf("Deploy");

        assertThat(posSetup).isLessThan(posBuild);
        assertThat(posSetup).isLessThan(posDeploy);
    }

    @Test
    void getShowsDependencies() {
        createTool.execute(Map.of("subject", "Parent"), CTX).block();
        createTool.execute(Map.of("subject", "Child"), CTX).block();
        updateTool.execute(Map.of("taskId", "1", "addBlocks", List.of("2")), CTX).block();

        ToolResult result = getTool.execute(Map.of("taskId", "2"), CTX).block();
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("blockedBy");
        assertThat(result.content()).contains("1");
    }

    private Map<String, TaskEntry> buildTaskMap() {
        Map<String, TaskEntry> map = new LinkedHashMap<>();
        for (TaskEntry t : store.listAll()) {
            map.put(t.id(), t);
        }
        return map;
    }
}
