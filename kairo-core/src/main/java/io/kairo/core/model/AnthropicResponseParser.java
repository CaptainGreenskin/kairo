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
package io.kairo.core.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.message.Content;
import io.kairo.api.model.ModelResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses Anthropic Messages API responses (both streaming and non-streaming) into Kairo model
 * objects.
 *
 * <p>Owns the {@link ObjectMapper} and all JSON-to-domain conversion logic: content block parsing,
 * stop reason mapping, and usage extraction.
 */
public class AnthropicResponseParser {

    private static final Logger log = LoggerFactory.getLogger(AnthropicResponseParser.class);

    private final ObjectMapper objectMapper;

    /**
     * Create a new response parser.
     *
     * @param objectMapper the Jackson ObjectMapper for JSON operations
     */
    public AnthropicResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Return the ObjectMapper used by this parser. */
    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    /**
     * Parse a non-streaming Anthropic API response body.
     *
     * @param responseBody the raw JSON response string
     * @return the parsed ModelResponse
     * @throws JsonProcessingException if JSON parsing fails
     */
    public ModelResponse parseResponse(String responseBody) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        String id = root.path("id").asText();
        String model = root.path("model").asText();

        List<Content> contents = new ArrayList<>();
        JsonNode contentNode = root.path("content");
        if (contentNode.isArray()) {
            for (JsonNode block : contentNode) {
                Content c = parseContentBlock(block);
                if (c != null) contents.add(c);
            }
        }

        ModelResponse.StopReason stopReason =
                parseStopReason(root.path("stop_reason").asText(null));
        ModelResponse.Usage usage = parseUsage(root.path("usage"));

        return new ModelResponse(id, contents, usage, stopReason, model);
    }

    /**
     * Parse a single content block from a JSON node.
     *
     * @param block the JSON node representing a content block
     * @return the parsed Content, or null for unknown types
     */
    public Content parseContentBlock(JsonNode block) {
        String type = block.path("type").asText();
        return switch (type) {
            case "text" -> new Content.TextContent(block.path("text").asText());
            case "thinking" -> new Content.ThinkingContent(block.path("thinking").asText(), 0);
            case "tool_use" -> {
                Map<String, Object> input =
                        objectMapper.convertValue(
                                block.path("input"),
                                objectMapper
                                        .getTypeFactory()
                                        .constructMapType(
                                                HashMap.class, String.class, Object.class));
                yield new Content.ToolUseContent(
                        block.path("id").asText(),
                        block.path("name").asText(),
                        input != null ? input : Map.of());
            }
            default -> {
                log.debug("Unknown content block type: {}", type);
                yield null;
            }
        };
    }

    /**
     * Map an Anthropic stop reason string to the domain enum.
     *
     * @param reason the raw stop reason string
     * @return the mapped StopReason, or null if the input is null
     */
    public ModelResponse.StopReason parseStopReason(String reason) {
        if (reason == null) return null;
        return switch (reason) {
            case "end_turn" -> ModelResponse.StopReason.END_TURN;
            case "tool_use" -> ModelResponse.StopReason.TOOL_USE;
            case "max_tokens" -> ModelResponse.StopReason.MAX_TOKENS;
            case "stop_sequence" -> ModelResponse.StopReason.STOP_SEQUENCE;
            default -> {
                log.debug("Unknown stop reason: {}", reason);
                yield ModelResponse.StopReason.END_TURN;
            }
        };
    }

    /**
     * Parse a usage JSON node into a Usage record.
     *
     * @param usageNode the JSON node containing usage data
     * @return the parsed Usage
     */
    public ModelResponse.Usage parseUsage(JsonNode usageNode) {
        if (usageNode.isMissingNode()) {
            return new ModelResponse.Usage(0, 0, 0, 0);
        }
        return new ModelResponse.Usage(
                usageNode.path("input_tokens").asInt(0),
                usageNode.path("output_tokens").asInt(0),
                usageNode.path("cache_read_input_tokens").asInt(0),
                usageNode.path("cache_creation_input_tokens").asInt(0));
    }
}
