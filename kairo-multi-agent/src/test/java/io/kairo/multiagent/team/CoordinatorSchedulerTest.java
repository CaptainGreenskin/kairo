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
package io.kairo.multiagent.team;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentState;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.task.Task;
import io.kairo.api.task.TaskStatus;
import io.kairo.multiagent.task.DefaultTaskBoard;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CoordinatorSchedulerTest {

    private Agent mockCoordinator;
    private CoordinatorScheduler scheduler;
    private DefaultTaskBoard taskBoard;

    @BeforeEach
    void setUp() {
        mockCoordinator = mock(Agent.class);
        when(mockCoordinator.call(any(Msg.class)))
                .thenReturn(Mono.just(Msg.of(MsgRole.ASSISTANT, "dispatched")));
        scheduler = new CoordinatorScheduler(mockCoordinator);
        taskBoard = new DefaultTaskBoard();
    }

    private Agent mockAgent(String id, AgentState state) {
        Agent agent = mock(Agent.class);
        when(agent.id()).thenReturn(id);
        when(agent.state()).thenReturn(state);
        when(agent.call(any(Msg.class))).thenReturn(Mono.just(Msg.of(MsgRole.ASSISTANT, "done")));
        return agent;
    }

    @Test
    void dispatch_noUnblockedTasks_completesEmpty() {
        // Empty task board should return Mono.empty()
        List<Agent> agents = List.of(mockAgent("worker-1", AgentState.IDLE));

        StepVerifier.create(scheduler.dispatch(taskBoard, agents)).verifyComplete();

        // Coordinator should not be called at all
        verify(mockCoordinator, never()).call(any());
    }

    @Test
    void dispatch_withTasks_callsCoordinator() {
        taskBoard.create("Task A", "Do something");
        taskBoard.create("Task B", "Do another thing");

        List<Agent> agents =
                List.of(
                        mockAgent("worker-1", AgentState.IDLE),
                        mockAgent("worker-2", AgentState.IDLE));

        StepVerifier.create(scheduler.dispatch(taskBoard, agents)).verifyComplete();

        // Coordinator should be called with a prompt containing the tasks
        verify(mockCoordinator, times(1)).call(any(Msg.class));
    }

    @Test
    void dispatch_onlyCountsAvailableAgents() {
        taskBoard.create("Task A", "Do something");

        Agent idleAgent = mockAgent("idle-1", AgentState.IDLE);
        Agent runningAgent = mockAgent("running-1", AgentState.RUNNING);
        Agent completedAgent = mockAgent("completed-1", AgentState.COMPLETED);

        List<Agent> agents = List.of(idleAgent, runningAgent, completedAgent);

        StepVerifier.create(scheduler.dispatch(taskBoard, agents)).verifyComplete();

        // Should still call coordinator; agent filtering happens in the prompt
        verify(mockCoordinator, times(1)).call(any(Msg.class));
    }

    @Test
    void dispatchTask_setsOwnerAndStatus() {
        Task task = taskBoard.create("Single task", "Execute this");
        Agent agent = mockAgent("worker-1", AgentState.IDLE);

        StepVerifier.create(scheduler.dispatchTask(task, agent)).verifyComplete();

        // Task owner and status should be set
        assertEquals("worker-1", task.owner(), "Task owner should be set to agent id");
        assertEquals(TaskStatus.IN_PROGRESS, task.status(), "Task status should be IN_PROGRESS");

        // Coordinator should be called
        verify(mockCoordinator, times(1)).call(any(Msg.class));
    }

    @Test
    void dispatch_blockedTasksAreSkipped() {
        Task taskA = taskBoard.create("Task A", "First task");
        Task taskB = taskBoard.create("Task B", "Blocked task");
        taskBoard.addDependency(taskB.id(), taskA.id());

        List<Agent> agents = List.of(mockAgent("worker-1", AgentState.IDLE));

        StepVerifier.create(scheduler.dispatch(taskBoard, agents)).verifyComplete();

        // Coordinator should be called for unblocked tasks only
        verify(mockCoordinator, times(1)).call(any(Msg.class));
    }
}
