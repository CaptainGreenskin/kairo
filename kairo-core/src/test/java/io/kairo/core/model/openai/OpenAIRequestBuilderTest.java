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

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.core.model.ModelProviderUtils;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAIRequestBuilderTest {

    private OpenAIRequestBuilder builder;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ObjectMapper om = ModelProviderUtils.createObjectMapper();
        builder =
                new OpenAIRequestBuilder(
                        "test-key", "https://api.openai.com", "/v1/chat/completions", om);
    }

    @Test
    void buildHttpRequestSetsHeadersAndUri() {
        var request = builder.buildHttpRequest("{\"model\":\"gpt-4o\"}");
        assertEquals("https://api.openai.com/v1/chat/completions", request.uri().toString());
        assertEquals("Bearer test-key", request.headers().firstValue("Authorization").orElse(null));
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElse(null));
        assertEquals("POST", request.method());
    }

    @Test
    void buildRequestBodyBasicFields() throws Exception {
        ModelConfig config =
                ModelConfig.builder().model("gpt-4o").maxTokens(1024).temperature(0.7).build();
        String body =
                builder.buildRequestBody(List.of(Msg.of(MsgRole.USER, "Hello")), config, false);
        JsonNode root = mapper.readTree(body);

        assertEquals("gpt-4o", root.path("model").asText());
        assertEquals(1024, root.path("max_tokens").asInt());
        assertEquals(0.7, root.path("temperature").asDouble(), 0.01);
        assertFalse(root.has("stream"));
    }

    @Test
    void buildRequestBodyWithStream() throws Exception {
        ModelConfig config = ModelConfig.builder().model("gpt-4o").build();
        String body = builder.buildRequestBody(List.of(Msg.of(MsgRole.USER, "Hi")), config, true);
        JsonNode root = mapper.readTree(body);

        assertTrue(root.path("stream").asBoolean());
    }

    @Test
    void buildRequestBodyIncludesSystemPrompt() throws Exception {
        ModelConfig config =
                ModelConfig.builder().model("gpt-4o").systemPrompt("Be helpful").build();
        String body = builder.buildRequestBody(List.of(Msg.of(MsgRole.USER, "Hi")), config, false);
        JsonNode root = mapper.readTree(body);

        JsonNode messages = root.path("messages");
        assertTrue(messages.isArray());
        assertEquals("system", messages.get(0).path("role").asText());
        assertEquals("Be helpful", messages.get(0).path("content").asText());
    }

    @Test
    void buildRequestBodyIncludesTools() throws Exception {
        ToolDefinition tool =
                new ToolDefinition(
                        "read_file",
                        "Read a file",
                        ToolCategory.FILE_AND_CODE,
                        new JsonSchema(
                                "object",
                                Map.of("path", new JsonSchema("string", null, null, "file path")),
                                List.of("path"),
                                null),
                        Object.class);
        ModelConfig config = ModelConfig.builder().model("gpt-4o").addTool(tool).build();
        String body =
                builder.buildRequestBody(List.of(Msg.of(MsgRole.USER, "read")), config, false);
        JsonNode root = mapper.readTree(body);

        JsonNode tools = root.path("tools");
        assertTrue(tools.isArray());
        assertEquals(1, tools.size());
        assertEquals("read_file", tools.get(0).path("function").path("name").asText());
        assertEquals("Read a file", tools.get(0).path("function").path("description").asText());
    }

    @Test
    void buildRequestBodyWithEffort() throws Exception {
        ModelConfig config = ModelConfig.builder().model("gpt-4o").effort(0.8).build();
        String body =
                builder.buildRequestBody(List.of(Msg.of(MsgRole.USER, "think")), config, false);
        JsonNode root = mapper.readTree(body);

        assertEquals("high", root.path("reasoning_effort").asText());
    }

    @Test
    void buildRequestBodyEffortLow() throws Exception {
        ModelConfig config = ModelConfig.builder().model("gpt-4o").effort(0.2).build();
        String body =
                builder.buildRequestBody(List.of(Msg.of(MsgRole.USER, "quick")), config, false);
        JsonNode root = mapper.readTree(body);

        assertEquals("low", root.path("reasoning_effort").asText());
    }

    @Test
    void buildRequestBodyEffortMedium() throws Exception {
        ModelConfig config = ModelConfig.builder().model("gpt-4o").effort(0.5).build();
        String body =
                builder.buildRequestBody(List.of(Msg.of(MsgRole.USER, "think")), config, false);
        JsonNode root = mapper.readTree(body);

        assertEquals("medium", root.path("reasoning_effort").asText());
    }

    // ── Image content tests ─────────────────────────────────────────────────────

    @Test
    void imageContentWithUrlSerializesAsImageUrl() throws Exception {
        Msg msg =
                Msg.builder()
                        .role(MsgRole.USER)
                        .addContent(new Content.TextContent("Describe this image"))
                        .addContent(
                                new Content.ImageContent(
                                        "https://example.com/photo.png", "image/png", null))
                        .build();
        ModelConfig config = ModelConfig.builder().model("gpt-4o").build();
        String body = builder.buildRequestBody(List.of(msg), config, false);
        JsonNode root = mapper.readTree(body);

        JsonNode content = root.path("messages").get(0).path("content");
        assertTrue(content.isArray(), "multi-part content should be an array");
        assertEquals(2, content.size());

        // First part: text
        assertEquals("text", content.get(0).path("type").asText());
        assertEquals("Describe this image", content.get(0).path("text").asText());

        // Second part: image_url with direct URL
        assertEquals("image_url", content.get(1).path("type").asText());
        assertEquals(
                "https://example.com/photo.png",
                content.get(1).path("image_url").path("url").asText());
    }

    @Test
    void imageContentWithBase64DataSerializesAsDataUrl() throws Exception {
        byte[] fakeImageBytes = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47}; // PNG magic bytes
        Msg msg =
                Msg.builder()
                        .role(MsgRole.USER)
                        .addContent(new Content.TextContent("What is in this picture?"))
                        .addContent(new Content.ImageContent(null, "image/png", fakeImageBytes))
                        .build();
        ModelConfig config = ModelConfig.builder().model("gpt-4o").build();
        String body = builder.buildRequestBody(List.of(msg), config, false);
        JsonNode root = mapper.readTree(body);

        JsonNode content = root.path("messages").get(0).path("content");
        assertTrue(content.isArray());
        assertEquals(2, content.size());

        JsonNode imageNode = content.get(1);
        assertEquals("image_url", imageNode.path("type").asText());

        String url = imageNode.path("image_url").path("url").asText();
        assertTrue(url.startsWith("data:image/png;base64,"), "should be a data: URL");

        // Verify the base64 payload round-trips
        String base64Part = url.substring("data:image/png;base64,".length());
        byte[] decoded = java.util.Base64.getDecoder().decode(base64Part);
        assertArrayEquals(fakeImageBytes, decoded);
    }

    @Test
    void imageContentWithBase64DefaultsMediaTypeToPng() throws Exception {
        byte[] data = "fake-image".getBytes();
        Msg msg =
                Msg.builder()
                        .role(MsgRole.USER)
                        .addContent(new Content.TextContent("describe"))
                        .addContent(new Content.ImageContent(null, null, data))
                        .build();
        ModelConfig config = ModelConfig.builder().model("gpt-4o").build();
        String body = builder.buildRequestBody(List.of(msg), config, false);
        JsonNode root = mapper.readTree(body);

        String url =
                root.path("messages")
                        .get(0)
                        .path("content")
                        .get(1)
                        .path("image_url")
                        .path("url")
                        .asText();
        assertTrue(
                url.startsWith("data:image/png;base64,"),
                "null mediaType should default to image/png");
    }

    @Test
    void imageContentWithNeitherUrlNorDataIsOmitted() throws Exception {
        Msg msg =
                Msg.builder()
                        .role(MsgRole.USER)
                        .addContent(new Content.TextContent("hello"))
                        .addContent(new Content.ImageContent(null, null, null))
                        .build();
        ModelConfig config = ModelConfig.builder().model("gpt-4o").build();
        String body = builder.buildRequestBody(List.of(msg), config, false);
        JsonNode root = mapper.readTree(body);

        JsonNode content = root.path("messages").get(0).path("content");
        // Only the text part should remain; the empty image is skipped
        assertTrue(content.isArray());
        assertEquals(1, content.size());
        assertEquals("text", content.get(0).path("type").asText());
    }
}
