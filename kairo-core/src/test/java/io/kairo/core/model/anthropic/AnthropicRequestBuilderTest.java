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
import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnthropicRequestBuilderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AnthropicRequestBuilder BUILDER = new AnthropicRequestBuilder(MAPPER);

    private static final String TEST_MODEL = "claude-sonnet-4-20250514";

    private ModelConfig basicConfig() {
        return ModelConfig.builder().model(TEST_MODEL).maxTokens(1024).build();
    }

    @Test
    void build_includesModelAndMaxTokens() throws JsonProcessingException {
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "Hello"));
        String json = BUILDER.build(messages, basicConfig(), false);
        JsonNode root = MAPPER.readTree(json);

        assertThat(root.get("model").asText()).isEqualTo(TEST_MODEL);
        assertThat(root.get("max_tokens").asInt()).isEqualTo(1024);
    }

    @Test
    void build_streamTrue_setsStreamField() throws JsonProcessingException {
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "Hello"));
        String json = BUILDER.build(messages, basicConfig(), true);
        JsonNode root = MAPPER.readTree(json);

        assertThat(root.get("stream").asBoolean()).isTrue();
    }

    @Test
    void build_streamFalse_noStreamField() throws JsonProcessingException {
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "Hello"));
        String json = BUILDER.build(messages, basicConfig(), false);
        JsonNode root = MAPPER.readTree(json);

        assertThat(root.has("stream")).isFalse();
    }

    @Test
    void build_systemPromptFromConfig_setsSystemString() throws JsonProcessingException {
        ModelConfig config =
                ModelConfig.builder()
                        .model(TEST_MODEL)
                        .maxTokens(1024)
                        .systemPrompt("You are a helpful assistant.")
                        .build();
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "Hello"));
        String json = BUILDER.build(messages, config, false);
        JsonNode root = MAPPER.readTree(json);

        assertThat(root.get("system").asText()).isEqualTo("You are a helpful assistant.");
    }

    @Test
    void build_systemPromptFromMessages_extractsSystemMessage() throws JsonProcessingException {
        List<Msg> messages =
                List.of(
                        Msg.of(MsgRole.SYSTEM, "System instructions"),
                        Msg.of(MsgRole.USER, "Hello"));
        String json = BUILDER.build(messages, basicConfig(), false);
        JsonNode root = MAPPER.readTree(json);

        assertThat(root.get("system").asText()).isEqualTo("System instructions");
        // SYSTEM messages must not appear in the messages array
        assertThat(root.get("messages").size()).isEqualTo(1);
        assertThat(root.get("messages").get(0).get("role").asText()).isEqualTo("user");
    }

    @Test
    void build_userMessage_hasCorrectRoleAndContent() throws JsonProcessingException {
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "Hello there"));
        String json = BUILDER.build(messages, basicConfig(), false);
        JsonNode root = MAPPER.readTree(json);

        JsonNode firstMsg = root.get("messages").get(0);
        assertThat(firstMsg.get("role").asText()).isEqualTo("user");
        assertThat(firstMsg.get("content").asText()).isEqualTo("Hello there");
    }

    @Test
    void build_assistantMessage_hasCorrectRole() throws JsonProcessingException {
        List<Msg> messages =
                List.of(Msg.of(MsgRole.USER, "Hello"), Msg.of(MsgRole.ASSISTANT, "Hi there"));
        String json = BUILDER.build(messages, basicConfig(), false);
        JsonNode root = MAPPER.readTree(json);

        JsonNode second = root.get("messages").get(1);
        assertThat(second.get("role").asText()).isEqualTo("assistant");
        assertThat(second.get("content").asText()).isEqualTo("Hi there");
    }

    @Test
    void build_toolDefinition_serializedWithInputSchema() throws JsonProcessingException {
        ToolDefinition tool =
                new ToolDefinition(
                        "bash",
                        "Run a bash command",
                        ToolCategory.EXECUTION,
                        new JsonSchema("object", Map.of(), List.of(), null),
                        Object.class);
        ModelConfig config =
                ModelConfig.builder().model(TEST_MODEL).maxTokens(1024).addTool(tool).build();
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "Run a command"));
        String json = BUILDER.build(messages, config, false);
        JsonNode root = MAPPER.readTree(json);

        JsonNode tools = root.get("tools");
        assertThat(tools).isNotNull();
        assertThat(tools.size()).isEqualTo(1);

        JsonNode toolNode = tools.get(0);
        assertThat(toolNode.get("name").asText()).isEqualTo("bash");
        assertThat(toolNode.get("description").asText()).isEqualTo("Run a bash command");
        assertThat(toolNode.has("input_schema")).isTrue();
    }

    @Test
    void build_thinkingEnabled_setsThinkingNode() throws JsonProcessingException {
        ModelConfig config =
                ModelConfig.builder()
                        .model(TEST_MODEL)
                        .maxTokens(20000)
                        .thinking(new ModelConfig.ThinkingConfig(true, 5000))
                        .build();
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "Think carefully"));
        String json = BUILDER.build(messages, config, false);
        JsonNode root = MAPPER.readTree(json);

        JsonNode thinking = root.get("thinking");
        assertThat(thinking).isNotNull();
        assertThat(thinking.get("type").asText()).isEqualTo("enabled");
        assertThat(thinking.get("budget_tokens").asInt()).isEqualTo(5000);
    }

    @Test
    void resolveSystemPrompt_fromConfig_returnsConfigValue() {
        ModelConfig config =
                ModelConfig.builder().model(TEST_MODEL).systemPrompt("Config system").build();
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "Hello"));

        String result = AnthropicRequestBuilder.resolveSystemPrompt(messages, config);

        assertThat(result).isEqualTo("Config system");
    }

    @Test
    void resolveSystemPrompt_noSystemPrompt_returnsEmpty() {
        List<Msg> messages = List.of(Msg.of(MsgRole.USER, "Hello"));

        String result = AnthropicRequestBuilder.resolveSystemPrompt(messages, basicConfig());

        assertThat(result).isEmpty();
    }
}
