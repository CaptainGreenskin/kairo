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

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ContentTest {

    @Test
    void textContentHoldsText() {
        Content.TextContent tc = new Content.TextContent("hello world");
        assertEquals("hello world", tc.text());
    }

    @Test
    void textContentEquality() {
        Content.TextContent a = new Content.TextContent("same");
        Content.TextContent b = new Content.TextContent("same");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void textContentInequality() {
        Content.TextContent a = new Content.TextContent("one");
        Content.TextContent b = new Content.TextContent("two");
        assertNotEquals(a, b);
    }

    @Test
    void imageContentWithUrl() {
        Content.ImageContent ic = new Content.ImageContent("https://img.png", "image/png", null);
        assertEquals("https://img.png", ic.url());
        assertEquals("image/png", ic.mediaType());
        assertNull(ic.data());
    }

    @Test
    void imageContentWithData() {
        byte[] data = new byte[] {1, 2, 3};
        Content.ImageContent ic = new Content.ImageContent(null, "image/jpeg", data);
        assertNull(ic.url());
        assertEquals("image/jpeg", ic.mediaType());
        assertArrayEquals(new byte[] {1, 2, 3}, ic.data());
    }

    @Test
    void toolUseContentFields() {
        Map<String, Object> input = Map.of("path", "/tmp", "recursive", true);
        Content.ToolUseContent tu = new Content.ToolUseContent("call-1", "bash", input);
        assertEquals("call-1", tu.toolId());
        assertEquals("bash", tu.toolName());
        assertEquals(input, tu.input());
    }

    @Test
    void toolResultContentFields() {
        Content.ToolResultContent tr =
                new Content.ToolResultContent("call-1", "file created", false);
        assertEquals("call-1", tr.toolUseId());
        assertEquals("file created", tr.content());
        assertFalse(tr.isError());
    }

    @Test
    void toolResultContentError() {
        Content.ToolResultContent tr =
                new Content.ToolResultContent("call-2", "permission denied", true);
        assertTrue(tr.isError());
    }

    @Test
    void thinkingContentFields() {
        Content.ThinkingContent tc = new Content.ThinkingContent("Let me think...", 5000);
        assertEquals("Let me think...", tc.thinking());
        assertEquals(5000, tc.budgetTokens());
    }

    @Test
    void sealedInterfacePatternMatching() {
        Content content = new Content.TextContent("test");
        assertTrue(content instanceof Content.TextContent);
        assertFalse(content instanceof Content.ImageContent);

        Content tool = new Content.ToolUseContent("id", "name", Map.of());
        assertTrue(tool instanceof Content.ToolUseContent);

        Content thinking = new Content.ThinkingContent("hmm", 100);
        assertTrue(thinking instanceof Content.ThinkingContent);
        assertEquals(100, ((Content.ThinkingContent) thinking).budgetTokens());
    }

    @Test
    void allVariantsAreContentInstances() {
        Content[] variants = {
            new Content.TextContent("a"),
            new Content.ImageContent("url", "image/png", null),
            new Content.ToolUseContent("id", "name", Map.of()),
            new Content.ToolResultContent("id", "result", false),
            new Content.ThinkingContent("think", 1000)
        };

        for (Content c : variants) {
            String type = classifyContent(c);
            assertNotNull(type);
        }
    }

    private static String classifyContent(Content c) {
        if (c instanceof Content.TextContent) return "text";
        if (c instanceof Content.ImageContent) return "image";
        if (c instanceof Content.ToolUseContent) return "tool_use";
        if (c instanceof Content.ToolResultContent) return "tool_result";
        if (c instanceof Content.ThinkingContent) return "thinking";
        return null;
    }
}
