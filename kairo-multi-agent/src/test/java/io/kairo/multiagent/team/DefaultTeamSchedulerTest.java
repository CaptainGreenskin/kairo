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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentState;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.multiagent.task.DefaultTaskBoard;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DefaultTeamSchedulerTest {

    private DefaultTeamScheduler scheduler;
    private DefaultTaskBoard taskBoard;

    @BeforeEach
    void setUp() {
        scheduler = new DefaultTeamScheduler();
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
    void dispatchShouldAssignTasksToIdleAgents() {
        taskBoard.create("Task A", "Do A");
        taskBoard.create("Task B", "Do B");

        Agent a1 = mockAgent("agent-1", AgentState.IDLE);
        Agent a2 = mockAgent("agent-2", AgentState.IDLE);

        StepVerifier.create(scheduler.dispatch(taskBoard, List.of(a1, a2))).verifyComplete();

        verify(a1).call(any(Msg.class));
        verify(a2).call(any(Msg.class));
    }

    @Test
    void dispatchShouldSkipRunningAgents() {
        taskBoard.create("Task A", "Do A");

        Agent running = mockAgent("agent-busy", AgentState.RUNNING);
        Agent idle = mockAgent("agent-idle", AgentState.IDLE);

        StepVerifier.create(scheduler.dispatch(taskBoard, List.of(running, idle))).verifyComplete();

        verify(running, never()).call(any(Msg.class));
        verify(idle).call(any(Msg.class));
    }

    @Test
    void dispatchShouldHandleNoUnblockedTasks() {
        // No tasks at all
        Agent idle = mockAgent("agent-1", AgentState.IDLE);

        StepVerifier.create(scheduler.dispatch(taskBoard, List.of(idle))).verifyComplete();

        verify(idle, never()).call(any(Msg.class));
    }

    @Test
    void dispatchShouldHandleNoAvailableAgents() {
        taskBoard.create("Task A", "Do A");

        Agent running = mockAgent("agent-busy", AgentState.RUNNING);

        StepVerifier.create(scheduler.dispatch(taskBoard, List.of(running))).verifyComplete();

        verify(running, never()).call(any(Msg.class));
    }

    @Test
    void dispatchShouldAssignToCompletedAgents() {
        taskBoard.create("Task A", "Do A");
        Agent completed = mockAgent("agent-done", AgentState.COMPLETED);

        StepVerifier.create(scheduler.dispatch(taskBoard, List.of(completed))).verifyComplete();

        verify(completed).call(any(Msg.class));
    }

    @Test
    void dispatchShouldLimitToAvailableAgentCount() {
        taskBoard.create("Task A", "Do A");
        taskBoard.create("Task B", "Do B");
        taskBoard.create("Task C", "Do C");

        // Only 1 agent available → only 1 task dispatched
        Agent idle = mockAgent("agent-1", AgentState.IDLE);

        StepVerifier.create(scheduler.dispatch(taskBoard, List.of(idle))).verifyComplete();

        verify(idle, times(1)).call(any(Msg.class));
    }

    @Test
    void dispatchShouldNotDispatchBlockedTasks() {
        var a = taskBoard.create("A", "");
        var b = taskBoard.create("B", "");
        taskBoard.addDependency(b.id(), a.id());

        Agent idle1 = mockAgent("agent-1", AgentState.IDLE);
        Agent idle2 = mockAgent("agent-2", AgentState.IDLE);

        StepVerifier.create(scheduler.dispatch(taskBoard, List.of(idle1, idle2))).verifyComplete();

        // Only task A should be dispatched (B is blocked)
        verify(idle1).call(any(Msg.class));
        verify(idle2, never()).call(any(Msg.class));
    }

    @Test
    void dispatchTaskShouldSendFormattedMessage() {
        var task = taskBoard.create("Build module", "Run mvn compile");
        Agent agent = mockAgent("worker", AgentState.IDLE);

        StepVerifier.create(scheduler.dispatchTask(task, agent)).verifyComplete();

        verify(agent).call(argThat(msg -> msg.text().contains("Build module")));
    }

    @Test
    void dispatchWithEmptyAgentListShouldComplete() {
        taskBoard.create("Task A", "Do A");

        StepVerifier.create(scheduler.dispatch(taskBoard, List.of())).verifyComplete();
    }

    @Test
    void dispatchWithEmptyTaskBoardShouldComplete() {
        Agent idle = mockAgent("agent-1", AgentState.IDLE);
        StepVerifier.create(scheduler.dispatch(taskBoard, List.of(idle))).verifyComplete();
    }
}
