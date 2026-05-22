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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.message.Content;
import io.kairo.api.model.ModelResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Parses Gemini {@code generateContent} responses into {@link ModelResponse}. Gemini puts the
 * payload under {@code candidates[0].content.parts[]}; each part is either {@code {text: ...}} or
 * {@code {functionCall: {name, args}}}. Stop reason lives at {@code candidates[0].finishReason};
 * token counts live under {@code usageMetadata}.
 */
final class GeminiResponseParser {

    private final ObjectMapper mapper;

    GeminiResponseParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    ModelResponse parse(String body, String modelName) throws JsonProcessingException {
        JsonNode root = mapper.readTree(body);
        String responseId =
                root.has("responseId")
                        ? root.get("responseId").asText()
                        : UUID.randomUUID().toString();

        List<Content> contents = new ArrayList<>();
        ModelResponse.StopReason stopReason = null;

        JsonNode candidates = root.path("candidates");
        if (candidates.isArray() && !candidates.isEmpty()) {
            JsonNode first = candidates.get(0);
            stopReason = parseFinishReason(first.path("finishReason").asText(null));
            JsonNode parts = first.path("content").path("parts");
            if (parts.isArray()) {
                for (JsonNode part : parts) {
                    JsonNode text = part.get("text");
                    if (text != null && !text.isNull()) {
                        contents.add(new Content.TextContent(text.asText()));
                        continue;
                    }
                    JsonNode functionCall = part.get("functionCall");
                    if (functionCall != null) {
                        String name = functionCall.path("name").asText();
                        JsonNode argsNode = functionCall.path("args");
                        Map<String, Object> args = nodeToMap(argsNode);
                        contents.add(new Content.ToolUseContent(toolUseId(name), name, args));
                    }
                }
            }
        }

        JsonNode usage = root.path("usageMetadata");
        ModelResponse.Usage tokenUsage =
                new ModelResponse.Usage(
                        usage.path("promptTokenCount").asInt(0),
                        usage.path("candidatesTokenCount").asInt(0),
                        0,
                        0);

        return new ModelResponse(responseId, contents, tokenUsage, stopReason, modelName);
    }

    static ModelResponse.StopReason parseFinishReason(String reason) {
        if (reason == null) return null;
        return switch (reason) {
            case "STOP" -> ModelResponse.StopReason.END_TURN;
            case "MAX_TOKENS" -> ModelResponse.StopReason.MAX_TOKENS;
            case "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII" ->
                    ModelResponse.StopReason.END_TURN;
            default -> ModelResponse.StopReason.END_TURN;
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nodeToMap(JsonNode argsNode) {
        if (argsNode == null || argsNode.isMissingNode() || argsNode.isNull()) return Map.of();
        try {
            return mapper.treeToValue(argsNode, Map.class);
        } catch (Exception e) {
            // Fall back: extract field-by-field.
            Map<String, Object> out = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = argsNode.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                JsonNode v = entry.getValue();
                if (v.isTextual()) out.put(entry.getKey(), v.asText());
                else if (v.isNumber()) out.put(entry.getKey(), v.numberValue());
                else if (v.isBoolean()) out.put(entry.getKey(), v.booleanValue());
                else out.put(entry.getKey(), v.toString());
            }
            return out;
        }
    }

    private static String toolUseId(String functionName) {
        return functionName + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
