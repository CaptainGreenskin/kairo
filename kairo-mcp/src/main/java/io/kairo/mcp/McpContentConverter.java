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
package io.kairo.mcp;

import io.kairo.api.tool.ToolResult;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Collections;
import java.util.stream.Collectors;

/** Converts MCP {@link McpSchema.CallToolResult} to Kairo {@link ToolResult}. */
public final class McpContentConverter {

    private McpContentConverter() {}

    /**
     * Converts an MCP call-tool result to a Kairo {@link ToolResult}.
     *
     * @param result the MCP result
     * @param toolUseId the correlating tool-use request ID
     * @return the Kairo tool result
     */
    public static ToolResult convert(McpSchema.CallToolResult result, String toolUseId) {
        String text = extractText(result);
        boolean isError = result.isError() != null && result.isError();
        return new ToolResult(toolUseId, text, isError, Collections.emptyMap());
    }

    /** Extracts a textual representation from the MCP result content list. */
    private static String extractText(McpSchema.CallToolResult result) {
        if (result.content() == null || result.content().isEmpty()) {
            return "";
        }
        return result.content().stream()
                .map(McpContentConverter::contentToString)
                .collect(Collectors.joining("\n"));
    }

    private static String contentToString(McpSchema.Content content) {
        if (content instanceof McpSchema.TextContent textContent) {
            return textContent.text();
        }
        if (content instanceof McpSchema.ImageContent imageContent) {
            return "[image:" + imageContent.mimeType() + "]";
        }
        if (content instanceof McpSchema.EmbeddedResource embeddedResource) {
            McpSchema.ResourceContents rc = embeddedResource.resource();
            if (rc instanceof McpSchema.TextResourceContents textRc) {
                return textRc.text();
            }
            return "[embedded-resource]";
        }
        return content.toString();
    }
}
