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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.message.Content;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.model.ProviderPipeline;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Parses OpenAI Chat Completions API responses into {@link ModelResponse} objects. */
public class OpenAIResponseParser implements ProviderPipeline.ResponseParser<String> {

    private final ObjectMapper objectMapper;

    public OpenAIResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** SPI entry point — delegates to {@link #parseResponse(String)}. */
    @Override
    public ModelResponse parse(String raw) throws JsonProcessingException {
        return parseResponse(raw);
    }

    /** Parse a JSON response body into a {@link ModelResponse}. */
    public ModelResponse parseResponse(String responseBody) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        String id = root.path("id").asText();
        String model = root.path("model").asText();

        List<Content> contents = new ArrayList<>();
        JsonNode choices = root.path("choices");
        String finishReason = null;

        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode choice = choices.get(0);
            finishReason = choice.path("finish_reason").asText(null);
            JsonNode message = choice.path("message");

            // Text content
            String textContent = message.path("content").asText(null);
            if (textContent != null) {
                contents.add(new Content.TextContent(textContent));
            }

            // Tool calls
            JsonNode toolCalls = message.path("tool_calls");
            if (toolCalls.isArray()) {
                for (JsonNode tc : toolCalls) {
                    String toolCallId = tc.path("id").asText();
                    JsonNode fn = tc.path("function");
                    String fnName = fn.path("name").asText();
                    String argsStr = fn.path("arguments").asText("{}");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> args = objectMapper.readValue(argsStr, Map.class);
                    contents.add(new Content.ToolUseContent(toolCallId, fnName, args));
                }
            }
        }

        ModelResponse.StopReason stopReason = parseFinishReason(finishReason);

        // Parse usage
        JsonNode usageNode = root.path("usage");
        ModelResponse.Usage usage =
                new ModelResponse.Usage(
                        usageNode.path("prompt_tokens").asInt(0),
                        usageNode.path("completion_tokens").asInt(0),
                        0,
                        0);

        return new ModelResponse(id, contents, usage, stopReason, model);
    }

    /** Map an OpenAI finish_reason string to a {@link ModelResponse.StopReason}. */
    public ModelResponse.StopReason parseFinishReason(String reason) {
        if (reason == null) return null;
        return switch (reason) {
            case "stop" -> ModelResponse.StopReason.END_TURN;
            case "tool_calls" -> ModelResponse.StopReason.TOOL_USE;
            case "length" -> ModelResponse.StopReason.MAX_TOKENS;
            default -> ModelResponse.StopReason.END_TURN;
        };
    }
}
