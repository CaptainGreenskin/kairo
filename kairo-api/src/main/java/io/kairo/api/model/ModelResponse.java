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
package io.kairo.api.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.message.Content;
import java.util.List;
import java.util.Optional;

/**
 * Response from a model invocation.
 *
 * @param id the response identifier
 * @param contents the content blocks returned by the model
 * @param usage token usage statistics
 * @param stopReason why the model stopped generating
 * @param model the model identifier that produced this response
 */
public record ModelResponse(
        String id, List<Content> contents, Usage usage, StopReason stopReason, String model) {

    private static final ObjectMapper SHARED_MAPPER = new ObjectMapper();

    /**
     * Deserialize the first text content block as the given type using Jackson.
     *
     * @param <T> the target type
     * @param type the class to deserialize into
     * @return the deserialized object
     * @throws IllegalStateException if no text content is found or JSON parsing fails
     */
    public <T> T contentAs(Class<T> type) {
        String text =
                contents.stream()
                        .filter(Content.TextContent.class::isInstance)
                        .map(c -> ((Content.TextContent) c).text())
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "No text content found in response to deserialize as "
                                                        + type.getSimpleName()));
        try {
            return SHARED_MAPPER.readValue(text, type);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to parse response text as "
                            + type.getSimpleName()
                            + ": "
                            + e.getMessage(),
                    e);
        }
    }

    /**
     * Deserialize the first text content block as the given type, returning an empty Optional on
     * failure instead of throwing.
     *
     * @param <T> the target type
     * @param type the class to deserialize into
     * @return an Optional containing the deserialized object, or empty if parsing fails
     */
    public <T> Optional<T> contentAsOptional(Class<T> type) {
        try {
            return Optional.of(contentAs(type));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Token usage statistics for a model call.
     *
     * @param inputTokens tokens consumed by the input
     * @param outputTokens tokens produced in the output
     * @param cacheReadTokens tokens read from prompt cache
     * @param cacheCreationTokens tokens written to prompt cache
     */
    public record Usage(
            int inputTokens, int outputTokens, int cacheReadTokens, int cacheCreationTokens) {}

    /** Reason the model stopped generating. */
    public enum StopReason {
        /** Model reached a natural end of turn. */
        END_TURN,
        /** Model wants to invoke a tool. */
        TOOL_USE,
        /** Output hit the max token limit. */
        MAX_TOKENS,
        /** A configured stop sequence was encountered. */
        STOP_SEQUENCE
    }
}
