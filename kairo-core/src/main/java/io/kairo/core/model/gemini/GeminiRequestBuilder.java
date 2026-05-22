/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.core.model.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.tool.ToolDefinition;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Builds Google Generative Language API requests. Gemini's wire format differs notably from OpenAI
 * / Anthropic:
 *
 * <ul>
 *   <li>Role names are {@code "user"} and {@code "model"} (no {@code "assistant"}). System message
 *       is hoisted out of the message list into a top-level {@code systemInstruction}.
 *   <li>Messages live in {@code contents[]}, each with a {@code role} and a {@code parts[]} array.
 *       Text part is {@code {"text": "..."}}; tool-use is {@code {"functionCall": {"name": ...,
 *       "args": {...}}}}; tool-result is {@code {"functionResponse": {"name": ..., "response":
 *       {...}}}}.
 *   <li>Tools are declared as {@code tools: [{"functionDeclarations": [...]}]}.
 *   <li>Temperature / maxTokens live under {@code generationConfig}.
 *   <li>API key goes in the URL query string ({@code ?key=...}), not a header.
 * </ul>
 */
final class GeminiRequestBuilder {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private final String apiKey;
    private final String baseUrl;
    private final ObjectMapper mapper;

    GeminiRequestBuilder(String apiKey, String baseUrl, ObjectMapper mapper) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.mapper = mapper;
    }

    /** {@code POST <base>/v1beta/models/<model>:generateContent?key=...}. */
    HttpRequest buildRequest(String model, String body, boolean stream) {
        String endpoint = stream ? "streamGenerateContent?alt=sse&key=" : "generateContent?key=";
        URI uri = URI.create(baseUrl + "/v1beta/models/" + model + ":" + endpoint + apiKey);
        return HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "application/json")
                .timeout(DEFAULT_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    String buildBody(List<Msg> messages, ModelConfig config) throws JsonProcessingException {
        ObjectNode root = mapper.createObjectNode();

        // System instruction — hoisted out of the conversation. We pull both from
        // ModelConfig.systemPrompt (Kairo's canonical place) and from any system-role messages.
        StringBuilder systemText = new StringBuilder();
        if (config.systemPrompt() != null && !config.systemPrompt().isBlank()) {
            systemText.append(config.systemPrompt());
        }

        ArrayNode contents = mapper.createArrayNode();
        for (Msg msg : messages) {
            if (msg.role() == MsgRole.SYSTEM) {
                if (systemText.length() > 0) systemText.append("\n\n");
                for (Content c : msg.contents()) {
                    if (c instanceof Content.TextContent t) systemText.append(t.text());
                }
                continue;
            }
            ObjectNode message = mapper.createObjectNode();
            message.put("role", mapRole(msg.role()));
            ArrayNode parts = mapper.createArrayNode();
            for (Content c : msg.contents()) {
                appendPart(parts, c);
            }
            if (parts.isEmpty()) continue;
            message.set("parts", parts);
            contents.add(message);
        }
        root.set("contents", contents);

        if (systemText.length() > 0) {
            ObjectNode sys = mapper.createObjectNode();
            ArrayNode sysParts = mapper.createArrayNode();
            sysParts.add(mapper.createObjectNode().put("text", systemText.toString()));
            sys.set("parts", sysParts);
            root.set("systemInstruction", sys);
        }

        // Tools.
        List<ToolDefinition> tools = config.tools();
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArr = mapper.createArrayNode();
            ObjectNode toolBlock = mapper.createObjectNode();
            ArrayNode decls = mapper.createArrayNode();
            for (ToolDefinition t : tools) {
                ObjectNode fn = mapper.createObjectNode();
                fn.put("name", t.name());
                fn.put("description", t.description() == null ? "" : t.description());
                Object schema = t.inputSchema();
                if (schema != null) {
                    fn.set("parameters", mapper.valueToTree(schema));
                }
                decls.add(fn);
            }
            toolBlock.set("functionDeclarations", decls);
            toolsArr.add(toolBlock);
            root.set("tools", toolsArr);
        }

        // GenerationConfig.
        ObjectNode genConfig = mapper.createObjectNode();
        if (config.maxTokens() > 0) genConfig.put("maxOutputTokens", config.maxTokens());
        genConfig.put("temperature", config.temperature());
        root.set("generationConfig", genConfig);

        return mapper.writeValueAsString(root);
    }

    private void appendPart(ArrayNode parts, Content c) {
        if (c instanceof Content.TextContent t) {
            if (t.text() != null && !t.text().isEmpty()) {
                parts.add(mapper.createObjectNode().put("text", t.text()));
            }
        } else if (c instanceof Content.ToolUseContent tu) {
            ObjectNode part = mapper.createObjectNode();
            ObjectNode call = mapper.createObjectNode();
            call.put("name", tu.toolName());
            call.set("args", mapper.valueToTree(tu.input() == null ? Map.of() : tu.input()));
            part.set("functionCall", call);
            parts.add(part);
        } else if (c instanceof Content.ToolResultContent tr) {
            ObjectNode part = mapper.createObjectNode();
            ObjectNode resp = mapper.createObjectNode();
            // We don't have the original tool name here, only the toolUseId; Gemini wants name.
            // Best-effort: use the toolUseId as the name placeholder. Callers that need exact
            // round-trip should pass tool name through metadata once the SPI grows that field.
            resp.put("name", tr.toolUseId());
            ObjectNode response = mapper.createObjectNode();
            response.put("content", tr.content() == null ? "" : tr.content());
            if (tr.isError()) response.put("isError", true);
            resp.set("response", response);
            part.set("functionResponse", resp);
            parts.add(part);
        }
        // ImageContent + ThinkingContent: skipped for v1.3.
    }

    private static String mapRole(MsgRole role) {
        return switch (role) {
            case ASSISTANT -> "model";
            case TOOL -> "user"; // Gemini routes tool results as user-role functionResponse parts
            default -> "user";
        };
    }
}
