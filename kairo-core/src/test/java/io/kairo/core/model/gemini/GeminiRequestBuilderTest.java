/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.core.model.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GeminiRequestBuilderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final GeminiRequestBuilder builder =
            new GeminiRequestBuilder(
                    "test-key", "https://generativelanguage.googleapis.com", mapper);

    private static Msg textMsg(MsgRole role, String text) {
        return Msg.builder().role(role).addContent(new Content.TextContent(text)).build();
    }

    @Test
    void buildRequestPutsApiKeyInQueryString() {
        var req = builder.buildRequest("gemini-2.0-flash", "{}", false);
        assertThat(req.uri().toString())
                .startsWith(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=")
                .endsWith("test-key");
    }

    @Test
    void buildRequestUsesStreamEndpointWhenAsked() {
        var req = builder.buildRequest("gemini-2.0-flash", "{}", true);
        assertThat(req.uri().toString()).contains(":streamGenerateContent?alt=sse&key=");
    }

    @Test
    void buildBodyMapsAssistantRoleToModel() throws Exception {
        var messages = List.of(textMsg(MsgRole.USER, "hi"), textMsg(MsgRole.ASSISTANT, "hello"));
        String body =
                builder.buildBody(
                        messages, ModelConfig.builder().model("gemini-2.0-flash").build());
        JsonNode root = mapper.readTree(body);
        assertThat(root.path("contents").get(0).path("role").asText()).isEqualTo("user");
        assertThat(root.path("contents").get(1).path("role").asText()).isEqualTo("model");
    }

    @Test
    void buildBodyHoistsSystemMessageOutOfContents() throws Exception {
        var messages = List.of(textMsg(MsgRole.SYSTEM, "be terse"), textMsg(MsgRole.USER, "hi"));
        String body =
                builder.buildBody(
                        messages, ModelConfig.builder().model("gemini-2.0-flash").build());
        JsonNode root = mapper.readTree(body);
        assertThat(root.path("systemInstruction").path("parts").get(0).path("text").asText())
                .isEqualTo("be terse");
        // System message is NOT in contents
        assertThat(root.path("contents").size()).isEqualTo(1);
    }

    @Test
    void buildBodyEmitsGenerationConfig() throws Exception {
        var config =
                ModelConfig.builder()
                        .model("gemini-2.0-flash")
                        .maxTokens(2048)
                        .temperature(0.5)
                        .build();
        String body = builder.buildBody(List.of(), config);
        JsonNode root = mapper.readTree(body);
        assertThat(root.path("generationConfig").path("maxOutputTokens").asInt()).isEqualTo(2048);
        assertThat(root.path("generationConfig").path("temperature").asDouble()).isEqualTo(0.5);
    }

    @Test
    void buildBodyEmitsToolsAsFunctionDeclarations() throws Exception {
        var schema = new JsonSchema("object", Map.of(), List.of(), null);
        var tool =
                new ToolDefinition(
                        "get_weather", "Get the weather", ToolCategory.GENERAL, schema, null);
        var config = ModelConfig.builder().model("gemini-2.0-flash").tools(List.of(tool)).build();
        String body = builder.buildBody(List.of(), config);
        JsonNode root = mapper.readTree(body);
        JsonNode decls = root.path("tools").get(0).path("functionDeclarations");
        assertThat(decls.isArray()).isTrue();
        assertThat(decls.get(0).path("name").asText()).isEqualTo("get_weather");
        assertThat(decls.get(0).path("description").asText()).isEqualTo("Get the weather");
        assertThat(decls.get(0).path("parameters").path("type").asText()).isEqualTo("object");
    }

    @Test
    void buildBodyEmitsToolUseAsFunctionCallPart() throws Exception {
        var msg =
                Msg.builder()
                        .role(MsgRole.ASSISTANT)
                        .addContent(
                                new Content.ToolUseContent(
                                        "tool-1", "get_weather", Map.of("city", "Paris")))
                        .build();
        String body =
                builder.buildBody(
                        List.of(msg), ModelConfig.builder().model("gemini-2.0-flash").build());
        JsonNode root = mapper.readTree(body);
        JsonNode fc = root.path("contents").get(0).path("parts").get(0).path("functionCall");
        assertThat(fc.path("name").asText()).isEqualTo("get_weather");
        assertThat(fc.path("args").path("city").asText()).isEqualTo("Paris");
    }
}
