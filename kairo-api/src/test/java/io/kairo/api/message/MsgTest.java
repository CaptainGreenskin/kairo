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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MsgTest {

    @Test
    void builderCreatesMessageWithRole() {
        Msg msg =
                Msg.builder().role(MsgRole.USER).addContent(new Content.TextContent("hi")).build();
        assertEquals(MsgRole.USER, msg.role());
        assertNotNull(msg.id());
        assertNotNull(msg.timestamp());
    }

    @Test
    void builderRoleRequired() {
        assertThrows(NullPointerException.class, () -> Msg.builder().build());
    }

    @Test
    void textConvenienceExtractsFirstText() {
        Msg msg =
                Msg.builder()
                        .role(MsgRole.ASSISTANT)
                        .addContent(new Content.TextContent("first"))
                        .addContent(new Content.TextContent("second"))
                        .build();
        assertEquals("first", msg.text());
    }

    @Test
    void textReturnsEmptyWhenNoTextContent() {
        Msg msg =
                Msg.builder()
                        .role(MsgRole.TOOL)
                        .addContent(new Content.ToolResultContent("id-1", "result", false))
                        .build();
        assertEquals("", msg.text());
    }

    @Test
    void ofFactoryCreatesSimpleTextMessage() {
        Msg msg = Msg.of(MsgRole.USER, "Hello");
        assertEquals(MsgRole.USER, msg.role());
        assertEquals("Hello", msg.text());
        assertEquals(1, msg.contents().size());
    }

    @Test
    void multipleContentBlocks() {
        Msg msg =
                Msg.builder()
                        .role(MsgRole.ASSISTANT)
                        .addContent(new Content.TextContent("I'll search"))
                        .addContent(
                                new Content.ToolUseContent(
                                        "tc-1", "search", Map.of("query", "java")))
                        .build();
        assertEquals(2, msg.contents().size());
        assertInstanceOf(Content.TextContent.class, msg.contents().get(0));
        assertInstanceOf(Content.ToolUseContent.class, msg.contents().get(1));
    }

    @Test
    void contentsListIsImmutable() {
        Msg msg =
                Msg.builder()
                        .role(MsgRole.USER)
                        .addContent(new Content.TextContent("test"))
                        .build();
        assertThrows(UnsupportedOperationException.class, () -> msg.contents().add(null));
    }

    @Test
    void metadataIsImmutable() {
        Msg msg =
                Msg.builder()
                        .role(MsgRole.USER)
                        .addContent(new Content.TextContent("test"))
                        .metadata("key", "value")
                        .build();
        assertEquals("value", msg.metadata().get("key"));
        assertThrows(UnsupportedOperationException.class, () -> msg.metadata().put("x", "y"));
    }

    @Test
    void contentsSetterReplacesContents() {
        Content.TextContent first = new Content.TextContent("first");
        Content.TextContent second = new Content.TextContent("second");
        Msg msg =
                Msg.builder()
                        .role(MsgRole.USER)
                        .addContent(first)
                        .contents(List.of(second))
                        .build();
        assertEquals(1, msg.contents().size());
        assertEquals("second", msg.text());
    }

    @Test
    void customTimestampAndTokenCount() {
        Instant ts = Instant.parse("2025-01-01T00:00:00Z");
        Msg msg =
                Msg.builder()
                        .role(MsgRole.SYSTEM)
                        .addContent(new Content.TextContent("sys"))
                        .timestamp(ts)
                        .tokenCount(42)
                        .build();
        assertEquals(ts, msg.timestamp());
        assertEquals(42, msg.tokenCount());
    }

    @Test
    void verbatimPreservedFlag() {
        Msg msg =
                Msg.builder()
                        .role(MsgRole.USER)
                        .addContent(new Content.TextContent("important"))
                        .verbatimPreserved(true)
                        .build();
        assertTrue(msg.verbatimPreserved());
    }

    @Test
    void sourceAgentId() {
        Msg msg =
                Msg.builder()
                        .role(MsgRole.ASSISTANT)
                        .addContent(new Content.TextContent("reply"))
                        .sourceAgentId("agent-42")
                        .build();
        assertEquals("agent-42", msg.sourceAgentId());
    }

    @Test
    void toStringContainsRoleAndId() {
        Msg msg = Msg.of(MsgRole.USER, "test");
        String str = msg.toString();
        assertTrue(str.contains("USER"));
        assertTrue(str.contains("Msg{"));
    }

    @Test
    void customId() {
        Msg msg =
                Msg.builder()
                        .id("custom-id")
                        .role(MsgRole.USER)
                        .addContent(new Content.TextContent("test"))
                        .build();
        assertEquals("custom-id", msg.id());
    }
}
