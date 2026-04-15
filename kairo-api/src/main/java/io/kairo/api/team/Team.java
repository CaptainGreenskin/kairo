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
import io.kairo.api.task.TaskBoard;
import java.util.List;

/** A team of agents collaborating on a shared task board via a message bus. */
public class Team {

    private final String name;
    private final List<Agent> agents;
    private final TaskBoard taskBoard;
    private final MessageBus messageBus;

    public Team(String name, List<Agent> agents, TaskBoard taskBoard, MessageBus messageBus) {
        this.name = name;
        this.agents = agents;
        this.taskBoard = taskBoard;
        this.messageBus = messageBus;
    }

    /** The team name. */
    public String name() {
        return name;
    }

    /** The agents in this team. */
    public List<Agent> agents() {
        return agents;
    }

    /** The shared task board. */
    public TaskBoard taskBoard() {
        return taskBoard;
    }

    /** The message bus for inter-agent communication. */
    public MessageBus messageBus() {
        return messageBus;
    }
}
