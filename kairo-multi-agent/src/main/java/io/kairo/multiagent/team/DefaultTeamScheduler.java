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
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.task.Task;
import io.kairo.api.task.TaskBoard;
import io.kairo.api.task.TaskStatus;
import io.kairo.api.team.TeamScheduler;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Default implementation of {@link TeamScheduler} that dispatches unblocked tasks to available
 * (idle or completed) agents.
 *
 * <p>The scheduling algorithm:
 *
 * <ol>
 *   <li>Retrieve all unblocked tasks from the task board
 *   <li>Find all agents in IDLE or COMPLETED state
 *   <li>Zip tasks with available agents (1:1 assignment)
 *   <li>Dispatch each task to its assigned agent asynchronously
 * </ol>
 */
public class DefaultTeamScheduler implements TeamScheduler {

    private static final Logger log = LoggerFactory.getLogger(DefaultTeamScheduler.class);

    @Override
    public Mono<Void> dispatch(TaskBoard taskBoard, List<Agent> agents) {
        List<Task> unblockedTasks = SchedulingPrelude.unblockedTasks(taskBoard);

        if (unblockedTasks.isEmpty()) {
            log.debug("No unblocked tasks to dispatch");
            return Mono.empty();
        }

        // Find available agents (IDLE or COMPLETED state)
        List<Agent> availableAgents = SchedulingPrelude.availableAgents(agents);

        if (availableAgents.isEmpty()) {
            log.debug("No available agents for {} unblocked tasks", unblockedTasks.size());
            return Mono.empty();
        }

        log.info(
                "Dispatching {} tasks to {} available agents",
                Math.min(unblockedTasks.size(), availableAgents.size()),
                availableAgents.size());

        return Flux.fromIterable(unblockedTasks)
                .zipWith(Flux.fromIterable(availableAgents))
                .flatMap(
                        tuple -> {
                            Task task = tuple.getT1();
                            Agent agent = tuple.getT2();
                            // Mark task as in-progress before dispatching
                            task.setOwner(agent.id());
                            task.setStatus(TaskStatus.IN_PROGRESS);
                            return dispatchTask(task, agent);
                        })
                .then();
    }

    @Override
    public Mono<Void> dispatchTask(Task task, Agent agent) {
        String taskPrompt =
                String.format("Task: %s\n\nDescription:\n%s", task.subject(), task.description());

        Msg taskMsg = Msg.of(MsgRole.USER, taskPrompt);

        log.info("Dispatching task #{} '{}' to agent '{}'", task.id(), task.subject(), agent.id());

        return agent.call(taskMsg)
                .doOnSuccess(
                        result -> {
                            task.setStatus(TaskStatus.COMPLETED);
                            log.info(
                                    "Task #{} '{}' completed by agent '{}'",
                                    task.id(),
                                    task.subject(),
                                    agent.id());
                        })
                .doOnError(
                        e -> {
                            task.setStatus(TaskStatus.FAILED);
                            log.error(
                                    "Task #{} '{}' failed on agent '{}': {}",
                                    task.id(),
                                    task.subject(),
                                    agent.id(),
                                    e.getMessage());
                        })
                .then();
    }
}
