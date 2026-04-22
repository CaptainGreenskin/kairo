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
package io.kairo.core.model.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelCapability;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ToolVerbosity;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.core.model.JsonSchemaGenerator;
import io.kairo.core.model.ModelCapabilityRegistry;
import io.kairo.core.model.ToolDescriptionAdapter;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;

/**
 * Builds HTTP requests for the OpenAI Chat Completions API.
 *
 * <p>Handles serialization of messages, tools, structured output, and effort parameters into the
 * JSON request body format expected by OpenAI and compatible APIs.
 */
public class OpenAIRequestBuilder {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

    private final String apiKey;
    private final String baseUrl;
    private final String chatCompletionsPath;
    private final ObjectMapper objectMapper;
    private final ToolDescriptionAdapter toolAdapter = new ToolDescriptionAdapter();

    public OpenAIRequestBuilder(
            String apiKey, String baseUrl, String chatCompletionsPath, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.chatCompletionsPath = chatCompletionsPath;
        this.objectMapper = objectMapper;
    }

    /** Build an {@link HttpRequest} from a pre-serialized JSON body. */
    public HttpRequest buildHttpRequest(String jsonBody) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + chatCompletionsPath))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(DEFAULT_TIMEOUT)
                .build();
    }

    /**
     * Serialize messages and config into a JSON request body string.
     *
     * @param messages the conversation history
     * @param config model configuration
     * @param stream whether to enable streaming
     * @return JSON body as a string
     */
    public String buildRequestBody(List<Msg> messages, ModelConfig config, boolean stream)
            throws JsonProcessingException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", config.model());
        root.put("max_tokens", config.maxTokens());

        if (config.temperature() >= 0) {
            root.put("temperature", config.temperature());
        }
        if (stream) {
            root.put("stream", true);
        }

        // Build messages array
        ArrayNode messagesNode = root.putArray("messages");

        // Add system prompt as first message if present
        String systemPrompt = config.systemPrompt();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode sysMsg = objectMapper.createObjectNode();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messagesNode.add(sysMsg);
        }

        for (Msg msg : messages) {
            // OpenAI requires each tool result as a separate message
            if (msg.role() == MsgRole.TOOL) {
                for (Content c : msg.contents()) {
                    if (c instanceof Content.ToolResultContent tr) {
                        ObjectNode toolMsg = objectMapper.createObjectNode();
                        toolMsg.put("role", "tool");
                        toolMsg.put("content", tr.content());
                        toolMsg.put("tool_call_id", tr.toolUseId());
                        messagesNode.add(toolMsg);
                    }
                }
            } else {
                messagesNode.add(convertMsg(msg));
            }
        }

        // Tools — adapt descriptions by model verbosity
        List<ToolDefinition> toolsToSerialize = config.tools();
        ModelCapability capability = ModelCapabilityRegistry.lookup(config.model());
        ToolVerbosity verbosity =
                config.toolVerbosity() != null
                        ? config.toolVerbosity()
                        : capability.toolVerbosity();
        toolsToSerialize = toolAdapter.adaptForModel(toolsToSerialize, verbosity);
        if (toolsToSerialize != null && !toolsToSerialize.isEmpty()) {
            ArrayNode toolsNode = root.putArray("tools");
            for (ToolDefinition tool : toolsToSerialize) {
                toolsNode.add(convertTool(tool));
            }
        }

        // Structured output: use native response_format with json_schema
        if (config.responseSchema() != null) {
            ObjectNode responseFormat = root.putObject("response_format");
            responseFormat.put("type", "json_schema");
            ObjectNode jsonSchema = responseFormat.putObject("json_schema");
            jsonSchema.put("name", config.responseSchema().getSimpleName());
            jsonSchema.put("strict", true);
            jsonSchema.set(
                    "schema",
                    JsonSchemaGenerator.generateSchema(config.responseSchema(), objectMapper));
        }

        // Effort parameter — maps to OpenAI reasoning_effort
        if (config.effort() != null) {
            double effort = config.effort();
            if (effort >= 0.0 && effort <= 1.0) {
                // Map 0.0-1.0 to OpenAI reasoning_effort: "low", "medium", "high"
                String reasoningEffort;
                if (effort <= 0.33) {
                    reasoningEffort = "low";
                } else if (effort <= 0.66) {
                    reasoningEffort = "medium";
                } else {
                    reasoningEffort = "high";
                }
                root.put("reasoning_effort", reasoningEffort);
            }
        }

        return objectMapper.writeValueAsString(root);
    }

    private ObjectNode convertMsg(Msg msg) {
        ObjectNode msgNode = objectMapper.createObjectNode();
        String role =
                switch (msg.role()) {
                    case SYSTEM -> "system";
                    case USER -> "user";
                    case ASSISTANT -> "assistant";
                    case TOOL -> "tool";
                };
        msgNode.put("role", role);

        List<Content> contents = msg.contents();

        // Handle tool result messages
        if (msg.role() == MsgRole.TOOL
                && contents.size() == 1
                && contents.get(0) instanceof Content.ToolResultContent tr) {
            msgNode.put("content", tr.content());
            msgNode.put("tool_call_id", tr.toolUseId());
            return msgNode;
        }

        // Handle assistant messages with tool calls — OpenAI requires content as string (or null) +
        // tool_calls
        if (msg.role() == MsgRole.ASSISTANT) {
            List<Content.ToolUseContent> toolUses =
                    contents.stream()
                            .filter(Content.ToolUseContent.class::isInstance)
                            .map(Content.ToolUseContent.class::cast)
                            .toList();

            // Extract text content (skip ThinkingContent which GLM/OpenAI don't support)
            String textContent =
                    contents.stream()
                            .filter(Content.TextContent.class::isInstance)
                            .map(c -> ((Content.TextContent) c).text())
                            .reduce((a, b) -> a + "\n" + b)
                            .orElse(null);

            if (!toolUses.isEmpty()) {
                // Assistant message with tool calls
                if (textContent != null) {
                    msgNode.put("content", textContent);
                } else {
                    msgNode.putNull("content");
                }
                ArrayNode toolCallsArray = msgNode.putArray("tool_calls");
                for (Content.ToolUseContent tu : toolUses) {
                    ObjectNode tc = objectMapper.createObjectNode();
                    tc.put("id", tu.toolId());
                    tc.put("type", "function");
                    ObjectNode fn = tc.putObject("function");
                    fn.put("name", tu.toolName());
                    try {
                        fn.put("arguments", objectMapper.writeValueAsString(tu.input()));
                    } catch (JsonProcessingException e) {
                        fn.put("arguments", "{}");
                    }
                    toolCallsArray.add(tc);
                }
                return msgNode;
            }

            // Assistant message without tool calls — just text
            if (textContent != null) {
                msgNode.put("content", textContent);
            } else {
                msgNode.put("content", "");
            }
            return msgNode;
        }

        // User/System messages
        if (contents.size() == 1 && contents.get(0) instanceof Content.TextContent tc) {
            msgNode.put("content", tc.text());
        } else {
            ArrayNode contentArray = msgNode.putArray("content");
            for (Content content : contents) {
                if (content instanceof Content.TextContent tc) {
                    ObjectNode n = objectMapper.createObjectNode();
                    n.put("type", "text");
                    n.put("text", tc.text());
                    contentArray.add(n);
                } else if (content instanceof Content.ImageContent ic && ic.url() != null) {
                    ObjectNode n = objectMapper.createObjectNode();
                    n.put("type", "image_url");
                    ObjectNode imageUrl = n.putObject("image_url");
                    imageUrl.put("url", ic.url());
                    contentArray.add(n);
                }
            }
        }
        return msgNode;
    }

    private ObjectNode convertTool(ToolDefinition tool) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "function");
        ObjectNode fn = node.putObject("function");
        fn.put("name", tool.name());
        fn.put("description", tool.description());

        ObjectNode parameters = objectMapper.createObjectNode();
        parameters.put("type", tool.inputSchema().type());
        if (tool.inputSchema().properties() != null) {
            ObjectNode props = parameters.putObject("properties");
            tool.inputSchema()
                    .properties()
                    .forEach(
                            (key, schema) -> {
                                ObjectNode propNode = props.putObject(key);
                                propNode.put("type", schema.type());
                                if (schema.description() != null) {
                                    propNode.put("description", schema.description());
                                }
                            });
        }
        if (tool.inputSchema().required() != null) {
            ArrayNode reqArray = parameters.putArray("required");
            tool.inputSchema().required().forEach(reqArray::add);
        }
        fn.set("parameters", parameters);
        return node;
    }
}
