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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kairo.api.tool.ToolResult;
import io.kairo.multiagent.team.InProcessMessageBus;
import io.kairo.tools.agent.SendMessageTool;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for agent messaging tools using the real {@link InProcessMessageBus}
 * implementation. Verifies end-to-end inter-agent messaging behaviour.
 */
@Tag("integration")
class AgentToolsIntegrationIT {

    private InProcessMessageBus messageBus;

    @BeforeEach
    void setUp() {
        messageBus = new InProcessMessageBus();
    }

    @Test
    void sendMessage_deliversToRecipient() {
        messageBus.registerAgent("agent-a");
        messageBus.registerAgent("agent-b");

        SendMessageTool sendTool = new SendMessageTool(messageBus, "agent-a");
        ToolResult result =
                sendTool.execute(Map.of("recipientId", "agent-b", "content", "Hello from A"));

        assertFalse(result.isError());
        assertTrue(result.content().contains("Message sent to agent agent-b"));

        var messages = messageBus.poll("agent-b");
        assertEquals(1, messages.size());
        assertEquals("Hello from A", messages.get(0).text());
    }

    @Test
    void sendMessage_toSpecificAgent_deliversCorrectly() {
        messageBus.registerAgent("leader");
        messageBus.registerAgent("worker-1");
        messageBus.registerAgent("worker-2");

        SendMessageTool worker1Send = new SendMessageTool(messageBus, "worker-1");
        worker1Send.execute(Map.of("recipientId", "leader", "content", "Task done"));

        SendMessageTool worker2Send = new SendMessageTool(messageBus, "worker-2");
        worker2Send.execute(Map.of("recipientId", "leader", "content", "Need help"));

        var leaderMessages = messageBus.poll("leader");
        assertEquals(2, leaderMessages.size());

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
