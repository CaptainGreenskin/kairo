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
package io.kairo.api.message;

import java.util.Map;

/**
 * A polymorphic content block within a {@link Msg}.
 *
 * <p>Uses Java sealed interfaces to represent the different content types that can appear in a
 * conversation message.
 */
public sealed interface Content {

    /**
     * Plain text content.
     *
     * @param text the text body
     */
    record TextContent(String text) implements Content {}

    /**
     * Image content, either by URL or inline bytes.
     *
     * @param url the image URL (may be null if data is provided)
     * @param mediaType the MIME type (e.g. "image/png")
     * @param data raw image bytes (may be null if url is provided)
     */
    record ImageContent(String url, String mediaType, byte[] data) implements Content {}

    /**
     * A tool invocation request from the model.
     *
     * @param toolId unique identifier for this tool-use block
     * @param toolName the name of the tool to invoke
     * @param input the tool input parameters
     */
    record ToolUseContent(String toolId, String toolName, Map<String, Object> input)
            implements Content {}

    /**
     * The result of a tool invocation.
     *
     * @param toolUseId the id of the corresponding {@link ToolUseContent}
     * @param content the textual result
     * @param isError whether the tool execution resulted in an error
     */
    record ToolResultContent(String toolUseId, String content, boolean isError)
            implements Content {}

    /**
     * Extended thinking / chain-of-thought content.
     *
     * @param thinking the thinking text
     * @param budgetTokens the token budget allocated for thinking
     */
    record ThinkingContent(String thinking, int budgetTokens) implements Content {}
}
