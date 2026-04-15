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
package io.kairo.core.message;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MsgBuilderTest {

    @Test
    @DisplayName("user() creates USER role message with text")
    void testUserFactory() {
        Msg msg = MsgBuilder.user("Hello world");

        assertEquals(MsgRole.USER, msg.role());
        assertEquals("Hello world", msg.text());
        assertNotNull(msg.id());
        assertNotNull(msg.timestamp());
        assertTrue(msg.tokenCount() > 0); // auto-estimated
    }

    @Test
    @DisplayName("system() creates SYSTEM role message")
    void testSystemFactory() {
        Msg msg = MsgBuilder.system("You are helpful");

        assertEquals(MsgRole.SYSTEM, msg.role());
        assertEquals("You are helpful", msg.text());
    }

    @Test
    @DisplayName("assistant() creates ASSISTANT role message")
    void testAssistantFactory() {
        Msg msg = MsgBuilder.assistant("Hi there!");

        assertEquals(MsgRole.ASSISTANT, msg.role());
        assertEquals("Hi there!", msg.text());
    }

    @Test
    @DisplayName("toolResultMsg() creates TOOL role with tool result content")
    void testToolResultMsg() {
        Msg msg = MsgBuilder.toolResultMsg("tu-1", "file contents here", false);

        assertEquals(MsgRole.TOOL, msg.role());
        assertEquals(1, msg.contents().size());
        assertInstanceOf(Content.ToolResultContent.class, msg.contents().get(0));

        Content.ToolResultContent trc = (Content.ToolResultContent) msg.contents().get(0);
        assertEquals("tu-1", trc.toolUseId());
        assertEquals("file contents here", trc.content());
        assertFalse(trc.isError());
    }

    @Test
    @DisplayName("Fluent builder with multiple content blocks")
    void testFluentBuilder() {
        Msg msg =
                MsgBuilder.create()
                        .role(MsgRole.USER)
                        .text("Look at this image")
                        .image("https://example.com/img.png", "image/png")
                        .build();

        assertEquals(MsgRole.USER, msg.role());
        assertEquals(2, msg.contents().size());
        assertInstanceOf(Content.TextContent.class, msg.contents().get(0));
        assertInstanceOf(Content.ImageContent.class, msg.contents().get(1));
    }

    @Test
    @DisplayName("Fluent builder with tool use content")
    void testToolUseContent() {
        Msg msg =
                MsgBuilder.create()
                        .role(MsgRole.ASSISTANT)
                        .toolUse("tu-1", "read_file", Map.of("path", "/test.txt"))
                        .build();

        assertEquals(1, msg.contents().size());
        Content.ToolUseContent tuc = (Content.ToolUseContent) msg.contents().get(0);
        assertEquals("tu-1", tuc.toolId());
        assertEquals("read_file", tuc.toolName());
        assertEquals("/test.txt", tuc.input().get("path"));
    }

    @Test
    @DisplayName("Fluent builder with thinking content")
    void testThinkingContent() {
        Msg msg =
                MsgBuilder.create()
                        .role(MsgRole.ASSISTANT)
                        .thinking("Let me think about this...", 1024)
                        .build();

        assertEquals(1, msg.contents().size());
        Content.ThinkingContent tc = (Content.ThinkingContent) msg.contents().get(0);
        assertEquals("Let me think about this...", tc.thinking());
        assertEquals(1024, tc.budgetTokens());
    }

    @Test
    @DisplayName("Custom ID overrides default")
    void testCustomId() {
        Msg msg = MsgBuilder.create().id("custom-id").role(MsgRole.USER).text("hello").build();

        assertEquals("custom-id", msg.id());
    }

    @Test
    @DisplayName("Metadata can be added")
    void testMetadata() {
        Msg msg =
                MsgBuilder.create()
                        .role(MsgRole.USER)
                        .text("hello")
                        .metadata("key", "value")
                        .build();

        assertEquals("value", msg.metadata().get("key"));
    }

    @Test
    @DisplayName("verbatimPreserved flag is set")
    void testVerbatimPreserved() {
        Msg msg =
                MsgBuilder.create()
                        .role(MsgRole.USER)
                        .text("important")
                        .verbatimPreserved(true)
                        .build();

        assertTrue(msg.verbatimPreserved());
    }

    @Test
    @DisplayName("sourceAgentId is set")
    void testSourceAgentId() {
        Msg msg =
                MsgBuilder.create()
                        .role(MsgRole.ASSISTANT)
                        .text("hi")
                        .sourceAgentId("agent-1")
                        .build();

        assertEquals("agent-1", msg.sourceAgentId());
    }

    @Test
    @DisplayName("Auto-estimated token count is reasonable")
    void testAutoEstimateTokens() {
        // 100 chars → ~25 tokens (100/4)
        Msg msg = MsgBuilder.user("a".repeat(100));

        assertEquals(25, msg.tokenCount());
    }

    @Test
    @DisplayName("estimateTokens with mixed content types")
    void testEstimateTokensMixed() {
        Msg msg =
                MsgBuilder.create()
                        .role(MsgRole.USER)
                        .text("hello world") // 11 chars
                        .addToolResult("tu-1", "result data", false) // 11 chars
                        .build();

        // (11 + 11) / 4 = 5
        assertTrue(msg.tokenCount() > 0);
    }

    @Test
    @DisplayName("imageData creates ImageContent with bytes")
    void testImageData() {
        byte[] data = new byte[] {1, 2, 3};
        Msg msg = MsgBuilder.create().role(MsgRole.USER).imageData(data, "image/jpeg").build();

        assertEquals(1, msg.contents().size());
        Content.ImageContent ic = (Content.ImageContent) msg.contents().get(0);
        assertNull(ic.url());
        assertEquals("image/jpeg", ic.mediaType());
        assertArrayEquals(data, ic.data());
    }

    @Test
    @DisplayName("Minimum token count is 1 even for empty message")
    void testMinTokenCount() {
        Msg msg = MsgBuilder.create().role(MsgRole.USER).text("").build();

        assertTrue(msg.tokenCount() >= 1);
    }
}
