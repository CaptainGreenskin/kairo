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

import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.team.MessageBus;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.tool.ToolHandler;
import java.util.Map;

/**
 * Sends a message to another agent in the team via the message bus.
 *
 * <p>Enables inter-agent communication for coordination, status updates, and collaborative problem
 * solving.
 */
@Tool(
        name = "send_message",
        description = "Send a message to another agent in the team.",
        category = ToolCategory.AGENT_AND_TASK)
public class SendMessageTool implements ToolHandler {

    @ToolParam(description = "The ID of the recipient agent", required = true)
    private String recipientId;

    @ToolParam(description = "The message content to send", required = true)
    private String content;

    private final MessageBus messageBus;
    private final String currentAgentId;

    /**
     * Create a new SendMessageTool.
     *
     * @param messageBus the message bus for sending messages
     * @param currentAgentId the ID of the agent using this tool
     */
    public SendMessageTool(MessageBus messageBus, String currentAgentId) {
        this.messageBus = messageBus;
        this.currentAgentId = currentAgentId;
    }

    @Override
    public ToolResult execute(Map<String, Object> input) {
        String to = (String) input.get("recipientId");
        String content = (String) input.get("content");

        if (to == null || to.isBlank()) {
            return new ToolResult(null, "Parameter 'recipientId' is required", true, Map.of());
        }
        if (content == null || content.isBlank()) {
            return new ToolResult(null, "Parameter 'content' is required", true, Map.of());
        }

        Msg msg = Msg.of(MsgRole.USER, content);
        messageBus.send(currentAgentId, to, msg).block();
        return new ToolResult(null, String.format("Message sent to agent %s", to), false, Map.of());
    }
}
