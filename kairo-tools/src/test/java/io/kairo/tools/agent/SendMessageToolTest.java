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
package io.kairo.tools.agent;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.tool.ToolResult;
import io.kairo.multiagent.team.InProcessMessageBus;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SendMessageToolTest {

    private InProcessMessageBus messageBus;
    private SendMessageTool tool;

    @BeforeEach
    void setUp() {
        messageBus = new InProcessMessageBus();
        tool = new SendMessageTool(messageBus, "sender-agent");
    }

    @Test
    void sendMessageSuccessfully() {
        ToolResult result =
                tool.execute(Map.of("recipientId", "agent-B", "content", "Hello agent B"));
        assertFalse(result.isError());
        assertTrue(result.content().contains("Message sent"));

        // Verify message arrived
        var messages = messageBus.poll("agent-B");
        assertEquals(1, messages.size());
        assertEquals("Hello agent B", messages.get(0).text());
    }

    @Test
    void sendMessageMissingRecipientId() {
        ToolResult result = tool.execute(Map.of("content", "hello"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("'recipientId' is required"));
    }

    @Test
    void sendMessageMissingContent() {
        ToolResult result = tool.execute(Map.of("recipientId", "agent-B"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("'content' is required"));
    }

    @Test
    void sendMessageBlankRecipientId() {
        ToolResult result = tool.execute(Map.of("recipientId", "  ", "content", "hi"));
        assertTrue(result.isError());
    }

    @Test
    void sendMessageBlankContent() {
        ToolResult result = tool.execute(Map.of("recipientId", "agent-B", "content", "  "));
        assertTrue(result.isError());
    }

    @Test
    void sendMultipleMessages() {
        tool.execute(Map.of("recipientId", "agent-B", "content", "msg1"));
        tool.execute(Map.of("recipientId", "agent-B", "content", "msg2"));

        var messages = messageBus.poll("agent-B");
        assertEquals(2, messages.size());
        assertEquals("msg1", messages.get(0).text());
        assertEquals("msg2", messages.get(1).text());
    }

    @Test
    void sendToDifferentRecipients() {
        tool.execute(Map.of("recipientId", "agent-B", "content", "for B"));
        tool.execute(Map.of("recipientId", "agent-C", "content", "for C"));

        assertEquals(1, messageBus.poll("agent-B").size());
        assertEquals(1, messageBus.poll("agent-C").size());
    }
}
