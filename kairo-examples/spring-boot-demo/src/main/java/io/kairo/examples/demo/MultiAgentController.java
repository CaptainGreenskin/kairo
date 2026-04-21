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
package io.kairo.examples.demo;

import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.task.Task;
import io.kairo.api.task.TaskStatus;
import io.kairo.multiagent.task.DefaultTaskBoard;
import io.kairo.multiagent.team.InProcessMessageBus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller demonstrating Kairo's multi-agent orchestration primitives.
 *
 * <p>This is a pure orchestration demo — no LLM API key is required. It exposes {@link
 * DefaultTaskBoard} for DAG-based task management and {@link InProcessMessageBus} for inter-agent
 * messaging over HTTP.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * # Create a plan with dependency DAG
 * curl -X POST http://localhost:8080/multi-agent/plan \
 *   -H "Content-Type: application/json" \
 *   -d '{"tasks": [
 *     {"subject": "Design API", "description": "Design the REST API schema"},
 *     {"subject": "Implement", "description": "Code the endpoints", "blockedBy": ["1"]},
 *     {"subject": "Test", "description": "Write tests", "blockedBy": ["2"]}
 *   ]}'
 *
 * # List all tasks
 * curl http://localhost:8080/multi-agent/tasks
 *
 * # Update task status
 * curl -X PUT http://localhost:8080/multi-agent/tasks/1/status \
 *   -H "Content-Type: application/json" \
 *   -d '{"status": "IN_PROGRESS"}'
 *
 * # Send a message between agents
 * curl -X POST http://localhost:8080/multi-agent/message \
 *   -H "Content-Type: application/json" \
 *   -d '{"from": "architect", "to": "coder", "content": "Schema is ready"}'
 *
 * # Poll an agent's inbox
 * curl http://localhost:8080/multi-agent/inbox/coder
 *
 * # Reset everything
 * curl -X POST http://localhost:8080/multi-agent/reset
 * }</pre>
 */
@RestController
@RequestMapping("/multi-agent")
public class MultiAgentController {

    private DefaultTaskBoard taskBoard;
    private InProcessMessageBus messageBus;

    /** Creates the controller with fresh TaskBoard and MessageBus instances. */
    public MultiAgentController() {
        this.taskBoard = new DefaultTaskBoard();
        this.messageBus = new InProcessMessageBus();
    }

    /**
     * Create a plan by submitting a list of tasks with optional dependency relationships.
     *
     * <p>Each task in the request may reference other tasks via {@code blockedBy}, using the
     * 1-based index that corresponds to creation order (the first task created gets ID "1").
     *
     * @param request the plan request containing the task list
     * @return the created tasks with their assigned IDs and dependency info
     */
    @PostMapping("/plan")
    public ResponseEntity<Map<String, Object>> createPlan(@RequestBody PlanRequest request) {
        List<Map<String, Object>> createdTasks = new ArrayList<>();

        // Resolve task inputs: support both "task" (single string) and "tasks" (list)
        List<TaskInput> taskInputs = request.resolveTaskInputs();
        if (taskInputs.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Either 'task' or 'tasks' must be provided"));
        }

        // Phase 1: create all tasks
        List<Task> tasks = new ArrayList<>();
        for (TaskInput input : taskInputs) {
            Task task = taskBoard.create(input.subject(), input.description());
            tasks.add(task);
        }

        // Phase 2: wire up dependencies
        for (int i = 0; i < taskInputs.size(); i++) {
            TaskInput input = taskInputs.get(i);
            if (input.blockedBy() != null) {
                for (String blockerId : input.blockedBy()) {
                    taskBoard.addDependency(tasks.get(i).id(), blockerId);
                }
            }
        }

        // Phase 3: build response
        for (Task task : tasks) {
            createdTasks.add(toTaskMap(taskBoard.get(task.id())));
        }

        return ResponseEntity.ok(
                Map.of(
                        "plan",
                        createdTasks,
                        "unblockedTasks",
                        taskBoard.getUnblockedTasks().stream().map(t -> t.id()).toList()));
    }

    /**
     * List all tasks with their current statuses and dependency information.
     *
     * @return all tasks on the board
     */
    @GetMapping("/tasks")
    public ResponseEntity<Map<String, Object>> listTasks() {
        List<Task> allTasks = taskBoard.list();
        List<Task> unblocked = taskBoard.getUnblockedTasks();
        return ResponseEntity.ok(
                Map.of(
                        "tasks", allTasks.stream().map(this::toTaskMap).toList(),
                        "unblockedTasks", unblocked.stream().map(t -> t.id()).toList()));
    }

    /**
     * Update the status of a task by its ID.
     *
     * <p>When a task is marked {@code COMPLETED}, all downstream tasks that were blocked by it are
     * automatically unblocked.
     *
     * @param id the task ID
     * @param request the status update request
     * @return the updated task
     */
    @PutMapping("/tasks/{id}/status")
    public ResponseEntity<Map<String, Object>> updateTaskStatus(
            @PathVariable String id, @RequestBody StatusUpdateRequest request) {
        TaskStatus status = TaskStatus.valueOf(request.status());
        Task updated = taskBoard.update(id, status);
        return ResponseEntity.ok(
                Map.of(
                        "task", toTaskMap(updated),
                        "unblockedTasks",
                                taskBoard.getUnblockedTasks().stream().map(t -> t.id()).toList()));
    }

    /**
     * Send a direct message from one agent to another.
     *
     * <p>Agents are auto-registered on first use (either as sender or receiver).
     *
     * @param request the message request containing from, to, and content
     * @return confirmation with message details
     */
    @PostMapping("/message")
    public ResponseEntity<Map<String, String>> sendMessage(@RequestBody MessageRequest request) {
        messageBus.registerAgent(request.from());
        messageBus.registerAgent(request.to());
        Msg msg = Msg.of(MsgRole.ASSISTANT, request.content());
        messageBus.send(request.from(), request.to(), msg).block();
        return ResponseEntity.ok(
                Map.of(
                        "status", "sent",
                        "from", request.from(),
                        "to", request.to(),
                        "content", request.content()));
    }

    /**
     * Poll all pending messages for the given agent.
     *
     * <p>Messages are consumed upon polling — subsequent polls will not return the same messages.
     *
     * @param agentName the agent whose inbox to poll
     * @return the list of pending messages
     */
    @GetMapping("/inbox/{agentName}")
    public ResponseEntity<Map<String, Object>> pollInbox(@PathVariable String agentName) {
        messageBus.registerAgent(agentName);
        List<Msg> messages = messageBus.poll(agentName);
        List<Map<String, String>> messageList =
                messages.stream()
                        .map(m -> Map.of("role", m.role().name(), "text", m.text()))
                        .toList();
        return ResponseEntity.ok(
                Map.of(
                        "agent", agentName,
                        "messageCount", messageList.size(),
                        "messages", messageList));
    }

    /**
     * Reset the task board and message bus to a clean state.
     *
     * @return confirmation of the reset
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> reset() {
        this.taskBoard = new DefaultTaskBoard();
        this.messageBus = new InProcessMessageBus();
        return ResponseEntity.ok(
                Map.of("status", "reset", "message", "TaskBoard and MessageBus have been reset"));
    }

    private Map<String, Object> toTaskMap(Task task) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", task.id());
        map.put("subject", task.subject());
        map.put("description", task.description());
        map.put("status", task.status().name());
        map.put("blockedBy", task.blockedBy());
        map.put("blocks", task.blocks());
        return map;
    }

    /**
     * Request body for creating a plan. Accepts either a single {@code task} string or a {@code
     * tasks} list.
     */
    public record PlanRequest(String task, List<TaskInput> tasks) {
        /** Resolves task inputs from either the single task string or the tasks list. */
        public List<TaskInput> resolveTaskInputs() {
            if (tasks != null && !tasks.isEmpty()) {
                return tasks;
            }
            if (task != null && !task.isBlank()) {
                return List.of(new TaskInput(task, task, null));
            }
            return List.of();
        }
    }

    /** Single task input within a plan request. */
    public record TaskInput(String subject, String description, List<String> blockedBy) {}

    /** Request body for updating task status. */
    public record StatusUpdateRequest(String status) {}

    /** Request body for sending an inter-agent message. */
    public record MessageRequest(String from, String to, String content) {}
}
