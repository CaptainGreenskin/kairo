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
package io.kairo.api.team;

import io.kairo.api.agent.Agent;
import io.kairo.api.task.Task;
import io.kairo.api.task.TaskBoard;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Dispatches tasks from a task board to agents.
 *
 * @deprecated since v0.10 — replaced by the Expert Team {@code TeamCoordinator} SPI. The
 *     implementation in {@code kairo-multi-agent} will be promoted to an internal strategy and the
 *     public SPI removed in v0.11.
 */
@Deprecated(since = "0.10", forRemoval = true)
public interface TeamScheduler {

    /**
     * Dispatch all unblocked tasks on the board to available agents.
     *
     * @param taskBoard the task board
     * @param agents the available agents
     * @return a Mono completing when all dispatched tasks are assigned
     */
    Mono<Void> dispatch(TaskBoard taskBoard, List<Agent> agents);

    /**
     * Dispatch a specific task to a specific agent.
     *
     * @param task the task to dispatch
     * @param agent the agent to assign it to
     * @return a Mono completing when the task is dispatched
     */
    Mono<Void> dispatchTask(Task task, Agent agent);
}
