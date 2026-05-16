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
package io.kairo.core.a2a.remote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class A2aMessageCodecTest {

    @Test
    void roundTripTextMessage() {
        Msg original = Msg.of(MsgRole.USER, "hello world");
        String json = A2aMessageCodec.encode(original);
        Msg decoded = A2aMessageCodec.decode(json);

        assertThat(decoded.id()).isEqualTo(original.id());
        assertThat(decoded.role()).isEqualTo(MsgRole.USER);
        assertThat(decoded.text()).isEqualTo("hello world");
    }

    @Test
    void roundTripToolUseContent() {
        Msg original =
                Msg.builder()
                        .role(MsgRole.ASSISTANT)
                        .addContent(
                                new Content.ToolUseContent(
                                        "tool-1", "bash", Map.of("command", "ls -la")))
                        .build();

        String json = A2aMessageCodec.encode(original);
        Msg decoded = A2aMessageCodec.decode(json);

        assertThat(decoded.contents()).hasSize(1);
        Content content = decoded.contents().get(0);
        assertThat(content).isInstanceOf(Content.ToolUseContent.class);
        Content.ToolUseContent tuc = (Content.ToolUseContent) content;
        assertThat(tuc.toolName()).isEqualTo("bash");
        assertThat(tuc.input()).containsEntry("command", "ls -la");
    }

    @Test
    void roundTripToolResultContent() {
        Msg original =
                Msg.builder()
                        .role(MsgRole.USER)
                        .addContent(new Content.ToolResultContent("tool-1", "file list", false))
                        .build();

        String json = A2aMessageCodec.encode(original);
        Msg decoded = A2aMessageCodec.decode(json);

        Content content = decoded.contents().get(0);
        assertThat(content).isInstanceOf(Content.ToolResultContent.class);
        Content.ToolResultContent trc = (Content.ToolResultContent) content;
        assertThat(trc.toolUseId()).isEqualTo("tool-1");
        assertThat(trc.content()).isEqualTo("file list");
        assertThat(trc.isError()).isFalse();
    }

    @Test
    void roundTripThinkingContent() {
        Msg original =
                Msg.builder()
                        .role(MsgRole.ASSISTANT)
                        .addContent(new Content.ThinkingContent("deep thought", 1024, "sig-abc"))
                        .build();

        String json = A2aMessageCodec.encode(original);
        Msg decoded = A2aMessageCodec.decode(json);

        Content content = decoded.contents().get(0);
        assertThat(content).isInstanceOf(Content.ThinkingContent.class);
        Content.ThinkingContent thc = (Content.ThinkingContent) content;
        assertThat(thc.thinking()).isEqualTo("deep thought");
        assertThat(thc.budgetTokens()).isEqualTo(1024);
        assertThat(thc.signature()).isEqualTo("sig-abc");
    }

    @Test
    void roundTripMetadata() {
        Msg original =
                Msg.builder()
                        .role(MsgRole.USER)
                        .addContent(new Content.TextContent("hi"))
                        .metadata("synthetic", true)
                        .metadata("count", 42)
                        .sourceAgentId("agent-1")
                        .build();

        String json = A2aMessageCodec.encode(original);
        Msg decoded = A2aMessageCodec.decode(json);

        assertThat(decoded.metadata()).containsEntry("synthetic", true);
        assertThat(decoded.metadata()).containsEntry("count", 42);
        assertThat(decoded.sourceAgentId()).isEqualTo("agent-1");
    }

    @Test
    void multipleContents() {
        Msg original =
                Msg.builder()
                        .role(MsgRole.ASSISTANT)
                        .addContent(new Content.TextContent("prefix"))
                        .addContent(
                                new Content.ToolUseContent("t1", "read", Map.of("path", "/tmp")))
                        .build();

        String json = A2aMessageCodec.encode(original);
        Msg decoded = A2aMessageCodec.decode(json);

        assertThat(decoded.contents()).hasSize(2);
        assertThat(decoded.contents().get(0)).isInstanceOf(Content.TextContent.class);
        assertThat(decoded.contents().get(1)).isInstanceOf(Content.ToolUseContent.class);
    }

    @Test
    void invalidJsonThrowsCodecException() {
        assertThatThrownBy(() -> A2aMessageCodec.decode("not json"))
                .isInstanceOf(A2aMessageCodec.A2aCodecException.class);
    }

    @Test
    void unknownContentTypeFallsBackToText() {
        String json =
                "{\"id\":\"x\",\"role\":\"USER\",\"contents\":[{\"type\":\"unknown_type\"}],"
                        + "\"metadata\":{},\"timestamp\":\"2026-05-15T00:00:00Z\",\"tokenCount\":0,"
                        + "\"verbatimPreserved\":false}";

        Msg decoded = A2aMessageCodec.decode(json);
        assertThat(decoded.contents()).hasSize(1);
        assertThat(decoded.text()).contains("unknown_type");
    }
}
