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
package io.kairo.core.model.anthropic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kairo.api.context.SystemPromptSegment;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelCapability;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ProviderPipeline;
import io.kairo.api.model.ToolVerbosity;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.core.model.JsonSchemaGenerator;
import io.kairo.core.model.ModelCapabilityRegistry;
import io.kairo.core.model.ToolDescriptionAdapter;
import java.util.List;
import java.util.Map;

/**
 * Builds Anthropic Messages API request bodies.
 *
 * <p>Handles serialization of messages, tools, system prompts (including multi-segment cache
 * control), structured output, extended thinking, and effort parameters into the Anthropic JSON
 * format.
 */
public class AnthropicRequestBuilder implements ProviderPipeline.RequestBuilder<String> {

    private final ObjectMapper objectMapper;
    private final ComplexityEstimator complexityEstimator = new ComplexityEstimator();
    private final ToolDescriptionAdapter toolAdapter = new ToolDescriptionAdapter();

    /**
     * Create a new request builder.
     *
     * @param objectMapper the Jackson ObjectMapper for JSON operations
     */
    public AnthropicRequestBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Build the Anthropic Messages API request body.
     *
     * @param messages the conversation messages
     * @param config model configuration
     * @param stream whether this is a streaming request
     * @return JSON string of the request body
     */
    @Override
    public String build(List<Msg> messages, ModelConfig config, boolean stream)
            throws JsonProcessingException {
        return buildRequestBody(messages, config, stream);
    }

    /**
     * Build the Anthropic Messages API request body (legacy entry point retained for backward
     * compatibility with intra-module callers).
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

        // Extract system prompt: from config or from SYSTEM messages
        String systemPrompt = config.systemPrompt();
        if (systemPrompt == null || systemPrompt.isBlank()) {
            systemPrompt =
                    messages.stream()
                            .filter(m -> m.role() == MsgRole.SYSTEM)
                            .map(Msg::text)
                            .reduce((a, b) -> a + "\n" + b)
                            .orElse(null);
        }

        // Structured output: inject JSON schema constraint into system prompt
        if (config.responseSchema() != null) {
            String schemaJson =
                    JsonSchemaGenerator.generateSchema(config.responseSchema(), objectMapper)
                            .toString();
            String schemaInstruction =
                    "\n\nYou MUST respond with valid JSON matching this schema: "
                            + schemaJson
                            + "\nDo NOT include any text outside the JSON object.";
            systemPrompt = (systemPrompt != null ? systemPrompt : "") + schemaInstruction;
        }

        // Priority 1: Multi-segment system prompt
        if (config.systemPromptSegments() != null && !config.systemPromptSegments().isEmpty()) {
            ArrayNode systemArray = objectMapper.createArrayNode();

            for (SystemPromptSegment segment : config.systemPromptSegments()) {
                ObjectNode block = objectMapper.createObjectNode();
                block.put("type", "text");
                block.put("text", segment.content());

                // Add cache_control for cacheable segments (GLOBAL and SESSION)
                if (segment.isCacheable()) {
                    ObjectNode cacheControl = objectMapper.createObjectNode();
                    cacheControl.put("type", "ephemeral");
                    block.set("cache_control", cacheControl);
                }

                systemArray.add(block);
            }

            root.set("system", systemArray);
        }
        // Priority 2: Legacy static/dynamic parts
        else if (systemPrompt != null && !systemPrompt.isBlank()) {
            Map<String, String> parts = config.systemPromptParts();
            if (parts != null
                    && parts.containsKey("staticPrefix")
                    && parts.containsKey("dynamicSuffix")
                    && !parts.get("staticPrefix").isBlank()
                    && !parts.get("dynamicSuffix").isBlank()) {
                // Serialize as array with cache_control on static prefix
                ArrayNode systemArray = objectMapper.createArrayNode();

                ObjectNode staticBlock = objectMapper.createObjectNode();
                staticBlock.put("type", "text");
                staticBlock.put("text", parts.get("staticPrefix"));
                ObjectNode cacheControl = objectMapper.createObjectNode();
                cacheControl.put("type", "ephemeral");
                staticBlock.set("cache_control", cacheControl);
                systemArray.add(staticBlock);

                ObjectNode dynamicBlock = objectMapper.createObjectNode();
                dynamicBlock.put("type", "text");
                dynamicBlock.put("text", parts.get("dynamicSuffix"));
                systemArray.add(dynamicBlock);

                root.set("system", systemArray);
            } else {
                // Priority 3: Single string
                root.put("system", systemPrompt);
            }
        }

        // Build messages array (exclude SYSTEM role)
        ArrayNode messagesNode = root.putArray("messages");
        for (Msg msg : messages) {
            if (msg.role() == MsgRole.SYSTEM) continue;
            messagesNode.add(convertMsg(msg));
        }

        // Tools — adapt descriptions by model verbosity and add cache_control on last tool
        List<ToolDefinition> toolsToSerialize = config.tools();
        ModelCapability capability = ModelCapabilityRegistry.lookup(config.model());
        ToolVerbosity verbosity =
                config.toolVerbosity() != null
                        ? config.toolVerbosity()
                        : capability.toolVerbosity();
        toolsToSerialize = toolAdapter.adaptForModel(toolsToSerialize, verbosity);
        if (toolsToSerialize != null && !toolsToSerialize.isEmpty()) {
            ArrayNode toolsNode = root.putArray("tools");
            for (int i = 0; i < toolsToSerialize.size(); i++) {
                ObjectNode toolNode = convertTool(toolsToSerialize.get(i));
                // Add cache_control on the last tool definition for prompt caching
                if (i == toolsToSerialize.size() - 1) {
                    ObjectNode cacheCtl = objectMapper.createObjectNode();
                    cacheCtl.put("type", "ephemeral");
                    toolNode.set("cache_control", cacheCtl);
                }
                toolsNode.add(toolNode);
            }
        }

        // Extended thinking — use dynamic budget from complexity if not explicitly configured
        if (config.thinking() != null && config.thinking().enabled()) {
            ObjectNode thinkingNode = root.putObject("thinking");
            thinkingNode.put("type", "enabled");
            thinkingNode.put("budget_tokens", config.thinking().budgetTokens());
        } else if (capability.supportsThinking() && capability.thinkingBudgetRange() != null) {
            // Auto-enable thinking with dynamic budget based on conversation complexity
            Integer explicitBudget = config.thinkingBudget();
            int budget;
            if (explicitBudget != null && explicitBudget > 0) {
                budget = capability.thinkingBudgetRange().clamp(explicitBudget);
            } else {
                int complexity = complexityEstimator.estimateComplexity(messages);
                budget = complexityEstimator.thinkingBudget(capability, complexity);
            }
            if (budget > 0) {
                ObjectNode thinkingNode = root.putObject("thinking");
                thinkingNode.put("type", "enabled");
                thinkingNode.put("budget_tokens", budget);
            }
        }

        // Correct: Map effort to output_config.effort (Anthropic API format)
        if (config.effort() != null) {
            double effort = config.effort();
            if (effort >= 0.0 && effort <= 1.0) {
                String effortLevel;
                if (effort <= 0.25) {
                    effortLevel = "low";
                } else if (effort <= 0.75) {
                    effortLevel = "medium";
                } else {
                    effortLevel = "high";
                }
                // Put in output_config object (Anthropic API format)
                ObjectNode outputConfig;
                if (root.has("output_config")) {
                    outputConfig = (ObjectNode) root.get("output_config");
                } else {
                    outputConfig = root.putObject("output_config");
                }
                outputConfig.put("effort", effortLevel);
                // DO NOT touch thinking mode — effort and thinking are independent controls
            }
        }

        return objectMapper.writeValueAsString(root);
    }

    /**
     * Resolve the system prompt from messages and config.
     *
     * @param messages the conversation messages
     * @param config model configuration
     * @return the resolved system prompt, or empty string if none
     */
    static String resolveSystemPrompt(List<Msg> messages, ModelConfig config) {
        String sp = config.systemPrompt();
        if (sp != null && !sp.isBlank()) return sp;
        return messages.stream()
                .filter(m -> m.role() == MsgRole.SYSTEM)
                .map(Msg::text)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private ObjectNode convertMsg(Msg msg) {
        ObjectNode msgNode = objectMapper.createObjectNode();
        String role =
                switch (msg.role()) {
                    case USER, TOOL -> "user";
                    case ASSISTANT -> "assistant";
                    case SYSTEM ->
                            throw new IllegalArgumentException(
                                    "SYSTEM messages should be extracted separately");
                };
        msgNode.put("role", role);

        List<Content> contents = msg.contents();
        boolean hasCacheControl =
                msg.metadata() != null && msg.metadata().containsKey("cache_control");
        // Single text content -> use string form (unless cache_control is needed)
        if (contents.size() == 1
                && contents.get(0) instanceof Content.TextContent tc
                && !hasCacheControl) {
            msgNode.put("content", tc.text());
        } else {
            ArrayNode contentArray = msgNode.putArray("content");
            for (int i = 0; i < contents.size(); i++) {
                ObjectNode contentNode = convertContent(contents.get(i));
                // Add cache_control to the last content block of a cache-hinted message
                if (hasCacheControl && i == contents.size() - 1) {
                    ObjectNode cc = objectMapper.createObjectNode();
                    cc.put("type", msg.metadata().get("cache_control").toString());
                    contentNode.set("cache_control", cc);
                }
                contentArray.add(contentNode);
            }
        }
        return msgNode;
    }

    private ObjectNode convertContent(Content content) {
        ObjectNode node = objectMapper.createObjectNode();
        if (content instanceof Content.TextContent tc) {
            node.put("type", "text");
            node.put("text", tc.text());
        } else if (content instanceof Content.ImageContent ic) {
            node.put("type", "image");
            ObjectNode source = node.putObject("source");
            if (ic.url() != null) {
                source.put("type", "url");
                source.put("url", ic.url());
            } else if (ic.data() != null) {
                source.put("type", "base64");
                source.put("media_type", ic.mediaType());
                source.put("data", java.util.Base64.getEncoder().encodeToString(ic.data()));
            }
        } else if (content instanceof Content.ToolUseContent tu) {
            node.put("type", "tool_use");
            node.put("id", tu.toolId());
            node.put("name", tu.toolName());
            node.set("input", objectMapper.valueToTree(tu.input()));
        } else if (content instanceof Content.ToolResultContent tr) {
            node.put("type", "tool_result");
            node.put("tool_use_id", tr.toolUseId());
            node.put("content", tr.content());
            if (tr.isError()) {
                node.put("is_error", true);
            }
        } else if (content instanceof Content.ThinkingContent tc) {
            node.put("type", "thinking");
            node.put("thinking", tc.thinking());
        }
        return node;
    }

    private ObjectNode convertTool(ToolDefinition tool) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", tool.name());
        node.put("description", tool.description());

        ObjectNode inputSchema = objectMapper.createObjectNode();
        inputSchema.put("type", tool.inputSchema().type());
        if (tool.inputSchema().properties() != null) {
            ObjectNode props = inputSchema.putObject("properties");
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
            ArrayNode reqArray = inputSchema.putArray("required");
            tool.inputSchema().required().forEach(reqArray::add);
        }
        node.set("input_schema", inputSchema);
        return node;
    }
}
