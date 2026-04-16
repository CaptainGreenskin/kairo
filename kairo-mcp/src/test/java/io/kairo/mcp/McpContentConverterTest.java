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

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.tool.ToolResult;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class McpContentConverterTest {

    @Test
    void convertsTextContent() {
        McpSchema.CallToolResult result = new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("Hello world")), false);
        ToolResult tr = McpContentConverter.convert(result, "id1");
        assertEquals("Hello world", tr.content());
        assertFalse(tr.isError());
        assertEquals("id1", tr.toolUseId());
    }

    @Test
    void convertsErrorResult() {
        McpSchema.CallToolResult result = new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("Something failed")), true);
        ToolResult tr = McpContentConverter.convert(result, "id2");
        assertTrue(tr.isError());
        assertEquals("Something failed", tr.content());
    }

    @Test
    void convertsImageContent() {
        McpSchema.CallToolResult result = new McpSchema.CallToolResult(
                List.of(new McpSchema.ImageContent(null, "base64data", "image/png")), false);
        ToolResult tr = McpContentConverter.convert(result, "id3");
        assertEquals("[image:image/png]", tr.content());
    }

    @Test
    void handlesEmptyResult() {
        McpSchema.CallToolResult result = new McpSchema.CallToolResult(
                Collections.emptyList(), false);
        ToolResult tr = McpContentConverter.convert(result, "id4");
        assertEquals("", tr.content());
        assertFalse(tr.isError());
    }

    @Test
    void handlesNullContent() {
        McpSchema.CallToolResult result = new McpSchema.CallToolResult((java.util.List<McpSchema.Content>) null, false);
        ToolResult tr = McpContentConverter.convert(result, "id5");
        assertEquals("", tr.content());
    }

    @Test
    void handlesMultipleTextContents() {
        McpSchema.CallToolResult result = new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("line1"),
                        new McpSchema.TextContent("line2"),
                        new McpSchema.TextContent("line3")),
                false);
        ToolResult tr = McpContentConverter.convert(result, "id6");
        assertEquals("line1\nline2\nline3", tr.content());
    }
}
