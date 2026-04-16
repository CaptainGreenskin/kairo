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
package io.kairo.examples.multiagent;

import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.task.Task;
import io.kairo.api.task.TaskStatus;
import io.kairo.multiagent.task.DefaultTaskBoard;
import io.kairo.multiagent.team.InProcessMessageBus;
import java.util.List;

/**
 * Demonstrates multi-agent orchestration: TaskBoard and MessageBus.
 *
 * <p>No LLM API key needed — exercises the orchestration layer directly.
 *
 * <p>Usage:
 *
 * <pre>
 *   mvn exec:java -pl kairo-examples -Dexec.mainClass="io.kairo.examples.multiagent.MultiAgentExample"
 * </pre>
 */
public class MultiAgentExample {

    public static void main(String[] args) {
        System.out.println("=== Kairo Multi-Agent Orchestration Example ===");
        System.out.println("No API key needed — demonstrates TaskBoard + MessageBus");
        System.out.println("=============================================\n");

        // --- Part 1: TaskBoard ---
        System.out.println("📋 Part 1: TaskBoard — Task lifecycle & dependency tracking\n");

        DefaultTaskBoard taskBoard = new DefaultTaskBoard();

        Task designTask =
                taskBoard.create(
                        "Design API schema", "Design the REST API schema for the user service");
        System.out.println("  Created: #" + designTask.id() + " — " + designTask.subject());

        Task implTask =
                taskBoard.create(
                        "Implement endpoints",
                        "Implement the REST endpoints based on the API schema");
        System.out.println("  Created: #" + implTask.id() + " — " + implTask.subject());

        Task testTask =
                taskBoard.create("Write tests", "Write integration tests for all endpoints");
        System.out.println("  Created: #" + testTask.id() + " — " + testTask.subject());

        // Add dependencies: impl depends on design, test depends on impl
        taskBoard.addDependency(implTask.id(), designTask.id());
        taskBoard.addDependency(testTask.id(), implTask.id());
        System.out.println("\n  Dependencies: design → impl → test");

        // Check what's unblocked
        List<Task> unblocked = taskBoard.getUnblockedTasks();
        System.out.println(
                "  Unblocked tasks: "
                        + unblocked.stream()
                                .map(t -> "#" + t.id() + "(" + t.subject() + ")")
                                .toList());

        // Complete design → impl becomes unblocked
        taskBoard.update(designTask.id(), TaskStatus.IN_PROGRESS);
        System.out.println("\n  #" + designTask.id() + " → IN_PROGRESS");
        taskBoard.update(designTask.id(), TaskStatus.COMPLETED);
        System.out.println("  #" + designTask.id() + " → COMPLETED ✅");

        unblocked = taskBoard.getUnblockedTasks();
        System.out.println(
                "  Unblocked now: "
                        + unblocked.stream()
                                .map(t -> "#" + t.id() + "(" + t.subject() + ")")
                                .toList());

        // Complete impl → test becomes unblocked
        taskBoard.update(implTask.id(), TaskStatus.COMPLETED);
        System.out.println("\n  #" + implTask.id() + " → COMPLETED ✅");

        unblocked = taskBoard.getUnblockedTasks();
        System.out.println(
                "  Unblocked now: "
                        + unblocked.stream()
                                .map(t -> "#" + t.id() + "(" + t.subject() + ")")
                                .toList());

        // --- Part 2: MessageBus ---
        System.out.println("\n📨 Part 2: MessageBus — Inter-agent communication\n");

        InProcessMessageBus messageBus = new InProcessMessageBus();

        // Register agents
        messageBus.registerAgent("architect");
        messageBus.registerAgent("coder");
        messageBus.registerAgent("tester");

        // Subscribe to messages (reactive)
        messageBus
                .receive("coder")
                .subscribe(msg -> System.out.println("  [coder received] " + msg.text()));
        messageBus
                .receive("tester")
                .subscribe(msg -> System.out.println("  [tester received] " + msg.text()));

        // Send direct message
        System.out.println("  architect → coder: sending design spec...");
        messageBus
                .send(
                        "architect",
                        "coder",
                        Msg.of(
                                MsgRole.ASSISTANT,
                                "API schema finalized: GET/POST/PUT/DELETE /users/{id}"))
                .block();

        // Broadcast to all
        System.out.println("\n  coder → broadcast: code ready...");
        messageBus
                .broadcast(
                        "coder",
                        Msg.of(MsgRole.ASSISTANT, "UserController implemented, ready for testing"))
                .block();

        // Poll (non-reactive)
        List<Msg> testerInbox = messageBus.poll("tester");
        System.out.println("\n  tester polled " + testerInbox.size() + " message(s)");

        // Cleanup
        messageBus.unregisterAgent("architect");
        messageBus.unregisterAgent("coder");
        messageBus.unregisterAgent("tester");

        System.out.println("\n========================================");
        System.out.println("  Multi-Agent Example complete!");
        System.out.println("  - TaskBoard: DAG dependency tracking");
        System.out.println("  - MessageBus: send/broadcast/poll");
        System.out.println("========================================");
    }
}
