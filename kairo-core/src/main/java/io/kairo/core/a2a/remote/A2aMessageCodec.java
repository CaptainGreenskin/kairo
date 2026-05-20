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
package io.kairo.core.a2a.remote;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * JSON codec for serializing/deserializing {@link Msg} over the A2A HTTP wire protocol.
 *
 * <p>Wire format:
 *
 * <pre>{@code
 * {
 *   "id": "msg-uuid",
 *   "role": "USER",
 *   "contents": [
 *     {"type": "text", "text": "hello"},
 *     {"type": "tool_use", "toolId": "...", "toolName": "...", "input": {...}},
 *     {"type": "tool_result", "toolUseId": "...", "content": "...", "isError": false}
 *   ],
 *   "metadata": {...},
 *   "timestamp": "2026-05-15T12:00:00Z",
 *   "tokenCount": 42,
 *   "sourceAgentId": "agent-1"
 * }
 * }</pre>
 */
public final class A2aMessageCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private A2aMessageCodec() {}

    public static String encode(Msg msg) {
        try {
            return MAPPER.writeValueAsString(toJson(msg));
        } catch (JsonProcessingException e) {
            throw new A2aCodecException("Failed to encode Msg", e);
        }
    }

    public static Msg decode(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            return fromJson(node);
        } catch (JsonProcessingException e) {
            throw new A2aCodecException("Failed to decode Msg", e);
        }
    }

    static ObjectNode toJson(Msg msg) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("id", msg.id());
        node.put("role", msg.role().name());

        ArrayNode contentsArray = node.putArray("contents");
        for (Content content : msg.contents()) {
            contentsArray.add(encodeContent(content));
        }

        ObjectNode metaNode = node.putObject("metadata");
        for (Map.Entry<String, Object> entry : msg.metadata().entrySet()) {
            metaNode.putPOJO(entry.getKey(), entry.getValue());
        }

        node.put("timestamp", msg.timestamp().toString());
        node.put("tokenCount", msg.tokenCount());
        if (msg.sourceAgentId() != null) {
            node.put("sourceAgentId", msg.sourceAgentId());
        }
        node.put("verbatimPreserved", msg.verbatimPreserved());
        return node;
    }

    static Msg fromJson(JsonNode node) {
        Msg.Builder builder =
                Msg.builder()
                        .id(node.path("id").asText())
                        .role(MsgRole.valueOf(node.path("role").asText()))
                        .timestamp(Instant.parse(node.path("timestamp").asText()))
                        .tokenCount(node.path("tokenCount").asInt(0))
                        .verbatimPreserved(node.path("verbatimPreserved").asBoolean(false));

        String sourceAgentId = node.path("sourceAgentId").asText(null);
        if (sourceAgentId != null) {
            builder.sourceAgentId(sourceAgentId);
        }

        JsonNode contentsNode = node.path("contents");
        if (contentsNode.isArray()) {
            for (JsonNode contentNode : contentsNode) {
                builder.addContent(decodeContent(contentNode));
            }
        }

        JsonNode metaNode = node.path("metadata");
        if (metaNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = metaNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                builder.metadata(entry.getKey(), nodeToValue(entry.getValue()));
            }
        }

        return builder.build();
    }

    private static ObjectNode encodeContent(Content content) {
        ObjectNode node = MAPPER.createObjectNode();
        switch (content) {
            case Content.TextContent tc -> {
                node.put("type", "text");
                node.put("text", tc.text());
            }
            case Content.ToolUseContent tuc -> {
                node.put("type", "tool_use");
                node.put("toolId", tuc.toolId());
                node.put("toolName", tuc.toolName());
                node.putPOJO("input", tuc.input());
            }
            case Content.ToolResultContent trc -> {
                node.put("type", "tool_result");
                node.put("toolUseId", trc.toolUseId());
                node.put("content", trc.content());
                node.put("isError", trc.isError());
            }
            case Content.ImageContent ic -> {
                node.put("type", "image");
                if (ic.url() != null) node.put("url", ic.url());
                if (ic.mediaType() != null) node.put("mediaType", ic.mediaType());
            }
            case Content.ThinkingContent thc -> {
                node.put("type", "thinking");
                node.put("thinking", thc.thinking());
                node.put("budgetTokens", thc.budgetTokens());
                if (thc.signature() != null) node.put("signature", thc.signature());
            }
        }
        return node;
    }

    @SuppressWarnings("unchecked")
    private static Content decodeContent(JsonNode node) {
        String type = node.path("type").asText("");
        return switch (type) {
            case "text" -> new Content.TextContent(node.path("text").asText(""));
            case "tool_use" -> {
                Map<String, Object> input = new HashMap<>();
                JsonNode inputNode = node.path("input");
                if (inputNode.isObject()) {
                    try {
                        input = MAPPER.treeToValue(inputNode, Map.class);
                    } catch (JsonProcessingException e) {
                        // fallback to empty
                    }
                }
                yield new Content.ToolUseContent(
                        node.path("toolId").asText(""), node.path("toolName").asText(""), input);
            }
            case "tool_result" ->
                    new Content.ToolResultContent(
                            node.path("toolUseId").asText(""),
                            node.path("content").asText(""),
                            node.path("isError").asBoolean(false));
            case "image" ->
                    new Content.ImageContent(
                            node.path("url").asText(null),
                            node.path("mediaType").asText(null),
                            null);
            case "thinking" ->
                    new Content.ThinkingContent(
                            node.path("thinking").asText(""),
                            node.path("budgetTokens").asInt(0),
                            node.path("signature").asText(null));
            default -> new Content.TextContent("[unknown content type: " + type + "]");
        };
    }

    private static Object nodeToValue(JsonNode node) {
        if (node.isTextual()) return node.asText();
        if (node.isInt()) return node.asInt();
        if (node.isLong()) return node.asLong();
        if (node.isDouble()) return node.asDouble();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isNull()) return null;
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonNode item : node) {
                list.add(nodeToValue(item));
            }
            return list;
        }
        if (node.isObject()) {
            Map<String, Object> map = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                map.put(entry.getKey(), nodeToValue(entry.getValue()));
            }
            return map;
        }
        return node.toString();
    }

    public static final class A2aCodecException extends RuntimeException {
        public A2aCodecException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
