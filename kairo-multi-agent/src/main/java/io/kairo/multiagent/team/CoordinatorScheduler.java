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

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentState;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.task.Task;
import io.kairo.api.task.TaskBoard;
import io.kairo.api.task.TaskStatus;
import io.kairo.api.team.TeamScheduler;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * An agent-driven scheduler that uses a coordinator {@link Agent} to plan and dispatch tasks
 * intelligently, rather than simple 1:1 zip assignment.
 *
 * <p>The coordinator receives a summary of all pending tasks and available workers, then uses its
 * orchestration tools (agent_spawn, task_update, etc.) to plan and dispatch work.
 */
public class CoordinatorScheduler implements TeamScheduler {

    private static final Logger log = LoggerFactory.getLogger(CoordinatorScheduler.class);

    private final Agent coordinator;

    public CoordinatorScheduler(Agent coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public Mono<Void> dispatch(TaskBoard taskBoard, List<Agent> agents) {
        List<Task> unblockedTasks = taskBoard.getUnblockedTasks();

        if (unblockedTasks.isEmpty()) {
            log.debug("No unblocked tasks to dispatch");
            return Mono.empty();
        }

        List<Agent> availableAgents =
                agents.stream()
                        .filter(
                                a ->
                                        a.state() == AgentState.IDLE
                                                || a.state() == AgentState.COMPLETED)
                        .toList();

        String taskSummary = summarizeTasks(unblockedTasks);
        String agentSummary = summarizeAgents(availableAgents);

        String coordinatorPrompt =
                String.format(
                        "You have %d unblocked tasks and %d available workers.\n\n"
                                + "## Tasks\n%s\n\n"
                                + "## Available Workers\n%s\n\n"
                                + "Plan the optimal assignment considering task dependencies and"
                                + " worker capabilities. "
                                + "Then dispatch each task to an appropriate worker using"
                                + " agent_spawn.",
                        unblockedTasks.size(), availableAgents.size(), taskSummary, agentSummary);

        log.info(
                "Coordinator dispatching {} tasks to {} workers",
                unblockedTasks.size(),
                availableAgents.size());

        return coordinator.call(Msg.of(MsgRole.USER, coordinatorPrompt)).then();
    }

    @Override
    public Mono<Void> dispatchTask(Task task, Agent agent) {
        // For single-task dispatch, build a focused prompt
        String prompt =
                String.format(
                        "Dispatch this task to the worker:\n\n"
                                + "Task #%s: %s\nDescription: %s\n\n"
                                + "Worker: %s\n\n"
                                + "Use agent_spawn to assign this task.",
                        task.id(), task.subject(), task.description(), agent.id());

        task.setOwner(agent.id());
        task.setStatus(TaskStatus.IN_PROGRESS);

        return coordinator.call(Msg.of(MsgRole.USER, prompt)).then();
    }

    /** Build a text summary of tasks for the coordinator. */
    private String summarizeTasks(List<Task> tasks) {
        return tasks.stream()
                .map(
                        t ->
                                String.format(
                                        "- Task #%s: %s [%s]\n  %s",
                                        t.id(),
                                        t.subject(),
                                        t.status(),
                                        t.description() != null
                                                ? truncate(t.description(), 200)
                                                : "(no description)"))
                .collect(Collectors.joining("\n"));
    }

    /** Build a text summary of available agents. */
    private String summarizeAgents(List<Agent> agents) {
        if (agents.isEmpty()) {
            return "(no workers available — use agent_spawn to create new ones)";
        }
        return agents.stream()
                .map(a -> String.format("- %s [%s]", a.id(), a.state()))
                .collect(Collectors.joining("\n"));
    }

    /** Truncate a string to maxLen characters. */
    private static String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen) + "...";
    }
}
