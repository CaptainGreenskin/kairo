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
package io.kairo.tools.integration;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.task.Task;
import io.kairo.api.task.TaskBoard;
import io.kairo.api.task.TaskStatus;
import io.kairo.api.tool.ToolResult;
import io.kairo.multiagent.task.DefaultTaskBoard;
import io.kairo.multiagent.team.InProcessMessageBus;
import io.kairo.tools.agent.SendMessageTool;
import io.kairo.tools.agent.TaskCreateTool;
import io.kairo.tools.agent.TaskGetTool;
import io.kairo.tools.agent.TaskListTool;
import io.kairo.tools.agent.TaskUpdateTool;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for agent task and message tools using real {@link DefaultTaskBoard} and {@link
 * InProcessMessageBus} implementations. Verifies end-to-end tool behavior including task lifecycle,
 * dependency management, and inter-agent messaging.
 */
@Tag("integration")
class AgentToolsIntegrationIT {

    private TaskBoard taskBoard;
    private InProcessMessageBus messageBus;

    private TaskCreateTool taskCreateTool;
    private TaskListTool taskListTool;
    private TaskUpdateTool taskUpdateTool;
    private TaskGetTool taskGetTool;

    @BeforeEach
    void setUp() {
        taskBoard = new DefaultTaskBoard();
        messageBus = new InProcessMessageBus();

        taskCreateTool = new TaskCreateTool(taskBoard);
        taskListTool = new TaskListTool(taskBoard);
        taskUpdateTool = new TaskUpdateTool(taskBoard);
        taskGetTool = new TaskGetTool(taskBoard);
    }

    // ── TaskCreateTool tests ────────────────────────────────────────────

    @Test
    void taskCreate_addsToTaskBoard() {
        ToolResult result =
                taskCreateTool.execute(
                        Map.of(
                                "subject",
                                "Implement feature X",
                                "description",
                                "Build the X module"));

        assertFalse(result.isError());
        assertTrue(result.content().contains("Created task"));
        assertTrue(result.content().contains("Implement feature X"));

        // Verify the task is actually in the board
        String taskId = (String) result.metadata().get("taskId");
        assertNotNull(taskId);
        Task task = taskBoard.get(taskId);
        assertNotNull(task);
        assertEquals("Implement feature X", task.subject());
        assertEquals("Build the X module", task.description());
        assertEquals(TaskStatus.PENDING, task.status());
    }

    @Test
    void taskCreate_missingSubject_returnsError() {
        ToolResult result = taskCreateTool.execute(Map.of("description", "some desc"));

        assertTrue(result.isError());
        assertTrue(result.content().contains("subject"));
    }

    // ── TaskListTool tests ──────────────────────────────────────────────

    @Test
    void taskList_returnsAllTasks() {
        taskCreateTool.execute(Map.of("subject", "Task A", "description", "first"));
        taskCreateTool.execute(Map.of("subject", "Task B", "description", "second"));
        taskCreateTool.execute(Map.of("subject", "Task C", "description", "third"));

        ToolResult result = taskListTool.execute(Map.of());

        assertFalse(result.isError());
        assertEquals(3, result.metadata().get("count"));
        assertTrue(result.content().contains("Task A"));
        assertTrue(result.content().contains("Task B"));
        assertTrue(result.content().contains("Task C"));
    }

    @Test
    void taskList_emptyBoard_returnsNoTasks() {
        ToolResult result = taskListTool.execute(Map.of());

        assertFalse(result.isError());
        assertEquals(0, result.metadata().get("count"));
        assertTrue(result.content().contains("No tasks"));
    }

    // ── TaskUpdateTool tests ────────────────────────────────────────────

    @Test
    void taskUpdate_changesStatus() {
        ToolResult createResult =
                taskCreateTool.execute(Map.of("subject", "Work item", "description", "do stuff"));
        String taskId = (String) createResult.metadata().get("taskId");

        ToolResult updateResult =
                taskUpdateTool.execute(Map.of("taskId", taskId, "status", "in_progress"));

        assertFalse(updateResult.isError());
        assertTrue(updateResult.content().contains("Updated task"));
        assertEquals(TaskStatus.IN_PROGRESS, taskBoard.get(taskId).status());
    }

    @Test
    void taskUpdate_invalidTask_returnsError() {
        ToolResult result =
                taskUpdateTool.execute(Map.of("taskId", "nonexistent", "status", "completed"));

        assertTrue(result.isError());
        assertTrue(result.content().contains("Error"));
    }

    // ── TaskGetTool tests ───────────────────────────────────────────────

    @Test
    void taskGet_retrievesTaskDetails() {
        ToolResult createResult =
                taskCreateTool.execute(
                        Map.of("subject", "Detailed task", "description", "Full description here"));
        String taskId = (String) createResult.metadata().get("taskId");

        ToolResult getResult = taskGetTool.execute(Map.of("taskId", taskId));

        assertFalse(getResult.isError());
        assertTrue(getResult.content().contains("Detailed task"));
        assertTrue(getResult.content().contains("Full description here"));
        assertTrue(getResult.content().contains("PENDING"));
    }

    // ── Dependency tests ────────────────────────────────────────────────

    @Test
    void taskCreate_withDependency_linksCorrectly() {
        // Create two tasks, then link them via the board's addDependency
        ToolResult parentResult =
                taskCreateTool.execute(Map.of("subject", "Build core", "description", "core lib"));
        ToolResult childResult =
                taskCreateTool.execute(Map.of("subject", "Build UI", "description", "UI layer"));

        String parentId = (String) parentResult.metadata().get("taskId");
        String childId = (String) childResult.metadata().get("taskId");

        // Add dependency: child is blocked by parent
        taskBoard.addDependency(childId, parentId);

        // Verify via TaskGetTool
        ToolResult childGet = taskGetTool.execute(Map.of("taskId", childId));
        assertFalse(childGet.isError());
        assertTrue(childGet.content().contains(parentId));

        // Verify the parent's blocks list
        Task parent = taskBoard.get(parentId);
        assertTrue(parent.blocks().contains(childId));

        // Child should be blocked
        Task child = taskBoard.get(childId);
        assertTrue(child.blockedBy().contains(parentId));
    }

    @Test
    void taskUpdate_toCompleted_unblocksDependents() {
        // Create parent and child tasks with dependency
        ToolResult parentResult =
                taskCreateTool.execute(
                        Map.of("subject", "Setup DB", "description", "database setup"));
        ToolResult childResult =
                taskCreateTool.execute(
                        Map.of("subject", "Run migrations", "description", "apply migrations"));

        String parentId = (String) parentResult.metadata().get("taskId");
        String childId = (String) childResult.metadata().get("taskId");

        taskBoard.addDependency(childId, parentId);

        // Before completion, child is blocked
        Task childBefore = taskBoard.get(childId);
        assertFalse(childBefore.blockedBy().isEmpty());

        // Unblocked tasks should not include the child
        List<Task> unblockedBefore = taskBoard.getUnblockedTasks();
        assertTrue(
                unblockedBefore.stream().noneMatch(t -> t.id().equals(childId)),
                "Child should not be in unblocked list while parent is pending");

        // Complete the parent via tool
        taskUpdateTool.execute(Map.of("taskId", parentId, "status", "completed"));

        // After completion, child should be unblocked
        Task childAfter = taskBoard.get(childId);
        assertTrue(
                childAfter.blockedBy().isEmpty(),
                "Child's blockedBy should be empty after parent completes");

        // Child should now appear in unblocked tasks
        List<Task> unblockedAfter = taskBoard.getUnblockedTasks();
        assertTrue(
                unblockedAfter.stream().anyMatch(t -> t.id().equals(childId)),
                "Child should now be in the unblocked tasks list");
    }

    // ── SendMessageTool tests ───────────────────────────────────────────

    @Test
    void sendMessage_deliversToRecipient() {
        messageBus.registerAgent("agent-a");
        messageBus.registerAgent("agent-b");

        SendMessageTool sendTool = new SendMessageTool(messageBus, "agent-a");
        ToolResult result =
                sendTool.execute(Map.of("recipientId", "agent-b", "content", "Hello from A"));

        assertFalse(result.isError());
        assertTrue(result.content().contains("Message sent to agent agent-b"));

        // Verify message is in agent-b's inbox
        var messages = messageBus.poll("agent-b");
        assertEquals(1, messages.size());
        assertEquals("Hello from A", messages.get(0).text());
    }

    @Test
    void sendMessage_toSpecificAgent_deliversCorrectly() {
        messageBus.registerAgent("leader");
        messageBus.registerAgent("worker-1");
        messageBus.registerAgent("worker-2");

        // worker-1 sends to leader only
        SendMessageTool worker1Send = new SendMessageTool(messageBus, "worker-1");
        worker1Send.execute(Map.of("recipientId", "leader", "content", "Task done"));

        // worker-2 sends to leader only
        SendMessageTool worker2Send = new SendMessageTool(messageBus, "worker-2");
        worker2Send.execute(Map.of("recipientId", "leader", "content", "Need help"));

        // Leader should have 2 messages
        var leaderMessages = messageBus.poll("leader");
        assertEquals(2, leaderMessages.size());

        // worker-1 and worker-2 should have no messages in their inboxes
        assertTrue(messageBus.poll("worker-1").isEmpty());
        assertTrue(messageBus.poll("worker-2").isEmpty());
    }

    @Test
    void sendMessage_missingRecipient_returnsError() {
        SendMessageTool sendTool = new SendMessageTool(messageBus, "agent-a");
        ToolResult result = sendTool.execute(Map.of("content", "Hello"));

        assertTrue(result.isError());
        assertTrue(result.content().contains("recipientId"));
    }

    @Test
    void sendMessage_missingContent_returnsError() {
        SendMessageTool sendTool = new SendMessageTool(messageBus, "agent-a");
        ToolResult result = sendTool.execute(Map.of("recipientId", "agent-b"));

        assertTrue(result.isError());
        assertTrue(result.content().contains("content"));
    }
}
