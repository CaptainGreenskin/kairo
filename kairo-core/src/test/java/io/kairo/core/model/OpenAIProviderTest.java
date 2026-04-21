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
package io.kairo.core.model;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class OpenAIProviderTest {

    private MockWebServer server;
    private OpenAIProvider provider;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("/").toString();
        // Remove trailing slash
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        provider = new OpenAIProvider("test-api-key", baseUrl, "/v1/chat/completions");
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private ModelConfig simpleConfig() {
        return ModelConfig.builder().model("gpt-4o").maxTokens(1024).temperature(0.7).build();
    }

    @Test
    void name() {
        assertEquals("openai", provider.name());
    }

    @Test
    void successfulChatCompletion() {
        String response =
                """
                {
                  "id": "chatcmpl-123",
                  "model": "gpt-4o",
                  "choices": [{
                    "index": 0,
                    "message": {"role": "assistant", "content": "Hello!"},
                    "finish_reason": "stop"
                  }],
                  "usage": {"prompt_tokens": 10, "completion_tokens": 5}
                }
                """;
        server.enqueue(
                new MockResponse().setBody(response).setHeader("Content-Type", "application/json"));

        Msg input = Msg.of(MsgRole.USER, "Hi");
        StepVerifier.create(provider.call(List.of(input), simpleConfig()))
                .assertNext(
                        resp -> {
                            assertEquals("chatcmpl-123", resp.id());
                            assertEquals("gpt-4o", resp.model());
                            assertEquals(ModelResponse.StopReason.END_TURN, resp.stopReason());
                            assertEquals(1, resp.contents().size());
                            assertInstanceOf(Content.TextContent.class, resp.contents().get(0));
                            assertEquals(
                                    "Hello!",
                                    ((Content.TextContent) resp.contents().get(0)).text());
                            assertEquals(10, resp.usage().inputTokens());
                            assertEquals(5, resp.usage().outputTokens());
                        })
                .verifyComplete();
    }

    @Test
    void toolCallsResponse() {
        String response =
                """
                {
                  "id": "chatcmpl-456",
                  "model": "gpt-4o",
                  "choices": [{
                    "index": 0,
                    "message": {
                      "role": "assistant",
                      "content": null,
                      "tool_calls": [{
                        "id": "call_abc",
                        "type": "function",
                        "function": {
                          "name": "bash",
                          "arguments": "{\\"command\\": \\"ls -la\\"}"
                        }
                      }]
                    },
                    "finish_reason": "tool_calls"
                  }],
                  "usage": {"prompt_tokens": 20, "completion_tokens": 15}
                }
                """;
        server.enqueue(
                new MockResponse().setBody(response).setHeader("Content-Type", "application/json"));

        StepVerifier.create(
                        provider.call(List.of(Msg.of(MsgRole.USER, "list files")), simpleConfig()))
                .assertNext(
                        resp -> {
                            assertEquals(ModelResponse.StopReason.TOOL_USE, resp.stopReason());
                            assertEquals(1, resp.contents().size());
                            Content.ToolUseContent tu =
                                    (Content.ToolUseContent) resp.contents().get(0);
                            assertEquals("call_abc", tu.toolId());
                            assertEquals("bash", tu.toolName());
                            assertEquals("ls -la", tu.input().get("command"));
                        })
                .verifyComplete();
    }

    @Test
    void httpError500() {
        // Enqueue enough 500 responses for all retry attempts (initial + 3 retries)
        server.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));
        server.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));
        server.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));
        server.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

        StepVerifier.create(provider.call(List.of(Msg.of(MsgRole.USER, "hi")), simpleConfig()))
                .expectErrorMatches(
                        e -> {
                            // After retries exhausted, ExceptionMapper maps to API-layer types
                            return e instanceof io.kairo.api.exception.ModelApiException
                                    && e.getMessage().contains("500");
                        })
                .verify();
    }

    @Test
    void rateLimitRetry429() {
        // Enqueue 429 three times, then success
        for (int i = 0; i < 3; i++) {
            server.enqueue(new MockResponse().setResponseCode(429).setBody("Rate limited"));
        }
        String success =
                """
                {"id":"r","model":"m","choices":[{"message":{"content":"ok"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":1}}
                """;
        server.enqueue(
                new MockResponse().setBody(success).setHeader("Content-Type", "application/json"));

        StepVerifier.create(provider.call(List.of(Msg.of(MsgRole.USER, "hi")), simpleConfig()))
                .assertNext(
                        resp ->
                                assertEquals(
                                        "ok",
                                        ((Content.TextContent) resp.contents().get(0)).text()))
                .verifyComplete();
    }

    @Test
    void requestContainsAuthorizationHeader() throws InterruptedException {
        String response =
                """
                {"id":"r","model":"m","choices":[{"message":{"content":"ok"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":1}}
                """;
        server.enqueue(
                new MockResponse().setBody(response).setHeader("Content-Type", "application/json"));

        provider.call(List.of(Msg.of(MsgRole.USER, "test")), simpleConfig()).block();

        RecordedRequest req = server.takeRequest();
        assertEquals("Bearer test-api-key", req.getHeader("Authorization"));
        assertEquals("application/json", req.getHeader("Content-Type"));
        assertTrue(req.getPath().endsWith("/v1/chat/completions"));
    }

    @Test
    void systemPromptIncludedInRequest() throws Exception {
        String response =
                """
                {"id":"r","model":"m","choices":[{"message":{"content":"ok"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":1}}
                """;
        server.enqueue(
                new MockResponse().setBody(response).setHeader("Content-Type", "application/json"));

        ModelConfig config =
                ModelConfig.builder().model("gpt-4o").systemPrompt("You are helpful").build();
        provider.call(List.of(Msg.of(MsgRole.USER, "hi")), config).block();

        RecordedRequest req = server.takeRequest();
        JsonNode body = mapper.readTree(req.getBody().readUtf8());
        JsonNode messages = body.path("messages");
        assertEquals("system", messages.get(0).path("role").asText());
        assertEquals("You are helpful", messages.get(0).path("content").asText());
    }

    @Test
    void toolsIncludedInRequest() throws Exception {
        String response =
                """
                {"id":"r","model":"m","choices":[{"message":{"content":"ok"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":1}}
                """;
        server.enqueue(
                new MockResponse().setBody(response).setHeader("Content-Type", "application/json"));

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
        provider.call(List.of(Msg.of(MsgRole.USER, "read")), config).block();

        RecordedRequest req = server.takeRequest();
        JsonNode body = mapper.readTree(req.getBody().readUtf8());
        JsonNode tools = body.path("tools");
        assertTrue(tools.isArray());
        assertEquals(1, tools.size());
        assertEquals("read_file", tools.get(0).path("function").path("name").asText());
    }

    @Test
    void parseResponseDirectly() throws Exception {
        String responseBody =
                """
                {
                  "id": "test-1",
                  "model": "gpt-4o",
                  "choices": [{
                    "message": {"content": "parsed directly"},
                    "finish_reason": "length"
                  }],
                  "usage": {"prompt_tokens": 50, "completion_tokens": 100}
                }
                """;
        ModelResponse resp = provider.parseResponse(responseBody);
        assertEquals("test-1", resp.id());
        assertEquals(ModelResponse.StopReason.MAX_TOKENS, resp.stopReason());
        assertEquals(50, resp.usage().inputTokens());
        assertEquals(100, resp.usage().outputTokens());
    }

    @Test
    void customChatCompletionsPath() throws Exception {
        MockWebServer customServer = new MockWebServer();
        customServer.start();
        String baseUrl = customServer.url("").toString();
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        OpenAIProvider custom = new OpenAIProvider("key", baseUrl, "/chat/completions");

        String response =
                """
                {"id":"r","model":"m","choices":[{"message":{"content":"ok"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":1}}
                """;
        customServer.enqueue(
                new MockResponse().setBody(response).setHeader("Content-Type", "application/json"));

        custom.call(List.of(Msg.of(MsgRole.USER, "hi")), simpleConfig()).block();

        RecordedRequest req = customServer.takeRequest();
        assertEquals("/chat/completions", req.getPath());
        customServer.shutdown();
    }

    @Test
    void structuredOutputAddsResponseFormat() throws Exception {
        String response =
                """
                {"id":"r","model":"m","choices":[{"message":{"content":"{}"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":1}}
                """;
        server.enqueue(
                new MockResponse().setBody(response).setHeader("Content-Type", "application/json"));

        ModelConfig config =
                ModelConfig.builder()
                        .model("gpt-4o")
                        .maxTokens(1024)
                        .responseSchema(StructuredTestOutput.class)
                        .build();
        provider.call(List.of(Msg.of(MsgRole.USER, "test")), config).block();

        RecordedRequest req = server.takeRequest();
        JsonNode body = mapper.readTree(req.getBody().readUtf8());
        JsonNode responseFormat = body.path("response_format");
        assertEquals("json_schema", responseFormat.path("type").asText());
        JsonNode jsonSchema = responseFormat.path("json_schema");
        assertEquals("StructuredTestOutput", jsonSchema.path("name").asText());
        assertTrue(jsonSchema.path("strict").asBoolean());
        assertTrue(jsonSchema.has("schema"));
        assertEquals("object", jsonSchema.path("schema").path("type").asText());
    }

    static class StructuredTestOutput {
        public String answer;
        public int confidence;
    }
}
