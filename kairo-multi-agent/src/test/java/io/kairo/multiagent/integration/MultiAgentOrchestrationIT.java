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
package io.kairo.multiagent.integration;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentState;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.task.Plan;
import io.kairo.api.task.Task;
import io.kairo.api.task.TaskStatus;
import io.kairo.api.team.Team;
import io.kairo.multiagent.task.DefaultTaskBoard;
import io.kairo.multiagent.task.PlanBuilder;
import io.kairo.multiagent.team.CoordinatorScheduler;
import io.kairo.multiagent.team.DefaultTeamManager;
import io.kairo.multiagent.team.DefaultTeamScheduler;
import io.kairo.multiagent.team.InProcessMessageBus;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * Integration tests for multi-agent orchestration covering DefaultTaskBoard lifecycle, retry logic,
 * PlanBuilder with dependencies, DefaultTeamScheduler dispatch, CoordinatorScheduler dispatch, and
 * DefaultTeamManager team management.
 */
@Tag("integration")
class MultiAgentOrchestrationIT {

    private DefaultTaskBoard taskBoard;

    @BeforeEach
    void setUp() {
        taskBoard = new DefaultTaskBoard();
    }

    // ==================== TASK LIFECYCLE ====================

    @Test
    void taskLifecycle_createAssignUpdateComplete() {
        // Full lifecycle: create → assign → IN_PROGRESS → COMPLETED
        Task task = taskBoard.create("Implement feature", "Build the login page");
        assertEquals(TaskStatus.PENDING, task.status());
        assertNull(task.owner());

        // Assign owner
        task.setOwner("agent-1");
        assertEquals("agent-1", task.owner());

        // Move to IN_PROGRESS
        Task updated = taskBoard.update(task.id(), TaskStatus.IN_PROGRESS);
        assertEquals(TaskStatus.IN_PROGRESS, updated.status());

        // Complete
        Task completed = taskBoard.update(task.id(), TaskStatus.COMPLETED);
        assertEquals(TaskStatus.COMPLETED, completed.status());

        // Verify via get
        Task retrieved = taskBoard.get(task.id());
        assertNotNull(retrieved);
        assertEquals(TaskStatus.COMPLETED, retrieved.status());
        assertEquals("agent-1", retrieved.owner());
    }

    @Test
    void taskLifecycle_createAndCancel() {
        Task task = taskBoard.create("Deprecated task", "No longer needed");
        taskBoard.update(task.id(), TaskStatus.CANCELLED);
        assertEquals(TaskStatus.CANCELLED, taskBoard.get(task.id()).status());
    }

    // ==================== RETRY LOGIC ====================

    @Test
    void retryLogic_failedAutoRequeueThenAbandoned() {
        DefaultTaskBoard board = new DefaultTaskBoard(3);
        Task task = board.create("Flaky task", "May fail intermittently");

        // First failure → requeued to PENDING (attempt 1/3)
        Task after1 = board.update(task.id(), TaskStatus.FAILED);
        assertEquals(TaskStatus.PENDING, after1.status());

        // Second failure → requeued to PENDING (attempt 2/3)
        Task after2 = board.update(task.id(), TaskStatus.FAILED);
        assertEquals(TaskStatus.PENDING, after2.status());

        // Third failure → ABANDONED (attempt 3/3, exhausted)
        Task after3 = board.update(task.id(), TaskStatus.FAILED);
        assertEquals(TaskStatus.ABANDONED, after3.status());

        // Verify the task is no longer in the unblocked list
        List<Task> unblocked = board.getUnblockedTasks();
        assertTrue(unblocked.stream().noneMatch(t -> t.id().equals(task.id())));
    }

    @Test
    void retryLogic_manualRecoveryResetsRetryCount() {
        Task task = taskBoard.create("Recoverable", "Can be fixed");

        // Fail twice (uses 2 of 3 retries)
        taskBoard.update(task.id(), TaskStatus.FAILED);
        taskBoard.update(task.id(), TaskStatus.FAILED);
        assertEquals(TaskStatus.PENDING, taskBoard.get(task.id()).status());

        // Manually set to IN_PROGRESS → resets retry count
        taskBoard.update(task.id(), TaskStatus.IN_PROGRESS);

        // Now can fail 3 more times before ABANDONED
        taskBoard.update(task.id(), TaskStatus.FAILED);
        assertEquals(TaskStatus.PENDING, taskBoard.get(task.id()).status());
        taskBoard.update(task.id(), TaskStatus.FAILED);
        assertEquals(TaskStatus.PENDING, taskBoard.get(task.id()).status());
        taskBoard.update(task.id(), TaskStatus.FAILED);
        assertEquals(TaskStatus.ABANDONED, taskBoard.get(task.id()).status());
    }

    @Test
    void retryLogic_customMaxRetriesOne_immediateAbandoned() {
        DefaultTaskBoard board = new DefaultTaskBoard(1);
        Task task = board.create("Fragile", "No retries");

        // First failure → ABANDONED immediately (maxRetries=1, attempt >= maxRetries)
        Task result = board.update(task.id(), TaskStatus.FAILED);
        assertEquals(TaskStatus.ABANDONED, result.status());
    }

    // ==================== DAG DEPENDENCIES VIA PLANBUILDER ====================

    @Test
    void planBuilder_dagDependencies_unblockedAfterCompletion() {
        // Build a plan: A (no dep) → B (depends on A) → C (depends on B)
        Plan plan =
                PlanBuilder.create(taskBoard)
                        .name("Sequential pipeline")
                        .overview("A → B → C chain")
                        .addTask("Task A", "First step")
                        .addTask("Task B", "Second step", "1")
                        .addTask("Task C", "Third step", "2")
                        .build();

        assertEquals("Sequential pipeline", plan.name());
        assertEquals(3, plan.tasks().size());

        // Only A should be unblocked initially
        List<Task> unblocked = taskBoard.getUnblockedTasks();
        assertEquals(1, unblocked.size());
        assertEquals("Task A", unblocked.get(0).subject());

        // Complete A → B becomes unblocked
        taskBoard.update("1", TaskStatus.COMPLETED);
        unblocked = taskBoard.getUnblockedTasks();
        assertEquals(1, unblocked.size());
        assertEquals("Task B", unblocked.get(0).subject());

        // Complete B → C becomes unblocked
        taskBoard.update("2", TaskStatus.COMPLETED);
        unblocked = taskBoard.getUnblockedTasks();
        assertEquals(1, unblocked.size());
        assertEquals("Task C", unblocked.get(0).subject());
    }

    @Test
    void planBuilder_diamondDependency_requiresBothParents() {
        // Diamond: A → B, A → C, B+C → D
        PlanBuilder.create(taskBoard)
                .name("Diamond plan")
                .overview("A fans out to B and C, then D joins")
                .addTask("A", "Root")
                .addTask("B", "Left branch", "1")
                .addTask("C", "Right branch", "1")
                .addTask("D", "Join point", "2", "3")
                .build();

        // Only A is unblocked
        assertEquals(1, taskBoard.getUnblockedTasks().size());

        // Complete A → B and C become unblocked
        taskBoard.update("1", TaskStatus.COMPLETED);
        List<Task> unblocked = taskBoard.getUnblockedTasks();
        assertEquals(2, unblocked.size());

        // Complete B only → D still blocked by C
        taskBoard.update("2", TaskStatus.COMPLETED);
        unblocked = taskBoard.getUnblockedTasks();
        assertEquals(1, unblocked.size());
        assertEquals("C", unblocked.get(0).subject());

        // Complete C → D becomes unblocked
        taskBoard.update("3", TaskStatus.COMPLETED);
        unblocked = taskBoard.getUnblockedTasks();
        assertEquals(1, unblocked.size());
        assertEquals("D", unblocked.get(0).subject());
    }

    // ==================== DEFAULT TEAM SCHEDULER ====================

    @Test
    void defaultTeamScheduler_dispatchesTasksToAvailableAgents() {
        // Create tasks
        taskBoard.create("Task 1", "desc 1");
        taskBoard.create("Task 2", "desc 2");

        // Create mock agents
        StubAgent agent1 = new StubAgent("worker-1", AgentState.IDLE);
        StubAgent agent2 = new StubAgent("worker-2", AgentState.IDLE);

        DefaultTeamScheduler scheduler = new DefaultTeamScheduler();

        // Dispatch
        scheduler.dispatch(taskBoard, List.of(agent1, agent2)).block(Duration.ofSeconds(5));

        // Both agents should have been called
        assertEquals(1, agent1.callCount.get());
        assertEquals(1, agent2.callCount.get());

        // Tasks should be IN_PROGRESS or COMPLETED (depending on agent success)
        Task t1 = taskBoard.get("1");
        Task t2 = taskBoard.get("2");
        // After successful dispatch, tasks are completed by the scheduler's doOnSuccess
        assertEquals(TaskStatus.COMPLETED, t1.status());
        assertEquals(TaskStatus.COMPLETED, t2.status());
    }

    @Test
    void defaultTeamScheduler_skipsRunningAgents() {
        taskBoard.create("Task 1", "desc");

        StubAgent busyAgent = new StubAgent("busy", AgentState.RUNNING);
        StubAgent idleAgent = new StubAgent("idle", AgentState.IDLE);

        DefaultTeamScheduler scheduler = new DefaultTeamScheduler();
        scheduler.dispatch(taskBoard, List.of(busyAgent, idleAgent)).block(Duration.ofSeconds(5));

        // Only idle agent should be used
        assertEquals(0, busyAgent.callCount.get());
        assertEquals(1, idleAgent.callCount.get());
    }

    @Test
    void defaultTeamScheduler_noTasksEmptyDispatch() {
        StubAgent agent = new StubAgent("worker", AgentState.IDLE);
        DefaultTeamScheduler scheduler = new DefaultTeamScheduler();

        // Should complete without error
        scheduler.dispatch(taskBoard, List.of(agent)).block(Duration.ofSeconds(5));
        assertEquals(0, agent.callCount.get());
    }

    // ==================== COORDINATOR SCHEDULER ====================

    @Test
    void coordinatorScheduler_delegatesToCoordinatorAgent() {
        taskBoard.create("Plan refactoring", "Extract interfaces");

        StubAgent coordinator = new StubAgent("coordinator", AgentState.IDLE);
        CoordinatorScheduler scheduler = new CoordinatorScheduler(coordinator);

        StubAgent worker = new StubAgent("worker", AgentState.IDLE);

        scheduler.dispatch(taskBoard, List.of(worker)).block(Duration.ofSeconds(5));

        // Coordinator should have been called with the task summary
        assertEquals(1, coordinator.callCount.get());
    }

    @Test
    void coordinatorScheduler_dispatchSingleTask() {
        Task task = taskBoard.create("Single task", "Just one thing");
        StubAgent coordinator = new StubAgent("coordinator", AgentState.IDLE);
        StubAgent worker = new StubAgent("worker", AgentState.IDLE);

        CoordinatorScheduler scheduler = new CoordinatorScheduler(coordinator);
        scheduler.dispatchTask(task, worker).block(Duration.ofSeconds(5));

        // Task should be assigned and in-progress
        assertEquals(worker.id(), task.owner());
        assertEquals(TaskStatus.IN_PROGRESS, task.status());
        // Coordinator should have been called
        assertEquals(1, coordinator.callCount.get());
    }

    // ==================== TEAM MANAGER ====================

    @Test
    void teamManager_createTeamAddAgentsAndDelete() {
        DefaultTeamManager manager = new DefaultTeamManager();

        // Create team
        Team team = manager.create("engineering");
        assertNotNull(team);
        assertEquals("engineering", team.name());
        assertTrue(team.agents().isEmpty());

        // Add agents
        StubAgent agent1 = new StubAgent("dev-1", AgentState.IDLE);
        StubAgent agent2 = new StubAgent("dev-2", AgentState.IDLE);
        manager.addAgent("engineering", agent1);
        manager.addAgent("engineering", agent2);

        Team retrieved = manager.get("engineering");
        assertEquals(2, retrieved.agents().size());

        // Remove one agent
        manager.removeAgent("engineering", "dev-1");
        assertEquals(1, manager.get("engineering").agents().size());

        // Delete team
        manager.delete("engineering");
        assertNull(manager.get("engineering"));
    }

    // ==================== CONCURRENT TASK OPERATIONS ====================

    @Test
    void concurrentTaskOperations_createAndUpdateSafely() throws Exception {
        int threadCount = 8;
        int tasksPerThread = 25;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch createLatch = new CountDownLatch(threadCount);

        // Phase 1: concurrent creates
        for (int t = 0; t < threadCount; t++) {
            final int threadIdx = t;
            executor.submit(
                    () -> {
                        try {
                            for (int i = 0; i < tasksPerThread; i++) {
                                taskBoard.create("Task-" + threadIdx + "-" + i, "desc");
                            }
                        } finally {
                            createLatch.countDown();
                        }
                    });
        }

        assertTrue(createLatch.await(10, TimeUnit.SECONDS));
        assertEquals(threadCount * tasksPerThread, taskBoard.list().size());

        // Phase 2: concurrent updates on different tasks
        CountDownLatch updateLatch = new CountDownLatch(threadCount * tasksPerThread);
        List<Task> allTasks = taskBoard.list();
        for (Task task : allTasks) {
            executor.submit(
                    () -> {
                        try {
                            taskBoard.update(task.id(), TaskStatus.IN_PROGRESS);
                            taskBoard.update(task.id(), TaskStatus.COMPLETED);
                        } finally {
                            updateLatch.countDown();
                        }
                    });
        }

        assertTrue(updateLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // All tasks should be completed
        long completedCount =
                taskBoard.list().stream()
                        .filter(t -> t.status() == TaskStatus.COMPLETED)
                        .count();
        assertEquals(threadCount * tasksPerThread, completedCount);
    }

    // ==================== STUB AGENT ====================

    /**
     * Minimal stub Agent for integration testing. Always returns a text response and tracks call
     * count.
     */
    static class StubAgent implements Agent {
        private final String agentId;
        private final AgentState agentState;
        final AtomicInteger callCount = new AtomicInteger(0);

        StubAgent(String agentId, AgentState state) {
            this.agentId = agentId;
            this.agentState = state;
        }

        @Override
        public Mono<Msg> call(Msg input) {
            callCount.incrementAndGet();
            return Mono.just(Msg.of(MsgRole.ASSISTANT, "Done: " + input.text()));
        }

        @Override
        public String id() {
            return agentId;
        }

        @Override
        public String name() {
            return agentId;
        }

        @Override
        public AgentState state() {
            return agentState;
        }

        @Override
        public void interrupt() {
            // no-op
        }
    }
}
