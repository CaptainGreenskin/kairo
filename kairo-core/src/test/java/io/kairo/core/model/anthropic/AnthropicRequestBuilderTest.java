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
package io.kairo.core.model.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnthropicRequestBuilderTest {

    private static final String MODEL = "claude-sonnet-4-20250514";

    private AnthropicRequestBuilder builder;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        builder = new AnthropicRequestBuilder(mapper);
    }

    private ModelConfig basicConfig() {
        return ModelConfig.builder().model(MODEL).maxTokens(1024).temperature(1.0).build();
    }

    @Test
    void buildContainsModel() throws JsonProcessingException {
        String json =
                builder.buildRequestBody(
                        List.of(Msg.of(MsgRole.USER, "hello")), basicConfig(), false);

        JsonNode root = mapper.readTree(json);
        assertThat(root.get("model").asText()).isEqualTo(MODEL);
    }

    @Test
    void buildContainsMaxTokens() throws JsonProcessingException {
        String json =
                builder.buildRequestBody(
                        List.of(Msg.of(MsgRole.USER, "hello")), basicConfig(), false);

        JsonNode root = mapper.readTree(json);
        assertThat(root.get("max_tokens").asInt()).isEqualTo(1024);
    }

    @Test
    void buildContainsTemperature() throws JsonProcessingException {
        ModelConfig config =
                ModelConfig.builder().model(MODEL).maxTokens(1024).temperature(0.5).build();

        String json = builder.buildRequestBody(List.of(Msg.of(MsgRole.USER, "hi")), config, false);

        JsonNode root = mapper.readTree(json);
        assertThat(root.get("temperature").asDouble()).isEqualTo(0.5);
    }

    @Test
    void buildWithStreamSetsSteamFlag() throws JsonProcessingException {
        String json =
                builder.buildRequestBody(
                        List.of(Msg.of(MsgRole.USER, "hello")), basicConfig(), true);

        JsonNode root = mapper.readTree(json);
        assertThat(root.get("stream").asBoolean()).isTrue();
    }

    @Test
    void buildWithoutStreamHasNoStreamField() throws JsonProcessingException {
        String json =
                builder.buildRequestBody(
                        List.of(Msg.of(MsgRole.USER, "hello")), basicConfig(), false);

        JsonNode root = mapper.readTree(json);
        assertThat(root.has("stream")).isFalse();
    }

    @Test
    void buildIncludesUserMessage() throws JsonProcessingException {
        String json =
                builder.buildRequestBody(
                        List.of(Msg.of(MsgRole.USER, "hello world")), basicConfig(), false);

        JsonNode root = mapper.readTree(json);
        JsonNode messages = root.get("messages");
        assertThat(messages).isNotNull();
        assertThat(messages.size()).isEqualTo(1);
        assertThat(messages.get(0).get("role").asText()).isEqualTo("user");
        assertThat(messages.get(0).get("content").asText()).isEqualTo("hello world");
    }

    @Test
    void buildExcludesSystemRoleFromMessages() throws JsonProcessingException {
        Msg systemMsg = Msg.of(MsgRole.SYSTEM, "You are helpful.");
        Msg userMsg = Msg.of(MsgRole.USER, "question");

        String json = builder.buildRequestBody(List.of(systemMsg, userMsg), basicConfig(), false);

        JsonNode root = mapper.readTree(json);
        JsonNode messages = root.get("messages");
        assertThat(messages.size()).isEqualTo(1);
        assertThat(messages.get(0).get("role").asText()).isEqualTo("user");
    }

    @Test
    void buildWithSystemPromptFromConfig() throws JsonProcessingException {
        ModelConfig config =
                ModelConfig.builder()
                        .model(MODEL)
                        .maxTokens(1024)
                        .systemPrompt("Be concise.")
                        .build();

        String json = builder.buildRequestBody(List.of(Msg.of(MsgRole.USER, "hi")), config, false);

        JsonNode root = mapper.readTree(json);
        assertThat(root.has("system")).isTrue();
        assertThat(root.get("system").asText()).isEqualTo("Be concise.");
    }

    @Test
    void buildWithSystemPromptFromMessage() throws JsonProcessingException {
        Msg sysMsg = Msg.of(MsgRole.SYSTEM, "System instructions.");
        ModelConfig config =
                ModelConfig.builder().model(MODEL).maxTokens(1024).temperature(1.0).build();

        String json =
                builder.buildRequestBody(
                        List.of(sysMsg, Msg.of(MsgRole.USER, "hi")), config, false);

        JsonNode root = mapper.readTree(json);
        assertThat(root.has("system")).isTrue();
    }

    @Test
    void resolveSystemPromptPrefersConfigOverMessages() {
        Msg sysMsg = Msg.of(MsgRole.SYSTEM, "From message");
        ModelConfig config =
                ModelConfig.builder()
                        .model(MODEL)
                        .maxTokens(1024)
                        .systemPrompt("From config")
                        .build();

        String result = AnthropicRequestBuilder.resolveSystemPrompt(List.of(sysMsg), config);

        assertThat(result).isEqualTo("From config");
    }

    @Test
    void resolveSystemPromptFallsBackToMessage() {
        ModelConfig config =
                ModelConfig.builder().model(MODEL).maxTokens(1024).temperature(1.0).build();

        String result =
                AnthropicRequestBuilder.resolveSystemPrompt(
                        List.of(Msg.of(MsgRole.SYSTEM, "From message")), config);

        assertThat(result).isEqualTo("From message");
    }

    @Test
    void resolveSystemPromptReturnsEmptyWhenNone() {
        ModelConfig config =
                ModelConfig.builder().model(MODEL).maxTokens(1024).temperature(1.0).build();

        String result =
                AnthropicRequestBuilder.resolveSystemPrompt(
                        List.of(Msg.of(MsgRole.USER, "hi")), config);

        assertThat(result).isEmpty();
    }

    @Test
    void buildAssistantMessageHasAssistantRole() throws JsonProcessingException {
        Msg userMsg = Msg.of(MsgRole.USER, "ping");
        Msg assistantMsg = Msg.of(MsgRole.ASSISTANT, "pong");

        String json =
                builder.buildRequestBody(List.of(userMsg, assistantMsg), basicConfig(), false);

        JsonNode root = mapper.readTree(json);
        JsonNode messages = root.get("messages");
        assertThat(messages.size()).isEqualTo(2);
        assertThat(messages.get(1).get("role").asText()).isEqualTo("assistant");
    }
}
