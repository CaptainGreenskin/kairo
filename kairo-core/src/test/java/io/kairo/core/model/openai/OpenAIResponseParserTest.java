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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.message.Content;
import io.kairo.api.model.ModelResponse;
import io.kairo.core.model.ModelProviderUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAIResponseParserTest {

    private OpenAIResponseParser parser;

    @BeforeEach
    void setUp() {
        ObjectMapper om = ModelProviderUtils.createObjectMapper();
        parser = new OpenAIResponseParser(om);
    }

    @Test
    void parseTextResponse() throws Exception {
        String body =
                """
                {
                  "id": "chatcmpl-123",
                  "model": "gpt-4o",
                  "choices": [{
                    "message": {"content": "Hello!"},
                    "finish_reason": "stop"
                  }],
                  "usage": {"prompt_tokens": 10, "completion_tokens": 5}
                }
                """;
        ModelResponse resp = parser.parseResponse(body);
        assertEquals("chatcmpl-123", resp.id());
        assertEquals("gpt-4o", resp.model());
        assertEquals(ModelResponse.StopReason.END_TURN, resp.stopReason());
        assertEquals(1, resp.contents().size());
        assertInstanceOf(Content.TextContent.class, resp.contents().get(0));
        assertEquals("Hello!", ((Content.TextContent) resp.contents().get(0)).text());
        assertEquals(10, resp.usage().inputTokens());
        assertEquals(5, resp.usage().outputTokens());
    }

    @Test
    void parseToolCallResponse() throws Exception {
        String body =
                """
                {
                  "id": "chatcmpl-456",
                  "model": "gpt-4o",
                  "choices": [{
                    "message": {
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
        ModelResponse resp = parser.parseResponse(body);
        assertEquals(ModelResponse.StopReason.TOOL_USE, resp.stopReason());
        assertEquals(1, resp.contents().size());
        Content.ToolUseContent tu = (Content.ToolUseContent) resp.contents().get(0);
        assertEquals("call_abc", tu.toolId());
        assertEquals("bash", tu.toolName());
        assertEquals("ls -la", tu.input().get("command"));
    }

    @Test
    void parseFinishReasonStop() {
        assertEquals(ModelResponse.StopReason.END_TURN, parser.parseFinishReason("stop"));
    }

    @Test
    void parseFinishReasonToolCalls() {
        assertEquals(ModelResponse.StopReason.TOOL_USE, parser.parseFinishReason("tool_calls"));
    }

    @Test
    void parseFinishReasonLength() {
        assertEquals(ModelResponse.StopReason.MAX_TOKENS, parser.parseFinishReason("length"));
    }

    @Test
    void parseFinishReasonNull() {
        assertNull(parser.parseFinishReason(null));
    }

    @Test
    void parseFinishReasonUnknown() {
        assertEquals(ModelResponse.StopReason.END_TURN, parser.parseFinishReason("unknown_reason"));
    }

    @Test
    void parseResponseMaxTokens() throws Exception {
        String body =
                """
                {
                  "id": "test-1",
                  "model": "gpt-4o",
                  "choices": [{
                    "message": {"content": "truncated"},
                    "finish_reason": "length"
                  }],
                  "usage": {"prompt_tokens": 50, "completion_tokens": 100}
                }
                """;
        ModelResponse resp = parser.parseResponse(body);
        assertEquals("test-1", resp.id());
        assertEquals(ModelResponse.StopReason.MAX_TOKENS, resp.stopReason());
        assertEquals(50, resp.usage().inputTokens());
        assertEquals(100, resp.usage().outputTokens());
    }
}
